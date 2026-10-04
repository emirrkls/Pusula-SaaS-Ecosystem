import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { REFERENCE_CLIENTS, REFERENCE_ORBITS, REFERENCE_ORBIT_CAPACITY } from '../src/data/references.js';
import { mainNavLinks, footerQuickLinks } from '../src/data/navigation.js';
import { PRERENDER_ROUTES } from '../src/seo/prerender-routes.js';
import { AUTHORIZED_BRANDS, AUTHORIZED_BRANDS_SUMMARY } from '../src/data/authorizedBrands.js';

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

test('reference IDs are unique and names are nonempty', () => {
    assert.equal(REFERENCE_CLIENTS.length, 21);
    assert.equal(new Set(REFERENCE_CLIENTS.map(({ id }) => id)).size, REFERENCE_CLIENTS.length);
    for (const reference of REFERENCE_CLIENTS) {
        assert.match(reference.id, /^[a-z0-9-]+$/);
        assert.ok(reference.name.trim());
    }
});

test('all verified logos are local, nonempty files with official provenance', () => {
    const verified = REFERENCE_CLIENTS.filter(({ logo }) => logo);
    assert.equal(verified.length, 18);
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
    assert.ok(REFERENCE_ORBITS[0].references.length <= 13);
    assert.ok(REFERENCE_ORBITS[1].references.length <= 8);
});

test('requested additions are present and Has Karaarslan is removed', () => {
    const ids = new Set(REFERENCE_CLIENTS.map(({ id }) => id));
    for (const id of ['ozsoy-yapi-muhendislik', 'cs-can-yapi', 'pilot-garage', 'emin-oto', 'eg-garaj', 'dfit-didim', 'gigis-coffee-didim', 'kartal-ceyiz-evi']) {
        assert.ok(ids.has(id), id);
    }
    assert.equal(ids.has('has-karaarslan-insaat'), false);
});

test('brand network has unique IDs, accurate relationship roles and safe local logos', () => {
    assert.deepEqual(AUTHORIZED_BRANDS.map(({ id }) => id), [
        'hisense', 'untes', 'nibe', 'lg-monoblok', 'solimpeks',
        'midea-vrf', 'daikin-vrf', 'quatech', 'termodinamik',
        'baymak', 'varmeks',
    ]);
    assert.equal(new Set(AUTHORIZED_BRANDS.map(({ id }) => id)).size, AUTHORIZED_BRANDS.length);
    for (const brand of AUTHORIZED_BRANDS) {
        assert.deepEqual(brand.roles, brand.id === 'daikin-vrf'
            ? ['Proje odaklı iş ortaklığı']
            : ['baymak', 'varmeks'].includes(brand.id) ? ['Çalıştığımız marka']
            : ['Yetkili bayi', 'Yetkili servis']);
        if (brand.source) assert.equal(new URL(brand.source).protocol, 'https:');
        assert.match(brand.logo, /^\/assets\/img\/brands\/[a-z0-9-]+\.(svg|png|webp)$/);
        const asset = path.join(webRoot, 'public', brand.logo);
        assert.ok(fs.statSync(asset).size > 100);
        if (asset.endsWith('.svg')) {
            assert.doesNotMatch(fs.readFileSync(asset, 'utf8'), /<script\b|<foreignObject\b|javascript:|\bon\w+\s*=/i);
        }
    }
});

test('Baymak and Varmeks keep the requested product scope without inferred authorization', () => {
    const brands = Object.fromEntries(AUTHORIZED_BRANDS.map((brand) => [brand.id, brand]));
    assert.equal(brands.baymak.category, 'Split klima, ısı pompası ve havuz ısı pompası');
    assert.equal(brands.varmeks.category, 'Havuz ısı pompası sistemleri');
    assert.ok(AUTHORIZED_BRANDS_SUMMARY.includes('Split klima ve ısı pompasında Baymak; havuz ısı pompalarında Baymak ve Varmeks'));
    for (const id of ['baymak', 'varmeks']) {
        assert.equal(new URL(brands[id].source).protocol, 'https:');
        assert.equal(new URL(brands[id].logoSource).protocol, 'https:');
        assert.ok(!brands[id].roles.some((role) => role.includes('Yetkili')));
    }
    const png = fs.readFileSync(path.join(webRoot, 'public', brands.varmeks.logo));
    assert.equal(png.subarray(0, 8).toString('hex'), '89504e470d0a1a0a');
    assert.ok(png.readUInt32BE(16) >= 400, 'official Varmeks wordmark has sufficient resolution');
});

test('new authorizations and project partnership keep the owner-confirmed scope', () => {
    const brands = Object.fromEntries(AUTHORIZED_BRANDS.map((brand) => [brand.id, brand]));
    assert.equal(brands['midea-vrf'].category, 'VRF sistemleri');
    assert.equal(brands['daikin-vrf'].category, 'VRV / VRF sistemleri');
    assert.equal(brands.quatech.category, 'Klima sistemleri');
    assert.equal(brands.termodinamik.category, 'Isıtma sistemleri');
    for (const id of ['midea-vrf', 'daikin-vrf', 'quatech', 'termodinamik']) {
        assert.equal(new URL(brands[id].source).protocol, 'https:');
        assert.ok(AUTHORIZED_BRANDS_SUMMARY.includes(brands[id].name), id);
    }
});

test('vector wordmarks follow their artwork proportions without fixed canvas dimensions', () => {
    for (const file of ['untes.svg', 'nibe.svg', 'hisense.svg', 'lg-monoblok.svg', 'baymak.svg']) {
        const svg = fs.readFileSync(path.join(webRoot, 'public/assets/img/brands', file), 'utf8');
        const [opening] = svg.match(/<svg\b[^>]*>/);
        assert.doesNotMatch(opening, /\s(?:width|height)="/, file);
        const [, viewBox] = svg.match(/<svg\b[^>]*viewBox="([^"]+)"/);
        const [, , width, height] = viewBox.split(/\s+/).map(Number);
        assert.ok(width > 0 && height > 0, file);
        assert.ok(width / height > 1.5, `${file}: logo viewport must follow its wordmark aspect ratio`);
        if (['untes.svg', 'nibe.svg'].includes(file)) assert.ok(width / height > 3, file);
    }
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
