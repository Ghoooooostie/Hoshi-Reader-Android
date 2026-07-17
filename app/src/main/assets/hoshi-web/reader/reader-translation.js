(function(global) {
  'use strict';

  var TARGET_SELECTOR = 'p, li, blockquote, figcaption, h1, h2, h3, h4, h5, h6, dt, dd';
  var TARGET_ATTRIBUTE = 'data-hoshi-reader-translation-id';
  var TRANSLATION_CLASS = 'hoshi-reader-translation';

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

  function ensureTargetId(element, index) {
    var current = element.getAttribute(TARGET_ATTRIBUTE);
    if (current) return current;
    var next = 'hoshi-translation-' + (index + 1);
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
    targetAtPoint: function(x, y) {
      var touched = document.elementFromPoint(x, y);
      var translationBlock = touched && touched.closest ? touched.closest('.' + TRANSLATION_CLASS) : null;
      if (!translationBlock) return null;
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
    },
    applyTranslation: function(targetId, translation) {
      var element = findTargetById(targetId);
      if (!element) return false;
      var block = findTranslationNode(element, targetId);
      if (!block) {
        block = document.createElement('div');
        block.className = TRANSLATION_CLASS;
        block.setAttribute('data-hoshi-translation-for', targetId);
        element.insertAdjacentElement('afterend', block);
      }
      block.textContent = translation || '';
      refreshReaderLayout();
      return true;
    },
    clearTranslations: function() {
      Array.from(document.querySelectorAll('.' + TRANSLATION_CLASS)).forEach(function(node) {
        node.remove();
      });
      refreshReaderLayout();
      return true;
    }
  };
})(window);
