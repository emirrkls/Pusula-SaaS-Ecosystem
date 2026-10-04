import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import test from 'node:test';
import { PRERENDER_ROUTES } from '../src/seo/prerender-routes.js';
import { BUSINESS_PARTNERS } from '../src/data/authorizedBrands.js';

const dist = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../dist');
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
        '.bg-brand-dark', '.text-brand-cyan', '.sm\\:grid-cols-2', '.lg\\:grid-cols-4',
        '.outline-hidden', '.bg-linear-to-t', '.shadow-xs',
    ]) {
        assert.ok(css.includes(selector), `Missing compiled utility: ${selector}`);
    }
    assert.ok(css.includes('overflow-wrap:anywhere'));
});

test('business partner cards are present on all existing brand-section pages', () => {
    for (const route of ['/', '/hakkimizda', '/hizmetler']) {
        const html = readRoute(route);
        for (const { id, logo } of BUSINESS_PARTNERS) {
            assert.ok(html.includes(`data-partner-id="${id}"`), `${route}: ${id}`);
            assert.ok(html.includes(`src="${logo}"`), `${route}: ${logo}`);
        }
    }
});

test('the contact email can wrap without losing its mail link', () => {
    const html = readRoute('/iletisim');
    assert.match(html, /href="mailto:pusulaiklimlendirme\.didim@gmail\.com" class="[^"]*\[overflow-wrap:anywhere\]/);
});
