import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { PRERENDER_ROUTES } from '../src/seo/prerender-routes.js';
import { AUTHORIZED_BRANDS, BRAND_NETWORK_TITLE, BRAND_NETWORK_LIST_LABEL, AUTHORIZED_BRANDS_SUMMARY } from '../src/data/authorizedBrands.js';
import { landingPages } from '../src/pages/landings/landingPages.js';

const dist = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../dist');
const brandCardStyles = fs.readFileSync(new URL('../src/components/AuthorizedBrandsSection.css', import.meta.url), 'utf8');
const readRoute = (route) => fs.readFileSync(path.join(dist, route.slice(1), 'index.html'), 'utf8');

for (const route of PRERENDER_ROUTES) {
    test(`${route} has prerendered page content and its own canonical metadata`, () => {
        const html = readRoute(route);
        assert.match(html, /<div id="root"><[a-z]/);
        assert.match(html, /<h1\b/);
        assert.match(html, /<title>[^<]+<\/title>/);
        assert.ok(html.includes(`href="https://www.pusulaiklimlendirme.com${route}"`), route);
        assert.ok(html.includes('application/ld+json'), route);
    });
}

test('the built stylesheet retains brand, responsive and form utilities after migration', () => {
    const css = fs.readdirSync(path.join(dist, 'assets'))
        .filter((name) => name.endsWith('.css'))
        .map((name) => fs.readFileSync(path.join(dist, 'assets', name), 'utf8'))
        .join('\n');
    for (const selector of [
        '.bg-brand-dark', '.text-brand-cyan', '.sm\\:grid-cols-2', '.lg\\:grid-cols-3',
        '.outline-hidden', '.bg-linear-to-t', '.shadow-xs',
    ]) {
        assert.ok(css.includes(selector), `Missing compiled utility: ${selector}`);
    }
    assert.ok(css.includes('overflow-wrap:anywhere'));
});

test('all brands share one section on each brand page without duplicate cards', () => {
    for (const route of ['/', '/hakkimizda', '/hizmetler']) {
        const html = readRoute(route);
        assert.equal((html.match(/id="authorized-brands-heading"/g) || []).length, 1, route);
        assert.equal((html.match(/data-brand-id=/g) || []).length, AUTHORIZED_BRANDS.length, route);
        assert.ok(html.includes(BRAND_NETWORK_TITLE), route);
        assert.ok(html.includes(`aria-label="${BRAND_NETWORK_LIST_LABEL}"`), route);
        assert.ok(!html.includes('data-partner-id='), route);
        for (const { id, logo } of AUTHORIZED_BRANDS) {
            assert.equal(html.split(`data-brand-id="${id}"`).length - 1, 1, `${route}: ${id}`);
            assert.ok(html.includes(`src="${logo}"`), `${route}: ${logo}`);
        }
    }
});

test('brand cards retain subtle pointer-only hover effects and reduced-motion support', () => {
    assert.match(brandCardStyles, /@media \(hover: hover\) and \(pointer: fine\)/);
    assert.match(brandCardStyles, /transform: translateY\(-4px\)/);
    assert.match(brandCardStyles, /\.authorized-brand-card--dark\s*\{/);
    assert.match(brandCardStyles, /@media \(prefers-reduced-motion: reduce\)[\s\S]*transition: none;[\s\S]*transform: none;/);

    const css = fs.readdirSync(path.join(dist, 'assets'))
        .filter((name) => name.endsWith('.css'))
        .map((name) => fs.readFileSync(path.join(dist, 'assets', name), 'utf8'))
        .join('\n');
    for (const selector of ['.authorized-brand-card:hover', '.authorized-brand-logo', '.authorized-brand-card--dark']) {
        assert.ok(css.includes(selector), `Missing compiled card style: ${selector}`);
    }
});

test('brand names, system scopes and local service copy remain visible in prerendered HTML', () => {
    for (const route of ['/', '/hakkimizda', '/hizmetler']) {
        const html = readRoute(route);
        assert.ok(html.includes('Didim, Akbük ve Altınkum’da klima, VRF'), route);
        assert.ok(html.includes('proje odaklı iş ortaklarımızla birlikte'), route);
        for (const { id, name, category, roles } of AUTHORIZED_BRANDS) {
            const card = html.match(new RegExp(`<li\\b[^>]*data-brand-id="${id}"[^>]*>([\\s\\S]*?)</li>`));
            assert.ok(card, `${route}: ${id}`);
            assert.ok(card[1].includes(`>${name}</h3>`), `${route}: ${name}`);
            assert.ok(card[1].includes(`>${category}</p>`), `${route}: ${category}`);
            assert.ok(!card[1].includes(roles.join(' · ')), `${route}: ${name} must not show relationship badges`);
            assert.equal((card[1].match(/<p\b/g) || []).length, 1, `${route}: ${name} shows only its system scope`);
            assert.ok(!card[0].includes('tabindex='), `${route}: decorative cards must not enter the tab order`);
        }
    }
});

test('VRF and service FAQ content distinguish authorization from the Daikin partnership', () => {
    const vrf = readRoute('/didim-vrf-servisi');
    const services = readRoute('/hizmetler');
    assert.ok(vrf.includes('Üntes ve Midea VRF sistemlerinde yetkili bayi ve servis'));
    assert.ok(vrf.includes('Daikin VRV / VRF sistemlerinde proje odaklı iş ortaklığı'));
    assert.ok(services.includes('Hisense ve Quatech klima sistemlerinde yetkili bayi ve servis'));
    for (const name of ['Midea', 'Daikin', 'Quatech', 'Termodinamik']) {
        assert.ok(services.includes(name), name);
    }
    assert.ok(services.includes('Midea yetki kapsamımız VRF sistemleriyle sınırlıdır.'));
    assert.doesNotMatch(vrf, /Daikin[^.!?<>\n]*yetkili(?: bayi| servis)/i);
    assert.ok(!vrf.includes('Daikin VRV / VRF Yetkili Servis'));
});

test('the About brand section uses the same light surface as the other pages', () => {
    const html = readRoute('/hakkimizda');
    assert.match(html, /<section aria-labelledby="authorized-brands-heading" class="bg-gray-50 py-16 md:py-20">/);
    assert.ok(!html.includes('authorized-brand-card--dark'));
});

test('every service landing page visibly describes the current brands and their scopes', () => {
    for (const page of Object.values(landingPages)) {
        const html = readRoute(`/${page.slug}`);
        assert.ok(html.includes('id="service-brands-heading"'), page.slug);
        assert.ok(html.includes(`>${AUTHORIZED_BRANDS_SUMMARY}</p>`), page.slug);
        assert.doesNotMatch(html, /Daikin[^.!?<>\n]*yetkili(?: bayi| servis)/i, page.slug);
    }
});

test('Baymak is visible in split and heat-pump pages, and pool heating is a discoverable child service', () => {
    for (const route of ['/didim-split-klima-servisi', '/didim-isi-pompasi-servisi']) {
        const html = readRoute(route);
        assert.match(html, /<title>[^<]*Baymak[^<]*<\/title>/, route);
        assert.ok(html.includes('Baymak'), route);
    }
    for (const route of ['/', '/hizmetler', '/didim-isi-pompasi-servisi']) {
        assert.ok(readRoute(route).includes('href="/didim-havuz-isi-pompasi"'), route);
    }
    const pool = readRoute('/didim-havuz-isi-pompasi');
    assert.ok(pool.includes('Baymak ve Varmeks'), 'pool content includes both brands');
    assert.ok(pool.includes('aria-label="Hizmet konumu"'), 'visible parent navigation');
    const schemas = [...pool.matchAll(/<script\b[^>]*type="application\/ld\+json"[^>]*>([\s\S]*?)<\/script>/g)]
        .flatMap(([, json]) => JSON.parse(json));
    const breadcrumb = schemas.find((schema) => schema['@type'] === 'BreadcrumbList');
    assert.deepEqual(breadcrumb.itemListElement.map(({ item }) => item), [
        'https://www.pusulaiklimlendirme.com/',
        'https://www.pusulaiklimlendirme.com/didim-isi-pompasi-servisi',
        'https://www.pusulaiklimlendirme.com/didim-havuz-isi-pompasi',
    ]);
    const service = schemas.find((schema) => schema['@type'] === 'Service');
    assert.equal(service.name, landingPages.havuzIsiPompasi.serviceName);
    assert.equal(service.url, 'https://www.pusulaiklimlendirme.com/didim-havuz-isi-pompasi');
});

test('the contact email can wrap without losing its mail link', () => {
    const html = readRoute('/iletisim');
    assert.match(html, /href="mailto:pusulaiklimlendirme\.didim@gmail\.com" class="[^"]*\[overflow-wrap:anywhere\]/);
});
