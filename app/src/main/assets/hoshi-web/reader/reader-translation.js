(function(global) {
  'use strict';

  var TARGET_SELECTOR = 'p, li, blockquote, figcaption, h1, h2, h3, h4, h5, h6, dt, dd';
  var TARGET_ATTRIBUTE = 'data-hoshi-reader-translation-id';
  var TRANSLATION_CLASS = 'hoshi-reader-translation';
  var HIDDEN_CLASS = 'hoshi-reader-translation-hidden';
  var REVEALED_CLASS = 'hoshi-reader-translation-revealed';
  var ON_LONG_PRESS_MODE = 'onLongPress';
  var READ_ALOUD_CLASS = 'hoshi-read-aloud-active';
  var displayMode = 'persistent';

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
      var block = findTranslationNode(element, targetId);
      if (!block) {
        block = document.createElement('div');
        block.className = TRANSLATION_CLASS;
        block.setAttribute('data-hoshi-translation-for', targetId);
        element.insertAdjacentElement('afterend', block);
      }
      block.textContent = translation || '';
      applyBlockVisibility(block);
      refreshReaderLayout();
      return true;
    },
    setDisplayMode: function(mode) {
      displayMode = (mode === ON_LONG_PRESS_MODE) ? ON_LONG_PRESS_MODE : 'persistent';
      Array.prototype.forEach.call(
        document.querySelectorAll('.' + TRANSLATION_CLASS),
        function(block) { applyBlockVisibility(block); }
      );
      refreshReaderLayout();
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
      block.classList.add(REVEALED_CLASS);
      block.classList.remove(HIDDEN_CLASS);
      if (wasHidden) refreshReaderLayout();
      scrollBlockIntoViewIfNeeded(block);
      return true;
    },
    clearTranslations: function() {
      Array.from(document.querySelectorAll('.' + TRANSLATION_CLASS)).forEach(function(node) {
        node.remove();
      });
      refreshReaderLayout();
      return true;
    },
    highlightReadAloudTarget: function(targetId, reveal) {
      this.clearReadAloudHighlight();
      var element = findTargetById(targetId);
      if (!element) return null;
      element.classList.add(READ_ALOUD_CLASS);
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
      return true;
    }
  };
})(window);
