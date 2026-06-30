import { useContext, useEffect, useMemo } from 'react';
import { applySeoToDocument } from './buildHead';
import { SeoCollectorContext } from './SeoCollectorContext';

export function PageSeo({
    title,
    description,
    path = '/',
    faqs,
    breadcrumbs,
    ogImage,
    noindex = false,
    structuredData,
}) {
    const collector = useContext(SeoCollectorContext);
    const seo = useMemo(
        () => ({ title, description, path, faqs, breadcrumbs, ogImage, noindex, structuredData }),
        [title, description, path, faqs, breadcrumbs, ogImage, noindex, structuredData]
    );

    useEffect(() => {
        if (!collector) {
            applySeoToDocument(seo);
        }
    }, [collector, seo]);

    if (collector) {
        Object.assign(collector, seo);
        return null;
    }

    return null;
}
