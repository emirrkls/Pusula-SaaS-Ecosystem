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
npm audit --audit-level=high
npm run build
node --test scripts/*.test.mjs
```

These checks run in CI for pushes to `main` and pull requests.

Tailwind 4 uses `@tailwindcss/postcss`; imports and vendor prefixing are handled
by that plugin rather than a separate Autoprefixer step. The existing brand
configuration is explicitly loaded by `@config` in `src/index.css`, and source
scanning is limited to `src/` and `index.html`. Small-shadow, gradient and form
outline utilities retain their pre-migration appearance. Browser baseline:
Safari 16.4+, Chrome 111+, Firefox 128+.

## References page

`/referanslarimiz` is a prerendered public page, linked from the header and footer.
`src/data/references.js` contains the owner-supplied reference list and the official
website/profile used to verify each logo. Logos are downloaded locally under
`public/assets/img/references/`; the page does not hotlink third-party CDN images.

- Desktop: two slow circular orbits with upright logos/captions, pause control,
  hover pause and a full-list alternative.
- Below 1200px: a readable, static grid. Reduced-motion preferences disable the
  desktop animation as well.
- The orbit showcases up to 21 references (13 outer / 8 inner); future additions remain visible in the
  full list/mobile grid. Adjust the geometry deliberately rather than crowding it.
- Unconfirmed logos use a neutral building icon, not a fabricated brand mark.
  **Subaşı İnşaat**, **Can Serhat Yapı** and **CS Can Yapı** still need the owner's
  exact business link/logo. CS Can Yapı has a matching Didim company-directory
  entry, but no official logo was verified.
- Verified spelling: **Espressolab**, **TRYP by Wyndham Didim**,
  **Akbük Palace Hotel & Residence**, **Gür Life Yapı İnşaat**,
  **Pilot Garage Didim** and **D’Fit Didim** (D’Fit Fitness & Fight Club).
  Sapphire's official profile uses **Safir Mimarlık**,
  while its logo reads **SAPPHIRE**; the owner-supplied display name is retained.
- The current list contains 21 businesses, including **Özsoy Yapı Mühendislik**,
  **CS Can Yapı**, **Pilot Garage Didim**, **Emin Oto**, **EG Garaj**, **D’Fit Didim**,
  **Gigi’s Coffee Didim** and **Kartal Çeyiz Evi** (Karaca Didim Bayi).
  Has Karaarslan was removed at the owner's request.
- Social-profile logo files are the publicly available originals (150px); replace
  them with owner-provided SVG/high-resolution artwork when available.

Names/logos belong to their respective owners. The list does not imply authorized
servicing, a brand-wide partnership or an endorsement. Before public release,
confirm the reference locations and permission to display the marks.

Run the reference data/asset regression checks with:

```bash
npm run build
node --test scripts/references.test.mjs scripts/references-build.test.mjs
```

## Business partners

`BUSINESS_PARTNERS` in `src/data/authorizedBrands.js` lists **Midea VRF**, **Daikin
VRV / VRF**, **Quatech Klima** and **Termodinamik Isıtma Sistemleri**. Original
logos are stored under `public/assets/img/brands/` with official-source links in
the data. `BusinessPartnersSection` displays them wherever the existing authorized
brand section is used. Partner cards are separate from authorized dealer/service
cards and do not imply an unverified brand authorization.

## WhatsApp Embedded Signup

`/whatsapp-connect` does not contain or read Meta App ID / Configuration ID from
Vite environment variables. The authenticated desktop client starts an
onboarding session through the backend and forwards the backend `StartResponse`
values in the URL fragment together with the single-use state and expiry. The
fragment is validated before the Meta SDK is initialized and is not sent in HTTP
request logs or referrer headers.

The page is only the public browser bridge. Credentials are exchanged and stored
by the backend; the web bundle must never contain the Meta App Secret, access
tokens, the credential-encryption key, or the webhook verify token.

Production dependencies:

- public route: `https://www.pusulaiklimlendirme.com/whatsapp-connect`
- onboarding API: `/api/integrations/whatsapp/onboarding-session`
- status API: `/api/integrations/whatsapp/status`
- webhook callback: `https://api.pusulaiklimlendirme.com/api/public/whatsapp/webhook`
- the Meta app must be published before production webhook traffic is delivered

The complete production checklist is in [RUNBOOK.md](../RUNBOOK.md#whatsapp-release-checks).
