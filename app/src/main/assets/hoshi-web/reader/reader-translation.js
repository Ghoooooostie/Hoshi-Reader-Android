(function(global) {
  'use strict';

  var TARGET_SELECTOR = 'p, li, blockquote, figcaption, h1, h2, h3, h4, h5, h6, dt, dd';
  var TARGET_ATTRIBUTE = 'data-hoshi-reader-translation-id';
  var TRANSLATION_CLASS = 'hoshi-reader-translation';
  var TRANSLATION_FAILED_CLASS = 'hoshi-reader-translation-failed';

  // 合并多次布局刷新：连续同步的 apply 只触发一次 refreshReaderLayout，
  // 避免整页翻译逐段插入时反复全章重排（性能/跳位问题）。
  var pendingLayoutRefresh = false;
  function scheduleTranslationLayoutRefresh() {
    if (pendingLayoutRefresh) return;
    pendingLayoutRefresh = true;
    global.setTimeout(function() {
      pendingLayoutRefresh = false;
      refreshReaderLayout();
    }, 0);
  }

  function isTargetElement(element) {
    if (!element || element.nodeType !== Node.ELEMENT_NODE) return false;
    if (element.classList.contains(TRANSLATION_CLASS)) return false;
    if (element.closest('.' + TRANSLATION_CLASS)) return false;
    return element.matches(TARGET_SELECTOR);
  }

  function isStandaloneBlock(element) {
    if (!element || element.nodeType !== Node.ELEMENT_NODE) return false;
    if (isTargetElement(element)) return true;
    if (element.classList.contains(TRANSLATION_CLASS) || element.closest('.' + TRANSLATION_CLASS)) return false;
    if (element.querySelector(TARGET_SELECTOR)) return false;
    var display = global.getComputedStyle(element).display;
    if (display !== 'block' && display !== 'list-item' && display !== 'table-cell') return false;
    return extractTargetText(element).length > 0;
  }

  function collectCandidateElements() {
    var elements = Array.from(document.body.querySelectorAll(TARGET_SELECTOR));
    Array.from(document.body.children).forEach(function(child) {
      if (elements.indexOf(child) >= 0) return;
      if (isStandaloneBlock(child)) {
        elements.push(child);
      }
    });
    return elements.filter(function(element) {
      return extractTargetText(element).length > 0;
    });
  }

  function extractTargetText(element) {
    if (!element) return '';
    var clone = element.cloneNode(true);
    Array.from(clone.querySelectorAll('rt, rp, script, style, .' + TRANSLATION_CLASS)).forEach(function(node) {
      node.remove();
    });
    var text = (clone.textContent || '').replace(/\s+/g, ' ').trim();
    if (!text) return '';
    if (global.hoshiReader && typeof global.hoshiReader.normalizeText === 'function') {
      text = global.hoshiReader.normalizeText(text);
    }
    return String(text || '').replace(/\s+/g, ' ').trim();
  }

  // 稳定的目标标识：基于段落文本内容做哈希，而不是 DOM 下标。
  // 这样章节 HTML 重载 / restore / 进程重建后，同一段落在同一章节内
  // 仍得到相同 id，缓存才能正确命中（不会错位或不显示）。
  function stableHash(text) {
    var hash = 5381;
    for (var i = 0; i < text.length; i++) {
      hash = ((hash << 5) + hash + text.charCodeAt(i)) & 0xffffffff;
    }
    return (hash >>> 0).toString(36);
  }

  function ensureTargetId(element) {
    var current = element.getAttribute(TARGET_ATTRIBUTE);
    if (current) return current;
    var text = extractTargetText(element);
    var base = 'hoshi-translation-' + stableHash(text);
    var id = base;
    var n = 2;
    var existing = document.querySelector('[' + TARGET_ATTRIBUTE + '="' + id + '"]');
    // 同章节内出现完全相同的文本（如重复台词）时追加后缀去重。
    while (existing && existing !== element) {
      id = base + '-' + n;
      n++;
      existing = document.querySelector('[' + TARGET_ATTRIBUTE + '="' + id + '"]');
    }
    element.setAttribute(TARGET_ATTRIBUTE, id);
    return id;
  }

  function isVisible(element) {
    var rect = element.getBoundingClientRect();
    if (!rect || rect.width <= 0 || rect.height <= 0) return false;
    return rect.right > 0 &&
      rect.left < global.innerWidth &&
      rect.bottom > 0 &&
      rect.top < global.innerHeight;
  }

  function findTargetById(targetId) {
    return document.querySelector('[' + TARGET_ATTRIBUTE + '="' + targetId + '"]');
  }

  function findTranslationNode(element, targetId) {
    var next = element.nextElementSibling;
    if (!next) return null;
    if (!next.classList.contains(TRANSLATION_CLASS)) return null;
    if (next.getAttribute('data-hoshi-translation-for') !== targetId) return null;
    return next;
  }

  function refreshReaderLayout() {
    if (!global.hoshiReader) return;
    global.hoshiReader.paginationMetrics = null;
    if (typeof global.hoshiReader.refreshSasayakiCuePresentation === 'function') {
      global.hoshiReader.refreshSasayakiCuePresentation();
    }
    if (typeof global.hoshiReader.warmPaginationMetrics === 'function') {
      global.hoshiReader.warmPaginationMetrics();
    }
  }

  // 写入译文到目标元素后的兄弟块。返回是否实际改变了内容。
  function applyTranslationInternal(targetId, translation) {
    var element = findTargetById(targetId);
    if (!element) return false;
    var block = findTranslationNode(element, targetId);
    if (!block) {
      block = document.createElement('div');
      block.className = TRANSLATION_CLASS;
      block.setAttribute('data-hoshi-translation-for', targetId);
      element.insertAdjacentElement('afterend', block);
    }
    var next = translation || '';
    if (block.getAttribute('data-hoshi-translation-text') === next) {
      return false;
    }
    block.setAttribute('data-hoshi-translation-text', next);
    block.classList.remove(TRANSLATION_FAILED_CLASS);
    block.textContent = next;
    return true;
  }

  global.hoshiReaderPageTranslation = {
    collectVisibleTargets: function() {
      var targets = [];
      collectCandidateElements().forEach(function(element) {
        if (!isVisible(element)) return;
        var text = extractTargetText(element);
        if (!text) return;
        targets.push({
          id: ensureTargetId(element),
          text: text
        });
      });
      return JSON.stringify(targets);
    },
    targetAtPoint: function(x, y) {
      var touched = document.elementFromPoint(x, y);
      var translationBlock = touched && touched.closest ? touched.closest('.' + TRANSLATION_CLASS) : null;
      if (translationBlock) {
        var targetId = translationBlock.getAttribute('data-hoshi-translation-for') || '';
        if (!targetId) return null;
        var target = findTargetById(targetId);
        if (!target) return null;
        var text = extractTargetText(target);
        if (!text) return null;
        return JSON.stringify({
          id: targetId,
          text: text
        });
      }
      // 没有译文块时，向上找最近的候选目标元素，让长按未翻译/失败的段落也能手动重译。
      var candidate = touched && touched.closest ? touched.closest(TARGET_SELECTOR) : null;
      if (!candidate || candidate.closest('.' + TRANSLATION_CLASS)) return null;
      var candidateText = extractTargetText(candidate);
      if (!candidateText) return null;
      return JSON.stringify({
        id: ensureTargetId(candidate),
        text: candidateText
      });
    },
    applyTranslation: function(targetId, translation) {
      var changed = applyTranslationInternal(targetId, translation);
      if (changed) scheduleTranslationLayoutRefresh();
      return changed;
    },
    applyTranslations: function(itemsJson) {
      var items = JSON.parse(itemsJson || '[]');
      var changed = false;
      items.forEach(function(item) {
        if (applyTranslationInternal(item.id, item.translation)) changed = true;
      });
      if (changed) scheduleTranslationLayoutRefresh();
      return changed;
    },
    applyFailure: function(targetId, message) {
      var element = findTargetById(targetId);
      if (!element) return false;
      var block = findTranslationNode(element, targetId);
      if (!block) {
        block = document.createElement('div');
        block.className = TRANSLATION_CLASS;
        block.setAttribute('data-hoshi-translation-for', targetId);
        element.insertAdjacentElement('afterend', block);
      }
      block.classList.add(TRANSLATION_FAILED_CLASS);
      // 失败时不要残留上一次的成功译文，避免被 no-op 逻辑误判。
      block.removeAttribute('data-hoshi-translation-text');
      block.textContent = message || '翻译失败，长按重试';
      scheduleTranslationLayoutRefresh();
      return true;
    },
    flushTranslationLayout: function() {
      pendingLayoutRefresh = false;
      refreshReaderLayout();
      return true;
    },
    clearTranslations: function() {
      pendingLayoutRefresh = false;
      Array.from(document.querySelectorAll('.' + TRANSLATION_CLASS)).forEach(function(node) {
        node.remove();
      });
      refreshReaderLayout();
      return true;
    }
  };
})(window);
