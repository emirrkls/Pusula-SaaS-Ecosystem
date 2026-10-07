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

## Authorized dealerships, service brands and project partners

`AUTHORIZED_BRANDS` in `src/data/authorizedBrands.js` is the single source for the
eleven displayed brands: **Hisense**, **Üntes**, **Nibe**, **LG Monoblok**,
**Solimpeks**, **Midea**, **Daikin**, **Quatech**, **Termodinamik**, **Baymak**
and **Varmeks**. Each card
shows its product scope; relationship roles remain in the data and service copy,
but are not displayed as card badges. On 4 October 2026 the owner
clarified that **Daikin VRV / VRF is a project-focused partnership**, not an
authorized dealership/service. The other additions are Midea **VRF**,
Quatech **climate systems** and Termodinamik **heating systems**. Do not
expand authorizations to other product groups without owner confirmation.
Baymak is included for **split air conditioning, heat pumps and pool heat pumps**;
Varmeks is included for **pool heat pumps only**. The owner confirmed working
with these brands, not authorized dealer/service status, so that status is not
inferred. Their official-source URLs and original logo URLs are recorded in the
data. The Baymak SVG uses its original artwork and aspect ratio; Varmeks uses
the original transparent PNG wordmark from its official website.

`AuthorizedBrandsSection` uses one centered logo-card grid, with three columns
on desktop, two on tablet and one on mobile. All cards show the relevant product
scope, with subtle pointer-only hover effects and reduced-motion support.
Home, About and Services use the same light surface. The former separate
business-partner block is removed, avoiding duplicated or inconsistent status.
Original logo artwork remains under `public/assets/img/brands/`; Üntes and Nibe
SVG viewports are fitted to the visible artwork, with a 2% edge margin, without
altering their vector paths. Hisense and LG SVGs inherit the artwork aspect ratio
instead of unrelated fixed canvas dimensions. New brand assets retain their official-source
links. Logo provenance alone does not prove
dealer/service authorization. FAQs and the VRF landing page use the same scope.

`/didim-split-klima-servisi` and `/didim-ticari-klima-servisi` are distinct,
prerendered service pages linked from the desktop/mobile services menu and the
overview. The existing Hisense page stays available. Every service landing
page includes a short, visible summary of current brands and partnership scopes;
VRF copy and metadata distinguish Üntes/Midea authorization from Daikin project
work, and heat-pump content keeps Termodinamik's heating-system scope separate.

`/didim-havuz-isi-pompasi` is a distinct child service of the heat-pump page,
with Baymak/Varmeks scope, capacity-selection context, FAQs and a parent breadcrumb.
The desktop/mobile menu groups it under **Isı Pompaları**, not air conditioning.
Home, the services overview and the parent heat-pump page link to it. It is
included in prerendering, the sitemap, service structured data and `llms.txt`.

## Yacht and boat climate service

`/didim-yat-tekne-klima-servisi` is a prerendered service page for **maintenance and
fault repair of yacht/boat air-conditioning systems and refrigerators only**, linked from Home, Services, desktop/mobile service navigation
and the footer. It includes seasonal maintenance, symptoms, service steps and FAQs.
Marine brands and specific marina coverage have not been supplied by the owner;
the page does not inherit the land-based authorized-brand summary. Its service
structured data uses Didim as the service area and does not promise authorization,
free surveys or round-the-clock marine response.

The service CTA opens `/iletisim?hizmet=yat-tekne#service-request-form`, preselecting
**Yat / Tekne Klima ve Buzdolabı**. The form asks for marina/berth location and optional boat
name and device brand/model. Marine details are included in the existing public
API description field; switching to another service omits those details. Device
selection is retained after successful submission. Photos can be sent through
the existing WhatsApp contact rather than uploaded through the form.

The illustrative yacht photo is by **Francisco Gomes**, freely usable commercially
under the **Unsplash License**. Provenance is recorded in
`public/assets/img/service-yat-tekne-license.txt`. It is not an owner project photo.

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
