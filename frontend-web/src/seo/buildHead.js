import { SITE_URL, DEFAULT_OG_IMAGE } from './constants';
import { buildBreadcrumbSchema, buildFaqSchema } from './schema';

export function buildHeadHtml({
    title,
    description,
    path = '/',
    faqs,
    breadcrumbs,
    ogImage = DEFAULT_OG_IMAGE,
    noindex = false,
    structuredData,
}) {
    const canonicalUrl = `${SITE_URL}${path === '/' ? '/' : path}`;
    const faqSchema = buildFaqSchema(faqs);
    const breadcrumbSchema = buildBreadcrumbSchema(breadcrumbs, SITE_URL);

    const tags = [
        `<title>${escapeHtml(title)}</title>`,
        `<meta name="description" content="${escapeAttr(description)}" />`,
        `<link rel="canonical" href="${escapeAttr(canonicalUrl)}" />`,
        `<meta property="og:title" content="${escapeAttr(title)}" />`,
        `<meta property="og:description" content="${escapeAttr(description)}" />`,
        `<meta property="og:url" content="${escapeAttr(canonicalUrl)}" />`,
        `<meta property="og:image" content="${escapeAttr(ogImage)}" />`,
        `<meta name="twitter:title" content="${escapeAttr(title)}" />`,
        `<meta name="twitter:description" content="${escapeAttr(description)}" />`,
        `<meta name="twitter:image" content="${escapeAttr(ogImage)}" />`,
    ];

    if (noindex) {
        tags.push('<meta name="robots" content="noindex, nofollow" />');
    }

    if (faqSchema) {
        tags.push(`<script type="application/ld+json">${JSON.stringify(faqSchema)}</script>`);
    }
    if (breadcrumbSchema) {
        tags.push(`<script type="application/ld+json">${JSON.stringify(breadcrumbSchema)}</script>`);
    }
    normalizeStructuredData(structuredData).forEach((item) => {
        tags.push(`<script type="application/ld+json">${JSON.stringify(item)}</script>`);
    });

    return tags.join('\n  ');
}

function escapeHtml(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');
}

function escapeAttr(value) {
    return String(value)
        .replace(/&/g, '&amp;')
        .replace(/"/g, '&quot;')
        .replace(/</g, '&lt;');
}

export function applySeoToDocument(seo) {
    if (!seo?.title) return;

    document.title = seo.title;
    upsertMeta('name', 'description', seo.description);
    setCanonical(`${SITE_URL}${seo.path === '/' ? '/' : seo.path}`);
    upsertMeta('property', 'og:title', seo.title);
    upsertMeta('property', 'og:description', seo.description);
    upsertMeta('property', 'og:url', `${SITE_URL}${seo.path === '/' ? '/' : seo.path}`);
    upsertMeta('property', 'og:image', seo.ogImage || DEFAULT_OG_IMAGE);
    upsertMeta('name', 'twitter:title', seo.title);
    upsertMeta('name', 'twitter:description', seo.description);
    upsertMeta('name', 'twitter:image', seo.ogImage || DEFAULT_OG_IMAGE);
    updateRobotsMeta(seo.noindex);
    upsertJsonLd('seo-faq-schema', buildFaqSchema(seo.faqs));
    upsertJsonLd('seo-breadcrumb-schema', buildBreadcrumbSchema(seo.breadcrumbs, SITE_URL));
    upsertJsonLdList('seo-extra-schema', normalizeStructuredData(seo.structuredData));
}

function normalizeStructuredData(data) {
    if (!data) return [];
    return Array.isArray(data) ? data.filter(Boolean) : [data];
}

function upsertMeta(attribute, key, content) {
    if (!content) return;
    let element = document.head.querySelector(`meta[${attribute}="${key}"]`);
    if (!element) {
        element = document.createElement('meta');
        element.setAttribute(attribute, key);
        document.head.appendChild(element);
    }
    element.setAttribute('content', content);
}

function setCanonical(href) {
    let element = document.head.querySelector('link[rel="canonical"]');
    if (!element) {
        element = document.createElement('link');
        element.rel = 'canonical';
        document.head.appendChild(element);
    }
    element.href = href;
}

function updateRobotsMeta(noindex) {
    const selector = 'meta[name="robots"][data-page-seo="true"]';
    let element = document.head.querySelector(selector);

    if (!noindex) {
        element?.remove();
        return;
    }

    if (!element) {
        element = document.createElement('meta');
        element.name = 'robots';
        element.dataset.pageSeo = 'true';
        document.head.appendChild(element);
    }
    element.content = 'noindex, nofollow';
}

function upsertJsonLd(id, data) {
    document.head.querySelector(`#${id}`)?.remove();
    if (!data) return;
    const script = document.createElement('script');
    script.type = 'application/ld+json';
    script.id = id;
    script.textContent = JSON.stringify(data);
    document.head.appendChild(script);
}

function upsertJsonLdList(idPrefix, items) {
    document.head.querySelectorAll(`script[id^="${idPrefix}-"]`).forEach((element) => element.remove());
    items.forEach((item, index) => {
        const script = document.createElement('script');
        script.type = 'application/ld+json';
        script.id = `${idPrefix}-${index}`;
        script.textContent = JSON.stringify(item);
        document.head.appendChild(script);
    });
}
