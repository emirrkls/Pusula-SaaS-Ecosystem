import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { REFERENCE_CLIENTS } from '../src/data/references.js';
import { AUTHORIZED_BRANDS, BRAND_NETWORK_TITLE } from '../src/data/authorizedBrands.js';

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const html = fs.readFileSync(path.join(webRoot, 'dist/referanslarimiz/index.html'), 'utf8');

const escapeHtml = (text) => text.replaceAll('&', '&amp;').replaceAll('"', '&quot;');

test('production route contains the reference content without executing JavaScript', () => {
    assert.ok(html.includes('Güçlü referanslar.'));
    for (const { name } of REFERENCE_CLIENTS) {
        assert.ok(html.includes(`<figcaption>${escapeHtml(name)}</figcaption>`), name);
    }
    assert.ok(!html.includes('has-karaarslan-insaat'));
});

test('production metadata uses the canonical references URL', () => {
    assert.match(html, /<title>Referanslarımız/);
    assert.ok(html.includes('<link rel="canonical" href="https://www.pusulaiklimlendirme.com/referanslarimiz"'));
    assert.ok(html.includes('application/ld+json'));
});

test('production assets are identical to the locally verified originals', () => {
    for (const { logo } of [...REFERENCE_CLIENTS.filter(({ logo }) => logo), ...AUTHORIZED_BRANDS]) {
        assert.deepEqual(
            fs.readFileSync(path.join(webRoot, 'dist', logo)),
            fs.readFileSync(path.join(webRoot, 'public', logo)),
            logo,
        );
    }
    assert.equal(fs.existsSync(path.join(webRoot, 'dist/assets/img/references/has-karaarslan-insaat.png')), false);
});

test('dealer, service and project-partner cards are prerendered in one network section', () => {
    const home = fs.readFileSync(path.join(webRoot, 'dist/index.html'), 'utf8');
    assert.ok(home.includes(BRAND_NETWORK_TITLE));
    assert.ok(!home.includes('business-partners-heading'));
    for (const { id, name } of AUTHORIZED_BRANDS) {
        assert.ok(home.includes(`data-brand-id="${id}"`), id);
        assert.ok(home.includes(escapeHtml(name)), name);
    }
});

test('motion styles counter-rotate captions and respect reduced motion', () => {
    const css = fs.readFileSync(path.join(webRoot, 'src/pages/References.css'), 'utf8');
    assert.match(css, /@keyframes reference-orbit-turn[\s\S]*rotate\(360deg\)/);
    assert.match(css, /@keyframes reference-orbit-counterturn[\s\S]*rotate\(-360deg\)/);
    assert.match(css, /@media \(prefers-reduced-motion: reduce\)[\s\S]*animation: none/);
    assert.match(css, /reference-orbit:hover[\s\S]*animation-play-state: paused/);
    assert.match(css, /\.reference-orbit \{[^}]*overflow: clip/);
});
