import { useEffect } from 'react';
import { SITE_URL, DEFAULT_OG_IMAGE } from './constants';
import { buildBreadcrumbSchema, buildFaqSchema } from './schema';
import { injectJsonLd, removeJsonLd } from './jsonLd';

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

export function usePageSeo({
    title,
    description,
    path = '/',
    faqs,
    breadcrumbs,
    ogImage = DEFAULT_OG_IMAGE,
}) {
    useEffect(() => {
        const canonicalUrl = `${SITE_URL}${path === '/' ? '/' : path}`;

        document.title = title;
        upsertMeta('name', 'description', description);
        setCanonical(canonicalUrl);
        upsertMeta('property', 'og:title', title);
        upsertMeta('property', 'og:description', description);
        upsertMeta('property', 'og:url', canonicalUrl);
        upsertMeta('property', 'og:image', ogImage);
        upsertMeta('name', 'twitter:title', title);
        upsertMeta('name', 'twitter:description', description);
        upsertMeta('name', 'twitter:image', ogImage);

        injectJsonLd('seo-faq-schema', buildFaqSchema(faqs));
        injectJsonLd('seo-breadcrumb-schema', buildBreadcrumbSchema(breadcrumbs, SITE_URL));

        return () => {
            removeJsonLd('seo-faq-schema');
            removeJsonLd('seo-breadcrumb-schema');
        };
    }, [title, description, path, faqs, breadcrumbs, ogImage]);
}
