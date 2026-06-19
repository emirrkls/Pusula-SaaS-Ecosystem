import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';
import { PRERENDER_ROUTES } from '../src/seo/prerender-routes.js';

const __dirname = path.dirname(fileURLToPath(import.meta.url));
const distDir = path.join(__dirname, '..', 'dist');
const templatePath = path.join(distDir, 'index.html');
const serverEntry = path.join(distDir, 'ssr', 'entry-server.js');

if (!fs.existsSync(templatePath)) {
    console.error('Missing dist/index.html — run vite build first.');
    process.exit(1);
}

if (!fs.existsSync(serverEntry)) {
    console.error('Missing SSR bundle — run vite build --ssr first.');
    process.exit(1);
}

const template = fs.readFileSync(templatePath, 'utf-8');
const { render } = await import(pathToFileURL(serverEntry).href);

const rootMarker = '<div id="root"></div>';

function injectPage(templateHtml, appHtml, head) {
    let html = templateHtml.replace(rootMarker, `<div id="root">${appHtml}</div>`);
    html = html.replace('</head>', `${head}\n  </head>`);
    return html;
}

function writeRoute(route, html) {
    const outFile =
        route === '/'
            ? path.join(distDir, 'index.html')
            : path.join(distDir, route.slice(1), 'index.html');

    fs.mkdirSync(path.dirname(outFile), { recursive: true });
    fs.writeFileSync(outFile, html, 'utf-8');
    console.log(`  prerendered ${route}`);
}

console.log(`Prerendering ${PRERENDER_ROUTES.length} routes...`);

for (const route of PRERENDER_ROUTES) {
    const { appHtml, head } = render(route);
    writeRoute(route, injectPage(template, appHtml, head));
}

console.log('SSG prerender complete.');

fs.rmSync(path.join(distDir, 'ssr'), { recursive: true, force: true });
