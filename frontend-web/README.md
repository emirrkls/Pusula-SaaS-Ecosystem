# frontend-web

Pusula marketing / corporate website (React 19 · Vite · Tailwind CSS).

For project-wide setup, environment variables, SSG prerender, and deployment, see the root documentation:

- [README.md](../README.md) (English)
- [README.tr.md](../README.tr.md) (Türkçe)

## Local development

```bash
cp .env.example .env
npm install
npm run dev
```

## Production build

```bash
npm run build
```

This runs the Vite client build, an SSR build, then `scripts/prerender.mjs` to prerender public routes into `dist/`.
