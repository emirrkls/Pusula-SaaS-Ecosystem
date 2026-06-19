import React, { createContext, useContext, useEffect } from 'react';
import { applySeoToDocument } from './buildHead';

export const SeoCollectorContext = createContext(null);

export function PageSeo({
    title,
    description,
    path = '/',
    faqs,
    breadcrumbs,
    ogImage,
}) {
    const collector = useContext(SeoCollectorContext);
    const seo = { title, description, path, faqs, breadcrumbs, ogImage };

    if (collector) {
        Object.assign(collector, seo);
        return null;
    }

    useEffect(() => {
        applySeoToDocument(seo);
    }, [title, description, path, faqs, breadcrumbs, ogImage]);

    return null;
}
