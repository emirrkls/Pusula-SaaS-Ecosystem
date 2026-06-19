import React from 'react';
import { renderToString } from 'react-dom/server';
import { MemoryRouter } from 'react-router-dom';
import App from './App.jsx';
import { SeoCollectorContext } from './seo/PageSeo.jsx';
import { buildHeadHtml } from './seo/buildHead.js';

export function render(url) {
    const seoCollector = {};
    const appHtml = renderToString(
        <SeoCollectorContext.Provider value={seoCollector}>
            <MemoryRouter initialEntries={[url]}>
                <App />
            </MemoryRouter>
        </SeoCollectorContext.Provider>,
    );

    return {
        appHtml,
        head: buildHeadHtml(seoCollector),
    };
}
