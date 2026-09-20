import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';
import vm from 'node:vm';

const translationUrl = new URL(
    '../../main/assets/hoshi-web/reader/reader-translation.js',
    import.meta.url,
);

function matchesGroup(element, group) {
    const tokens = group.match(/^[a-zA-Z0-9]+|\.[A-Za-z0-9_-]+|\[[^\]]+\]/g) ?? [];
    if (tokens.length === 0) return false;
    return tokens.every(token => {
        if (token.startsWith('.')) return element.classes.has(token.slice(1));
        if (token.startsWith('[')) {
            const inner = token.slice(1, -1);
            const separator = inner.indexOf('=');
            if (separator < 0) return element.hasAttribute(inner);
            const name = inner.slice(0, separator);
            const value = inner.slice(separator + 1).replace(/^["']|["']$/g, '');
            return element.getAttribute(name) === value;
        }
        return element.tagName === token.toUpperCase();
    });
}

function matchesSelector(element, selector) {
    return String(selector)
        .split(',')
        .some(group => matchesGroup(element, group.trim()));
}

class TestElement {
    constructor(tagName) {
        this.tagName = String(tagName).toUpperCase();
        this.nodeType = 1;
        this.childNodes = [];
        this.parentNode = null;
        this.attributes = new Map();
        this.classes = new Set();
        this.ownText = '';
        this.computedDisplay = 'block';
        this.rect = { width: 100, height: 20, left: 0, top: 0, right: 100, bottom: 20 };
        this.scrollIntoViewCalls = 0;
    }

    get className() {
        return [...this.classes].join(' ');
    }

    set className(value) {
        this.classes = new Set(String(value).split(/\s+/).filter(Boolean));
    }

    get classList() {
        return {
            contains: name => this.classes.has(name),
            add: name => this.classes.add(name),
            remove: name => this.classes.delete(name),
        };
    }

    get children() {
        return this.childNodes.filter(node => node.nodeType === 1);
    }

    get textContent() {
        return this.ownText + this.childNodes.map(node => node.textContent).join('');
    }

    set textContent(value) {
        this.childNodes = [];
        this.ownText = String(value);
    }

    get nextElementSibling() {
        const siblings = this.parentNode ? this.parentNode.children : [];
        const index = siblings.indexOf(this);
        return index >= 0 ? siblings[index + 1] ?? null : null;
    }

    hasAttribute(name) {
        return this.attributes.has(name);
    }

    getAttribute(name) {
        return this.attributes.has(name) ? this.attributes.get(name) : null;
    }

    setAttribute(name, value) {
        this.attributes.set(name, String(value));
    }

    appendChild(child) {
        child.parentNode = this;
        this.childNodes.push(child);
        return child;
    }

    remove() {
        const siblings = this.parentNode?.childNodes;
        if (!siblings) return;
        const index = siblings.indexOf(this);
        if (index >= 0) siblings.splice(index, 1);
        this.parentNode = null;
    }

    cloneNode() {
        const clone = new TestElement(this.tagName);
        clone.classes = new Set(this.classes);
        clone.attributes = new Map(this.attributes);
        clone.ownText = this.ownText;
        clone.computedDisplay = this.computedDisplay;
        clone.rect = { ...this.rect };
        this.childNodes.forEach(child => clone.appendChild(child.cloneNode(true)));
        return clone;
    }

    matches(selector) {
        return matchesSelector(this, selector);
    }

    closest(selector) {
        let node = this;
        while (node) {
            if (matchesSelector(node, selector)) return node;
            node = node.parentNode;
        }
        return null;
    }

    querySelectorAll(selector) {
        const found = [];
        const walk = node => {
            node.children.forEach(child => {
                if (matchesSelector(child, selector)) found.push(child);
                walk(child);
            });
        };
        walk(this);
        return found;
    }

    querySelector(selector) {
        return this.querySelectorAll(selector)[0] ?? null;
    }

    getBoundingClientRect() {
        return this.rect;
    }

    insertAdjacentElement(position, element) {
        const siblings = this.parentNode?.childNodes;
        if (!siblings) return element;
        const index = siblings.indexOf(this);
        element.parentNode = this.parentNode;
        siblings.splice(index + 1, 0, element);
        return element;
    }

    scrollIntoView() {
        this.scrollIntoViewCalls += 1;
    }
}

function createTranslationEnvironment() {
    const body = new TestElement('body');
    let pointTarget = null;
    const document = {
        body,
        createElement: tagName => new TestElement(tagName),
        querySelectorAll: selector => body.querySelectorAll(selector),
        querySelector: selector => body.querySelector(selector),
        elementFromPoint: () => pointTarget,
    };
    const window = {
        document,
        innerWidth: 400,
        innerHeight: 800,
        getComputedStyle: element => ({ display: element.computedDisplay }),
    };
    vm.runInNewContext(fs.readFileSync(translationUrl, 'utf8'), {
        document,
        window,
        Node: { ELEMENT_NODE: 1 },
    });
    return {
        body,
        api: window.hoshiReaderPageTranslation,
        hitAt: element => {
            pointTarget = element;
            return JSON.parse(window.hoshiReaderPageTranslation.targetAtPoint(1, 1, true));
        },
        hitAtWithoutOriginal: element => {
            pointTarget = element;
            return window.hoshiReaderPageTranslation.targetAtPoint(1, 1, false);
        },
    };
}

function addParagraph(body, text) {
    const paragraph = new TestElement('p');
    paragraph.ownText = text;
    body.appendChild(paragraph);
    return paragraph;
}

test('long press on the translation block reports a translation hit', () => {
    const { body, api, hitAt } = createTranslationEnvironment();
    const paragraph = addParagraph(body, '第一段原文');
    const targets = JSON.parse(api.collectVisibleTargets());
    api.applyTranslation(targets[0].id, '第一段译文');

    const block = paragraph.nextElementSibling;
    const hit = hitAt(block);

    assert.equal(hit.onTranslation, true);
    assert.equal(hit.id, targets[0].id);
    assert.equal(hit.text, '第一段原文');
});

test('long press on the original paragraph reports an original hit only when requested', () => {
    const { body, api, hitAt, hitAtWithoutOriginal } = createTranslationEnvironment();
    const paragraph = addParagraph(body, '第一段原文');
    const targets = JSON.parse(api.collectVisibleTargets());
    api.applyTranslation(targets[0].id, '第一段译文');

    const hit = hitAt(paragraph);
    assert.equal(hit.onTranslation, false);
    assert.equal(hit.id, targets[0].id);

    assert.equal(hitAtWithoutOriginal(paragraph), null);
});

test('on-long-press mode keeps translations hidden until the paragraph is revealed', () => {
    const { body, api } = createTranslationEnvironment();
    const paragraph = addParagraph(body, '第一段原文');
    const targets = JSON.parse(api.collectVisibleTargets());

    api.setDisplayMode('onLongPress');
    api.applyTranslation(targets[0].id, '第一段译文');

    const block = paragraph.nextElementSibling;
    assert.equal(block.classList.contains('hoshi-reader-translation-hidden'), true);

    assert.equal(api.revealTranslation(targets[0].id), true);
    assert.equal(block.classList.contains('hoshi-reader-translation-hidden'), false);
    assert.equal(block.classList.contains('hoshi-reader-translation-revealed'), true);
});

test('persistent mode keeps translations visible without revealing', () => {
    const { body, api } = createTranslationEnvironment();
    const paragraph = addParagraph(body, '第一段原文');
    const targets = JSON.parse(api.collectVisibleTargets());

    api.setDisplayMode('persistent');
    api.applyTranslation(targets[0].id, '第一段译文');

    const block = paragraph.nextElementSibling;
    assert.equal(block.classList.contains('hoshi-reader-translation-hidden'), false);
});

test('revealing another paragraph re-hides the previously revealed translation', () => {
    const { body, api } = createTranslationEnvironment();
    const first = addParagraph(body, '第一段原文');
    const second = addParagraph(body, '第二段原文');
    const targets = JSON.parse(api.collectVisibleTargets());

    api.setDisplayMode('onLongPress');
    api.applyTranslation(targets[0].id, '第一段译文');
    api.applyTranslation(targets[1].id, '第二段译文');

    const firstBlock = first.nextElementSibling;
    const secondBlock = second.nextElementSibling;

    api.revealTranslation(targets[0].id);
    assert.equal(firstBlock.classList.contains('hoshi-reader-translation-hidden'), false);

    api.revealTranslation(targets[1].id);
    assert.equal(secondBlock.classList.contains('hoshi-reader-translation-hidden'), false);
    assert.equal(firstBlock.classList.contains('hoshi-reader-translation-hidden'), true);
});
