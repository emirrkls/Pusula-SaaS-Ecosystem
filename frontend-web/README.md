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

## Quality checks

```bash
npm run lint
npm run build
```

Both commands run in CI for pushes to `main` and pull requests.

## WhatsApp Embedded Signup

`/whatsapp-connect` does not contain or read Meta App ID / Configuration ID from
Vite environment variables. The authenticated desktop client starts an
onboarding session through the backend and forwards the backend `StartResponse`
values in the URL fragment together with the single-use state and expiry. The
fragment is validated before the Meta SDK is initialized and is not sent in HTTP
request logs or referrer headers.
