import assert from 'node:assert/strict';
import fs from 'node:fs';
import test from 'node:test';
import { serviceMenuGroups, serviceSectionLinks } from '../src/data/navigation.js';
import { landingPages } from '../src/pages/landings/landingPages.js';
import { PRERENDER_ROUTES } from '../src/seo/prerender-routes.js';

test('Split and Commercial Air Conditioning are separate system links in the services menu', () => {
    const group = serviceMenuGroups.find(({ label }) => label === 'Sistemler & Enerji');
    for (const [name, path] of [
        ['Split Klima', '/didim-split-klima-servisi'],
        ['Ticari Klima', '/didim-ticari-klima-servisi'],
    ]) {
        assert.equal(group.links.filter((link) => link.name === name && link.path === path).length, 1);
        assert.equal(serviceSectionLinks.filter((link) => link.path === path).length, 1);
        assert.equal(serviceMenuGroups.flatMap(({ links }) => links).filter((link) => link.path === path).length, 1);
    }
});

test('all landing pages and related services have complete, routable content', () => {
    const app = fs.readFileSync(new URL('../src/App.jsx', import.meta.url), 'utf8');
    const sitemap = fs.readFileSync(new URL('../public/sitemap.xml', import.meta.url), 'utf8');
    const pages = Object.entries(landingPages);
    assert.equal(new Set(pages.map(([, page]) => page.slug)).size, pages.length);
    for (const [key, page] of pages) {
        for (const field of ['slug', 'title', 'description', 'h1', 'subtitle']) {
            assert.ok(page[field]?.trim(), `${key}: ${field}`);
        }
        for (const field of ['intro', 'features', 'steps', 'faqs']) {
            assert.ok(page[field].length >= 2, `${key}: ${field}`);
        }
        assert.ok(app.includes(`path="/${page.slug}" element={<ServiceLandingPage pageKey="${key}"`), key);
        assert.equal(PRERENDER_ROUTES.filter((route) => route === `/${page.slug}`).length, 1, key);
        assert.equal(sitemap.split(`<loc>https://www.pusulaiklimlendirme.com/${page.slug}</loc>`).length - 1, 1, key);
        for (const related of page.related) assert.ok(landingPages[related], `${key}: ${related}`);
        if (page.parent) assert.ok(landingPages[page.parent].childServices.includes(key), `${key}: linked from parent`);
        for (const child of page.childServices || []) assert.equal(landingPages[child].parent, key, `${key}: ${child} belongs to this parent`);
    }
});

test('pool heating belongs to the heat-pump menu group and is not duplicated under air conditioning', () => {
    const group = serviceMenuGroups.find(({ label }) => label === 'Isı Pompaları');
    assert.deepEqual(group.links, [
        { name: 'Isı Pompası', path: '/didim-isi-pompasi-servisi' },
        { name: 'Havuz Isı Pompası', path: '/didim-havuz-isi-pompasi' },
    ]);
    for (const { path } of group.links) {
        assert.equal(serviceMenuGroups.flatMap(({ links }) => links).filter((link) => link.path === path).length, 1, path);
        assert.equal(serviceSectionLinks.filter((link) => link.path === path).length, 1, path);
    }
    const llms = fs.readFileSync(new URL('../public/llms.txt', import.meta.url), 'utf8');
    assert.ok(llms.includes('https://www.pusulaiklimlendirme.com/didim-havuz-isi-pompasi'));
    assert.equal(landingPages.havuzIsiPompasi.parent, 'isiPompasi');
});

test('new Baymak and Varmeks service copy does not infer authorized dealership or service status', () => {
    for (const page of Object.values(landingPages)) {
        for (const text of [page.title, page.description, ...page.intro, ...page.features, ...page.faqs.map(({ a }) => a)]) {
            assert.doesNotMatch(text, /(?:Baymak|Varmeks)[^.!?;]*yetkili(?: bayi| servis)/i, page.slug);
        }
    }
    const index = fs.readFileSync(new URL('../index.html', import.meta.url), 'utf8');
    assert.ok(index.includes('Baymak Split Klima'));
    assert.ok(index.includes('Baymak ve Varmeks Havuz Isı Pompası'));
    assert.doesNotMatch(index, /(?:Baymak|Varmeks)[^"\n]*Yetkili/i);
});

test('service copy and public structured data do not present Daikin as authorized', () => {
    for (const page of Object.values(landingPages)) {
        for (const text of [page.title, page.description, ...page.intro, ...page.features, ...page.faqs.map(({ a }) => a)]) {
            assert.doesNotMatch(text, /Daikin[^.!?]*yetkili(?: bayi| servis)/i, page.slug);
        }
    }
    const index = fs.readFileSync(new URL('../index.html', import.meta.url), 'utf8');
    assert.ok(index.includes('Daikin VRV / VRF Proje Çözümleri'));
    assert.ok(!index.includes('Daikin VRV / VRF Yetkili Servis'));
});
