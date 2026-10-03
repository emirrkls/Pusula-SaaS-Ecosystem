import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { REFERENCE_CLIENTS, REFERENCE_ORBITS, REFERENCE_ORBIT_CAPACITY } from '../src/data/references.js';
import { mainNavLinks, footerQuickLinks } from '../src/data/navigation.js';
import { PRERENDER_ROUTES } from '../src/seo/prerender-routes.js';

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

test('reference IDs are unique and names are nonempty', () => {
    assert.equal(REFERENCE_CLIENTS.length, 19);
    assert.equal(new Set(REFERENCE_CLIENTS.map(({ id }) => id)).size, REFERENCE_CLIENTS.length);
    for (const reference of REFERENCE_CLIENTS) {
        assert.match(reference.id, /^[a-z0-9-]+$/);
        assert.ok(reference.name.trim());
    }
});

test('all verified logos are local, nonempty files with official provenance', () => {
    const verified = REFERENCE_CLIENTS.filter(({ logo }) => logo);
    assert.equal(verified.length, 16);
    for (const reference of verified) {
        assert.match(reference.logo, /^\/assets\/img\/references\/[a-z0-9-]+\.(svg|png|jpg)$/);
        assert.equal(new URL(reference.source).protocol, 'https:');
        const asset = path.join(webRoot, 'public', reference.logo);
        assert.ok(fs.statSync(asset).size > 100);
        if (asset.endsWith('.svg')) {
            assert.doesNotMatch(fs.readFileSync(asset, 'utf8'), /<script\b|<foreignObject\b|javascript:|\bon\w+\s*=/i);
        }
    }
});

test('ambiguous companies never receive a guessed logo or source link', () => {
    assert.deepEqual(REFERENCE_CLIENTS.filter(({ logo }) => !logo).map(({ id }) => id), [
        'subasi-insaat', 'can-serhat-yapi', 'cs-can-yapi',
    ]);
    for (const reference of REFERENCE_CLIENTS.filter(({ logo }) => !logo)) {
        assert.equal(reference.source, null);
        assert.ok(reference.verificationNote);
    }
});

test('the initial orbit contains all references exactly once', () => {
    const orbitIds = REFERENCE_ORBITS.flatMap(({ references }) => references.map(({ id }) => id));
    assert.deepEqual(orbitIds, REFERENCE_CLIENTS.slice(0, REFERENCE_ORBIT_CAPACITY).map(({ id }) => id));
    assert.equal(new Set(orbitIds).size, orbitIds.length);
    assert.ok(REFERENCE_ORBITS[0].references.length <= 11);
    assert.ok(REFERENCE_ORBITS[1].references.length <= 8);
});

test('requested additions are present and Has Karaarslan is removed', () => {
    const ids = new Set(REFERENCE_CLIENTS.map(({ id }) => id));
    for (const id of ['ozsoy-yapi-muhendislik', 'cs-can-yapi', 'pilot-garage', 'emin-oto', 'eg-garaj', 'dfit-didim']) {
        assert.ok(ids.has(id), id);
    }
    assert.equal(ids.has('has-karaarslan-insaat'), false);
});

test('orbit geometry keeps all cards separated throughout rotation', () => {
    const css = fs.readFileSync(path.join(webRoot, 'src/pages/References.css'), 'utf8');
    const radius = (selector) => Number(css.match(new RegExp(`${selector} \\{[^}]*--orbit-radius: (\\d+)px`))[1]);
    const outerRadius = radius('\\.reference-orbit-ring');
    const innerRadius = radius('\\.reference-orbit-ring--inner');
    const cardBlock = css.match(/\.reference-orbit-counter \{([^}]+)\}/)[1];
    const cardWidth = Number(cardBlock.match(/width: (\d+)px/)[1]);
    const cardHeight = Number(cardBlock.match(/height: (\d+)px/)[1]);
    const cardDiagonal = Math.hypot(cardWidth, cardHeight);
    for (const [index, orbit] of REFERENCE_ORBITS.entries()) {
        const ringRadius = index === 0 ? outerRadius : innerRadius;
        const adjacentDistance = 2 * ringRadius * Math.sin(Math.PI / orbit.references.length);
        assert.ok(adjacentDistance > cardDiagonal, orbit.id);
    }
    assert.ok(outerRadius - innerRadius > cardDiagonal, 'rings must not collide');
    const stageSize = Number(css.match(/\.reference-orbit \{[^}]*width: (\d+)px/)[1]);
    assert.ok(outerRadius + cardWidth / 2 < stageSize / 2);
    assert.ok(outerRadius + cardHeight / 2 < stageSize / 2);
});

test('references route is discoverable and prerendered', () => {
    for (const links of [mainNavLinks, footerQuickLinks]) {
        assert.equal(links.filter(({ path: href }) => href === '/referanslarimiz').length, 1);
    }
    assert.ok(PRERENDER_ROUTES.includes('/referanslarimiz'));
    const sitemap = fs.readFileSync(path.join(webRoot, 'public/sitemap.xml'), 'utf8');
    assert.ok(sitemap.includes('https://www.pusulaiklimlendirme.com/referanslarimiz'));
});
