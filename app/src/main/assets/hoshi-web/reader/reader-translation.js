(function(global) {
  'use strict';

  var TARGET_SELECTOR = 'p, li, blockquote, figcaption, h1, h2, h3, h4, h5, h6, dt, dd';
  var TARGET_ATTRIBUTE = 'data-hoshi-reader-translation-id';
  var TRANSLATION_CLASS = 'hoshi-reader-translation';
  var HIDDEN_CLASS = 'hoshi-reader-translation-hidden';
  var REVEALED_CLASS = 'hoshi-reader-translation-revealed';
  var ON_LONG_PRESS_MODE = 'onLongPress';
  var READ_ALOUD_CLASS = 'hoshi-read-aloud-active';
  var READ_ALOUD_SENTENCE_CLASS = 'hoshi-read-aloud-sentence';
  var READ_ALOUD_TRANSLATION_CLASS = 'hoshi-read-aloud-translation';
  var READ_ALOUD_TRANSLATION_ATTRIBUTE = 'data-hoshi-read-aloud-translation-for';
  var VN_UNREVEALED_SELECTOR = '[data-hoshi-visual-novel-unrevealed]';
  var displayMode = 'persistent';

  /**
   * Visual Novel 逐字显示把同一个文本节点拆成「已显示」+「隐藏的剩余部分」
   * （隐藏 span 用 visibility:hidden 占位）。隐藏部分仍在 DOM 里，若把它也算进
   * 段落文本 / 参与高亮包裹，屏幕上就会出现重复文字。
   */
  function isUnrevealedTextNode(node) {
    var element = node && node.nodeType === Node.TEXT_NODE ? node.parentNode : node;
    return !!(element && element.closest && element.closest(VN_UNREVEALED_SELECTOR));
  }

  /**
   * 朗读高亮包裹段落文本前，先把 VN 逐字显示补完，保证一个段落只有一份文本节点，
   * 避免 reveal 的后续 tick 把已被移进高亮 span 的字符再写一遍（表现为重复文字）。
   */
  function completePendingVisualNovelReveal(element) {
    var reader = global.hoshiReader;
    if (!reader || typeof reader.completeCurrentReveal !== 'function') return;
    if (!element || !element.querySelector) return;
    if (!element.querySelector(VN_UNREVEALED_SELECTOR)) return;
    reader.completeCurrentReveal();
  }

  /** 把高亮 span 还原成原文本节点，避免反复包裹后残留副本。 */
  function unwrapElement(element) {
    var parent = element.parentNode;
    if (!parent) return;
    while (element.firstChild) {
      parent.insertBefore(element.firstChild, element);
    }
    parent.removeChild(element);
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
    return hasMatchableText(extractTargetText(element));
  }

  function collectCandidateElements() {
    var elements = Array.from(document.body.querySelectorAll(TARGET_SELECTOR));
    // 递归遍历 body 下所有后代块级元素，捕获 TARGET_SELECTOR 之外的独立块
    // （如 EPUB 中常见的 <div> 段落）。原实现只检查 document.body.children，
    // 在 VN 等深层嵌套结构（正文位于 body > stage > screen > content 之下）会漏掉
    // 嵌套段落，导致朗读队列为空、双击无法开始播放。
    walkStandaloneBlocks(document.body, elements);
    return elements.filter(function(element) {
      return hasMatchableText(extractTargetText(element));
    });
  }

  function walkStandaloneBlocks(root, collected) {
    Array.prototype.forEach.call(root.children, function(child) {
      if (collected.indexOf(child) >= 0) return;
      if (child.classList && child.classList.contains(TRANSLATION_CLASS)) return;
      if (child.querySelector(TARGET_SELECTOR)) {
        // 内部已有更细的 target 元素（已由 querySelectorAll 收集），
        // 仍向下递归以捕获同级的独立块（如与 <p> 并列的 <div> 段落）。
        walkStandaloneBlocks(child, collected);
        return;
      }
      if (isStandaloneBlock(child)) {
        // 自身是独立块，但内部还有更细的独立块时交给后代收集，
        // 避免把多段文字合并成一段。
        if (!standaloneBlockDescendantExists(child)) {
          collected.push(child);
        } else {
          walkStandaloneBlocks(child, collected);
        }
        return;
      }
      // 非独立块容器：继续向下递归。
      walkStandaloneBlocks(child, collected);
    });
  }

  function standaloneBlockDescendantExists(element) {
    var children = Array.prototype.slice.call(element.children);
    for (var i = 0; i < children.length; i++) {
      var c = children[i];
      if (c.classList && c.classList.contains(TRANSLATION_CLASS)) continue;
      if (isStandaloneBlock(c)) return true;
      if (standaloneBlockDescendantExists(c)) return true;
    }
    return false;
  }

  function extractTargetText(element) {
    if (!element) return '';
    var clone = element.cloneNode(true);
    Array.from(clone.querySelectorAll('rt, rp, script, style, .' + TRANSLATION_CLASS)).forEach(function(node) {
      node.remove();
    });
    var text = (clone.textContent || '').replace(/\s+/g, ' ').trim();
    if (!text) return '';
    return text;
  }

  function hasMatchableText(text) {
    if (!text) return false;
    if (global.hoshiReader && typeof global.hoshiReader.countChars === 'function') {
      return global.hoshiReader.countChars(text) > 0;
    }
    return text.length > 0;
  }

  /**
   * 目标 id 是译文缓存/队列的键（缓存按「章 + id」索引），所以它必须在同一章内
   * 唯一且稳定。分页/连续模式的元素常驻 DOM，序号 id 天然稳定；VN 每次翻屏都会
   * 重建当前屏的 clone，序号 id 会被同一章不同屏的段落重复使用，于是双击某句可能
   * 套出别屏同序号段落的译文。reader 若能用源文档结构位置给出章内稳定 key，就用它。
   */
  function stableTargetId(element) {
    var reader = global.hoshiReader;
    if (!reader || typeof reader.translationTargetKeyForElement !== 'function') return null;
    var key = reader.translationTargetKeyForElement(element);
    return key ? key : null;
  }

  function ensureTargetId(element, index) {
    var current = element.getAttribute(TARGET_ATTRIBUTE);
    if (current) return current;
    var next = 'hoshi-translation-' + (stableTargetId(element) || (index + 1));
    element.setAttribute(TARGET_ATTRIBUTE, next);
    return next;
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

  function assignTargetIds() {
    collectCandidateElements().forEach(function(element, index) {
      ensureTargetId(element, index);
    });
  }

  function applyBlockVisibility(block) {
    if (!block) return;
    if (displayMode === ON_LONG_PRESS_MODE && !block.classList.contains(REVEALED_CLASS)) {
      block.classList.add(HIDDEN_CLASS);
    } else {
      block.classList.remove(HIDDEN_CLASS);
    }
  }

  function scrollBlockIntoViewIfNeeded(block) {
    var rect = block.getBoundingClientRect();
    if (!rect || rect.height <= 0) return;
    if (rect.top >= 0 && rect.bottom <= global.innerHeight) return;
    if (typeof block.scrollIntoView === 'function') block.scrollIntoView();
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

  function isPagedReader() {
    var reader = global.hoshiReader;
    return !!(reader &&
      typeof reader.getScrollContext === 'function' &&
      typeof reader.getPagePosition === 'function' &&
      typeof reader.setPagePosition === 'function');
  }

  function usesVerticalScrollAxis() {
    var reader = global.hoshiReader;
    var verticalWriting = !!(reader && typeof reader.isVertical === 'function' && reader.isVertical());
    // 分页：竖排走 scrollTop、横排走 scrollLeft（横向分栏）；连续滚动正好相反。
    return isPagedReader() ? verticalWriting : !verticalWriting;
  }

  function readScrollOffset() {
    var reader = global.hoshiReader;
    if (isPagedReader()) {
      return reader.getPagePosition(reader.getScrollContext());
    }
    var root = document.scrollingElement || document.documentElement;
    if (usesVerticalScrollAxis()) {
      var top = root.scrollTop;
      if (top === 0 && global.scrollY !== 0) top = global.scrollY;
      return top;
    }
    var left = global.scrollX;
    if (left === 0 && root.scrollLeft !== 0) left = root.scrollLeft;
    return left;
  }

  function writeScrollOffset(offset) {
    var reader = global.hoshiReader;
    if (isPagedReader()) {
      reader.setPagePosition(reader.getScrollContext(), offset);
      return;
    }
    var root = document.scrollingElement || document.documentElement;
    if (usesVerticalScrollAxis()) {
      global.scrollTo({ left: global.scrollX, top: offset, behavior: 'instant' });
      root.scrollTop = offset;
    } else {
      global.scrollTo({ left: offset, top: global.scrollY, behavior: 'instant' });
      root.scrollLeft = offset;
    }
  }

  function captureReadingAnchor() {
    if (!global.hoshiReader) return null;
    var vertical = usesVerticalScrollAxis();
    var viewport = vertical ? global.innerHeight : global.innerWidth;
    var elements = collectCandidateElements();
    for (var i = 0; i < elements.length; i++) {
      var rect = elements[i].getBoundingClientRect();
      if (!rect || rect.width <= 0 || rect.height <= 0) continue;
      var start = vertical ? rect.top : rect.left;
      var end = vertical ? rect.bottom : rect.right;
      if (end > 0 && start < viewport) {
        return { element: elements[i], offset: start };
      }
    }
    return null;
  }

  // 译文插入/移除会改变内容高度与分栏，若不还原锚点，正在读的段落会被顶走（表现为"跳几句"）。
  function withPreservedReadingPosition(mutate) {
    if (!global.hoshiReader) {
      mutate();
      return;
    }
    var anchor = captureReadingAnchor();
    mutate();
    if (!anchor) return;
    var rect = anchor.element.getBoundingClientRect();
    if (!rect || rect.width <= 0 || rect.height <= 0) return;
    var vertical = usesVerticalScrollAxis();
    var current = vertical ? rect.top : rect.left;
    var delta = current - anchor.offset;
    if (!isFinite(delta) || Math.abs(delta) < 1) return;
    writeScrollOffset(readScrollOffset() + delta);
  }

  global.hoshiReaderPageTranslation = {
    collectVisibleTargets: function() {
      var targets = [];
      collectCandidateElements().forEach(function(element, index) {
        if (!isVisible(element)) return;
        var text = extractTargetText(element);
        if (!text) return;
        targets.push({
          id: ensureTargetId(element, index),
          text: text
        });
      });
      return JSON.stringify(targets);
    },
    // 按文档顺序取 targetId 之后的段落。朗读据此推进队列，
    // 避免依赖"当前可见集合"（译文插入会让可见集合漂移，导致漏句）。
    collectTargetsAfter: function(targetId, limit) {
      var elements = collectCandidateElements();
      var startIndex = 0;
      if (targetId) {
        var found = -1;
        for (var i = 0; i < elements.length; i++) {
          if (ensureTargetId(elements[i], i) === targetId) {
            found = i;
            break;
          }
        }
        if (found < 0) return JSON.stringify([]);
        startIndex = found + 1;
      }
      var max = limit > 0 ? limit : elements.length;
      var targets = [];
      for (var j = startIndex; j < elements.length && targets.length < max; j++) {
        var text = extractTargetText(elements[j]);
        if (!text) continue;
        targets.push({
          id: ensureTargetId(elements[j], j),
          text: text
        });
      }
      return JSON.stringify(targets);
    },
    targetAtPoint: function(x, y, includeOriginal) {
      var touched = document.elementFromPoint(x, y);
      if (!touched || !touched.closest) return null;
      var translationBlock = touched.closest('.' + TRANSLATION_CLASS);
      if (translationBlock) {
        var targetId = translationBlock.getAttribute('data-hoshi-translation-for') || '';
        if (!targetId) return null;
        var target = findTargetById(targetId);
        if (!target) return null;
        var text = extractTargetText(target);
        if (!text) return null;
        return JSON.stringify({
          id: targetId,
          text: text,
          onTranslation: true
        });
      }
      if (!includeOriginal) return null;
      var original = touched.closest('[' + TARGET_ATTRIBUTE + ']');
      if (!original) {
        assignTargetIds();
        original = touched.closest('[' + TARGET_ATTRIBUTE + ']');
      }
      if (!original) return null;
      var originalText = extractTargetText(original);
      if (!originalText) return null;
      return JSON.stringify({
        id: original.getAttribute(TARGET_ATTRIBUTE) || '',
        text: originalText,
        onTranslation: false
      });
    },
    applyTranslation: function(targetId, translation) {
      var element = findTargetById(targetId);
      if (!element) return false;
      // 该段被显式给出译文（句子手势/整页翻译）时移除跟读块，保证一段只有一份译文。
      Array.prototype.forEach.call(
        document.querySelectorAll('[' + READ_ALOUD_TRANSLATION_ATTRIBUTE + '="' + targetId + '"]'),
        function(node) { node.remove(); }
      );
      var block = findTranslationNode(element, targetId);
      withPreservedReadingPosition(function() {
        if (!block) {
          block = document.createElement('div');
          block.className = TRANSLATION_CLASS;
          block.setAttribute('data-hoshi-translation-for', targetId);
          element.insertAdjacentElement('afterend', block);
        }
        block.textContent = translation || '';
        applyBlockVisibility(block);
        refreshReaderLayout();
      });
      return true;
    },
    setDisplayMode: function(mode) {
      displayMode = (mode === ON_LONG_PRESS_MODE) ? ON_LONG_PRESS_MODE : 'persistent';
      withPreservedReadingPosition(function() {
        Array.prototype.forEach.call(
          document.querySelectorAll('.' + TRANSLATION_CLASS),
          function(block) { applyBlockVisibility(block); }
        );
        refreshReaderLayout();
      });
      return true;
    },
    revealTranslation: function(targetId) {
      var element = findTargetById(targetId);
      if (!element) return false;
      var block = findTranslationNode(element, targetId);
      if (!block) return false;
      Array.prototype.forEach.call(
        document.querySelectorAll('.' + REVEALED_CLASS),
        function(revealed) {
          revealed.classList.remove(REVEALED_CLASS);
          applyBlockVisibility(revealed);
        }
      );
      var wasHidden = block.classList.contains(HIDDEN_CLASS);
      withPreservedReadingPosition(function() {
        block.classList.add(REVEALED_CLASS);
        block.classList.remove(HIDDEN_CLASS);
        if (wasHidden) refreshReaderLayout();
      });
      scrollBlockIntoViewIfNeeded(block);
      return true;
    },
    clearTranslations: function() {
      withPreservedReadingPosition(function() {
        Array.from(document.querySelectorAll('.' + TRANSLATION_CLASS)).forEach(function(node) {
          node.remove();
        });
        refreshReaderLayout();
      });
      return true;
    },
    /**
     * 朗读跟读翻译：把"当前正在朗读的这一句"的译文显示在原文段落下方。
     * 同一段落已有译文块（整页翻译/句子手势）时直接复用，不再另插；
     * 否则插入独立的跟读块（不同的 data 属性），同一时刻只保留一个，
     * 朗读推进到下一句时整块替换。
     */
    showReadAloudTranslation: function(targetId, translation) {
      this.clearReadAloudTranslation();
      var element = findTargetById(targetId);
      if (!element) return false;
      // 该段已有译文块（整页翻译/句子手势）时不再另插跟读块，避免同段出现两份译文。
      var existing = findTranslationNode(element, targetId);
      if (existing) {
        if (existing.classList.contains(HIDDEN_CLASS)) {
          this.revealTranslation(targetId);
        }
        return true;
      }
      withPreservedReadingPosition(function() {
        var block = document.createElement('div');
        // 带整页译文 class：复用译文样式，并被段落收集逻辑（朗读队列 / 翻译目标）排除。
        // 但不设 data-hoshi-translation-for，整页译文不会改写它。
        block.className = TRANSLATION_CLASS + ' ' + READ_ALOUD_TRANSLATION_CLASS;
        block.setAttribute(READ_ALOUD_TRANSLATION_ATTRIBUTE, targetId);
        block.textContent = translation || '';
        element.insertAdjacentElement('afterend', block);
        refreshReaderLayout();
      });
      return true;
    },
    clearReadAloudTranslation: function() {
      var blocks = document.querySelectorAll('.' + READ_ALOUD_TRANSLATION_CLASS);
      if (!blocks || blocks.length === 0) return true;
      withPreservedReadingPosition(function() {
        Array.prototype.forEach.call(blocks, function(node) { node.remove(); });
        refreshReaderLayout();
      });
      return true;
    },
    highlightReadAloudTarget: function(targetId, reveal, highlightVisible) {
      this.clearReadAloudHighlight();
      var element = findTargetById(targetId);
      if (!element) return null;
      if (highlightVisible !== false) {
        element.classList.add(READ_ALOUD_CLASS);
      }
      if (reveal) {
        if (global.hoshiReader && typeof global.hoshiReader.scrollToRange === 'function') {
          var range = document.createRange();
          range.selectNodeContents(element);
          if (global.hoshiReader.scrollToRange(range)) {
            return global.hoshiReader.calculateProgress();
          }
        } else if (typeof element.scrollIntoView === 'function') {
          element.scrollIntoView();
        }
      }
      return true;
    },
    clearReadAloudHighlight: function() {
      Array.prototype.forEach.call(
        document.querySelectorAll('.' + READ_ALOUD_CLASS),
        function(node) { node.classList.remove(READ_ALOUD_CLASS); }
      );
      Array.prototype.forEach.call(
        document.querySelectorAll('.' + READ_ALOUD_SENTENCE_CLASS),
        function(node) {
          node.classList.remove(READ_ALOUD_SENTENCE_CLASS);
          // 只去掉 class 会残留包裹用的 span：下一句再包裹时这些 span 内的文本会被
          // 当成"原文"再次参与匹配，最终表现为段落文字被重复显示。这里直接还原 DOM。
          unwrapElement(node);
        }
      );
      return true;
    },
    /**
     * Highlights the specific sentence currently being spoken (音量键上一句/下一句跳转后让显示的文字
     * 也跟随变化). Falls back to highlighting the whole paragraph when the sentence text cannot be
     * located in the DOM (e.g. 按页朗读没有分句的段落). Skips ruby annotation text (<rt>/<rp>) when
     * matching the sentence so furigana does not break offset alignment.
     *
     * [highlightVisible] false (播放高亮关闭时) 仍会 reveal/滚动跟随，只是不画高亮。
     */
    highlightReadAloudSentence: function(targetId, sentenceText, reveal, highlightVisible) {
      this.clearReadAloudHighlight();
      var element = findTargetById(targetId);
      if (!element) return null;
      completePendingVisualNovelReveal(element);
      var sentence = (typeof sentenceText === 'string') ? sentenceText : '';
      var range = sentence.length > 0 ? this._findSentenceRange(element, sentence) : null;
      if (highlightVisible !== false) {
        element.classList.add(READ_ALOUD_CLASS);
        if (range) {
          this._wrapRange(range, READ_ALOUD_SENTENCE_CLASS);
        }
      }
      if (reveal) {
        var targetRange = range || document.createRange().selectNodeContents(element);
        if (global.hoshiReader && typeof global.hoshiReader.scrollToRange === 'function') {
          if (global.hoshiReader.scrollToRange(targetRange)) {
            return global.hoshiReader.calculateProgress();
          }
        } else if (typeof element.scrollIntoView === 'function') {
          element.scrollIntoView();
        }
      }
      return true;
    },
    /** Builds a range covering the first occurrence of [sentence] within [element]'s base text. */
    _findSentenceRange: function(element, sentence) {
      var segments = [];
      var baseText = '';
      var walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT, null, false);
      var node;
      while ((node = walker.nextNode())) {
        if (node.closest && node.closest('rt, rp')) continue;
        if (isUnrevealedTextNode(node)) continue;
        var text = node.nodeValue;
        segments.push({ node: node, baseStart: baseText.length, length: text.length });
        baseText += text;
      }
      var index = baseText.indexOf(sentence);
      if (index < 0) return null;
      var end = index + sentence.length;
      var range = document.createRange();
      var startSet = false;
      for (var i = 0; i < segments.length; i++) {
        var seg = segments[i];
        var segEnd = seg.baseStart + seg.length;
        if (!startSet && seg.baseStart <= index && segEnd >= index) {
          range.setStart(seg.node, index - seg.baseStart);
          startSet = true;
        }
        if (seg.baseStart < end && segEnd >= end) {
          range.setEnd(seg.node, end - seg.baseStart);
          break;
        }
      }
      if (!startSet) return null;
      return range;
    },
    /** Wraps each text node slice covered by [range] in a span with [className]. */
    _wrapRange: function(range, className) {
      var nodes = [];
      var walker = document.createTreeWalker(range.commonAncestorContainer, NodeFilter.SHOW_TEXT, null, false);
      var node;
      while ((node = walker.nextNode())) {
        if (!range.intersectsNode(node)) continue;
        if (node.closest && node.closest('rt, rp')) continue;
        if (isUnrevealedTextNode(node)) continue;
        var start = (node === range.startContainer) ? range.startOffset : 0;
        var end = (node === range.endContainer) ? range.endOffset : node.nodeValue.length;
        if (start >= end) continue;
        nodes.push({ node: node, start: start, end: end });
      }
      for (var i = nodes.length - 1; i >= 0; i--) {
        this._wrapTextSlice(nodes[i].node, nodes[i].start, nodes[i].end, className);
      }
    },
    _wrapTextSlice: function(node, start, end, className) {
      var target = node;
      if (start > 0) {
        target = target.splitText(start);
      }
      var sliceLength = end - start;
      if (sliceLength < target.nodeValue.length) {
        target.splitText(sliceLength);
      }
      var span = document.createElement('span');
      span.className = className;
      target.parentNode.insertBefore(span, target);
      span.appendChild(target);
    }
  };
})(window);
