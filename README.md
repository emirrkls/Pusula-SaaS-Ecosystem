# Pusula Service Ecosystem

**Pusula** is a multi-tenant SaaS platform for HVAC and field-service companies. A shared backend powers dispatch, field operations, inventory, finance, reporting, subscriptions, notifications, and opt-in service networks while keeping every company's operational data isolated.

> **Languages:** English (this file) · [Türkçe](README.tr.md)

| Component | Stack | Description |
|-----------|-------|-------------|
| **Backend API** | Spring Boot 3 · Java 17 · PostgreSQL | REST API, JWT auth, tenant isolation |
| **Web (Marketing)** | React 19 · Vite · Tailwind CSS | Corporate site, local SEO landings, SSG prerender |
| **Desktop** | JavaFX 21 · Java 21 | Office / dispatch management (Windows), MSI auto-update |
| **Android** | Kotlin · Jetpack Compose · Hilt | Google Play field & admin mobile app |
| **iOS** | SwiftUI · StoreKit · APNs | App Store mobile app with push notifications |

---

## Table of Contents

- [Features](#features)
- [Architecture](#architecture)
- [Service Network Workflow](#service-network-workflow)
- [Repository Structure](#repository-structure)
- [Prerequisites](#prerequisites)
- [Quick Start](#quick-start)
- [Environment Variables](#environment-variables)
- [Database Migrations](#database-migrations)
- [Testing](#testing)
- [Production Deployment](#production-deployment)
- [Security](#security)
- [API Overview](#api-overview)
- [Related Documentation](#related-documentation)

---

## Features

### Operations
- Service tickets with customer/technician search, assignment windows, private technician instructions, status tracking, controlled rescheduling, reopening, signatures, and backdated completion
- Separate service sale, labor, collection, current-account, direct-cost, and external-expense semantics
- Inventory and vehicle stock with barcode lookup, fractional quantities, custom per-job sale prices, critical-stock alerts, and idempotent part usage
- Searchable service-photo archive with thumbnails, categories, notes, camera/gallery capture, download, and ticket/customer/date context
- Proposals with inventory-backed line items, customer search, status categories, PDF output, and conversion to service work
- Customer current accounts with transaction history; company debts with dated additions and partial/full payments
- Business assets/tools and inventory valuation with PDF exports
- Monthly profitability reports, current-account/debt snapshots, and operational dashboards
- In-app admin notification center plus mobile push delivery for relevant service events

### Platform
- **Multi-tenant architecture:** Each company is data-isolated; tenant context is resolved from JWT automatically (including vehicle and inventory mutation isolation).
- **Role-based access:** `SUPER_ADMIN`, `COMPANY_ADMIN`, `TECHNICIAN`, and super-admin sub-roles.
- **Subscriptions & quotas:** Centrally defined plan capabilities and usage limits for Free, Usta, and Patron tiers.
- **Opt-in service networks:** An entitled parent company can create or invite isolated child companies, dispatch orders, and follow acceptance, rejection, cancellation, notes, and lifecycle updates without sharing tenant data.
- **Google Play subscription verification:** `POST /api/subscription/google-verify`
- **App Store subscription verification:** `POST /api/subscription/apple-verify`
- **APNs push scheduling:** Assignment notifications are delivered for today's/next-24-hour work and deferred until future work enters that window.
- **Tenant-scoped WhatsApp notifications:** Optional approved-template messages for service creation and completion, restricted to explicitly allowed companies.
- **Payment webhooks:** Iyzico webhook signature validation (optional / future-compatible).
- **Super-admin operations:** Company management, quota status, diagnostic packages, operations dashboard.

### Clients
- **Desktop:** Full office/dispatch management, modern responsive dialogs, finance and asset tooling, service-photo browser, service-network administration, PDF reports, and verified MSI auto-update.
- **Android / iOS:** Field technician and company-admin flows, onboarding, customer/inventory search, service media, controlled rescheduling, notifications, Google/Apple sign-in, and Play Billing/StoreKit support.
- **Web:** Public marketing site with local SEO landing pages, price list, authorized brands, contact form, privacy/terms, and SSG prerender for public routes.

---

## Architecture

```mermaid
flowchart TB
    subgraph clients [Clients]
        WEB[frontend-web<br/>React / Vite / SSG]
        DESK[frontend-desktop<br/>JavaFX + MSI update]
        AND[frontend-playstore<br/>Android]
        IOS[frontend-appstore<br/>iOS]
    end

    subgraph backend [Backend]
        API[Spring Boot API<br/>JWT + Tenant Context + Flyway]
        DB[(PostgreSQL)]
    end

    subgraph external [External Services]
        GP[Google Play Billing]
        AS[Apple App Store]
        APNS[Apple APNs]
        GAuth[Google OAuth]
        IYZ[Iyzico Webhook]
        WA[Meta WhatsApp Cloud API]
    end

    WEB -->|HTTPS REST| API
    DESK -->|HTTPS REST| API
    AND -->|HTTPS REST| API
    IOS -->|HTTPS REST| API

    API --> DB
    AND --> GP
    IOS --> AS
    IOS --> APNS
    API --> AS
    API --> APNS
    AND --> GAuth
    API --> GAuth
    API --> IYZ
    API --> WA
```

**Authentication flow:** Clients authenticate through `/api/auth/authenticate` (or a supported identity-provider flow). Subsequent requests send `Authorization: Bearer <token>`. `TenantInterceptor` resolves the company context. Repositories and services still enforce company ownership; the client is never trusted to choose another tenant.

**Service-network boundary:** Parent and child companies remain separate tenants. Network tables carry explicit parent/child identifiers and immutable name snapshots. Accepting a network order creates a normal ticket inside the child company; it does not grant either company access to the other's customers, inventory, finance, users, or tickets.

---

## Service Network Workflow

Service networks are opt-in and separate from ordinary subscription-plan access. A super administrator enables a policy for a parent company and sets member and monthly dispatch limits.

1. A parent company administrator either creates a new child service (with its own company-admin account and isolated tenant) or invites an existing company by organization code.
2. An existing company must accept the invitation; a newly created child is linked immediately and begins with the configured trial behavior.
3. The parent dispatches a dated network order with customer contact/address details and private instructions.
4. The child accepts the order, optionally selects an existing customer and technician, and receives a normal ticket in its own company. Alternatively, the child may reject a still-pending order; the parent may cancel it.
5. Notes and ticket lifecycle updates remain visible through the network-order history. A relationship cannot be closed while it has pending or non-terminal work.

Only company administrators and super administrators can use the network API. A child service cannot open a second-level network. Requests that create children or dispatch work use idempotency keys so safe retries do not duplicate companies, accounts, or orders.

---

## Repository Structure

```
Pusula-SaaS-Ecosystem/
├── backend/                    # Spring Boot REST API
│   ├── src/main/java/          # Controllers, services, entities, DTOs
│   ├── src/main/resources/     # Configuration, legacy bootstrap SQL, fonts
│   ├── src/main/resources/db/migration/ # Active Flyway migrations (baseline 20, V21–V36)
│   ├── src/test/               # JUnit regression tests
│   ├── deploy_vps_staging.sh   # VPS deployment helper
│   └── .env.example            # Backend env template
├── frontend-web/               # Marketing / corporate website (Vercel + SSG)
├── frontend-desktop/           # JavaFX desktop application (Windows / MSI)
├── frontend-playstore/         # Android (Google Play) app
│   └── PusulaService/
├── frontend-appstore/          # iOS (App Store) app
│   └── PusulaService/
├── Pusula-Super-Admin-Panel/   # Super-admin web application
├── docs/                       # Architecture and feature notes
├── scripts/                    # Helper scripts (e.g. Play Store assets)
├── RUNBOOK.md                  # Production rollout checklist
├── README.md                   # English documentation (this file)
└── README.tr.md                # Turkish documentation
```

> Some directories may have their own build or deployment lifecycle. The root CI workflow currently verifies the backend and desktop projects; service-network validation adds PostgreSQL integration tests and an unsigned iOS simulator build.

---

## Prerequisites

| Tool | Version | Used for |
|------|---------|----------|
| **Java (JDK)** | 17 | Backend |
| **Java (JDK)** | 21 | Desktop (JavaFX) |
| **Maven** | 3.8+ | Backend & desktop builds |
| **PostgreSQL** | 14+ | Database |
| **Node.js** | 18+ | Web frontend |
| **Android Studio** | Latest | Android development |
| **Xcode** | Compatible with the iOS 17 SDK / SwiftUI project | iOS development |

---

## Quick Start

### 1. Backend

```bash
# Create the PostgreSQL database
createdb pusula_db

# Set environment variables (copy the example file)
cp backend/.env.example backend/.env
# Fill in DB_PASSWORD and JWT_SECRET in backend/.env

# Build and run
cd backend
mvn spring-boot:run
```

- **Local port:** `8081` (`application.properties`)
- **VPS profile:** activate with `spring.profiles.active=vps` → uses `application-vps.properties` (port `8080`)
- **Auth endpoints:** `/api/auth/*` (password login: `/api/auth/authenticate`)

### 2. Web (`frontend-web`)

```bash
cd frontend-web
cp .env.example .env
npm install
npm run dev
```

- **Dev server:** Vite default (`http://localhost:5173`)
- **Production build:** `npm run build` runs Vite client build, SSR build, then `scripts/prerender.mjs` (SSG for public routes) → deploy `dist/` to Vercel or static hosting
- **SPA routing:** configured via `vercel.json` rewrites

### 3. Desktop (`frontend-desktop`)

```bash
cd frontend-desktop
mvn javafx:run
```

Alternatively, run the main class `com.pusula.desktop.Launcher` from your IDE.

- **API base URL:** `RetrofitClient.BASE_URL` (production: `https://api.pusulaiklimlendirme.com/`)
- **App version:** `frontend-desktop/src/main/resources/app-version.properties`
- **Auto-update:** desktop checks `/api/public/desktop-version` and applies MSI updates
- **Windows installer output:** `frontend-desktop/installer/Output/` (gitignored)

### 4. Android (`frontend-playstore`)

Create `frontend-playstore/PusulaService/local.properties` (**never commit this file**):

```properties
# API
debug.api.base.url=https://api.pusulaiklimlendirme.com
release.api.base.url=https://api.pusulaiklimlendirme.com

# Google Sign-In
google.web.client.id=YOUR_GOOGLE_WEB_CLIENT_ID

# Release signing (required for Play Store uploads)
release.keystore.path=keystore/upload-keystore.jks
release.keystore.password=YOUR_KEYSTORE_PASSWORD
release.key.alias=upload
release.key.password=YOUR_KEY_PASSWORD
```

```bash
cd frontend-playstore/PusulaService
./gradlew assembleDebug        # Debug APK
./gradlew assembleRelease      # Release APK (when signing is configured)
```

- **Application ID:** `com.pusula.service`
- **Min SDK:** 26 · **Target SDK:** 35

### 5. iOS (`frontend-appstore`)

1. Open `frontend-appstore/PusulaService/` in Xcode (`PusulaService.xcodeproj`).
2. API base URL: `Services/NetworkManager.swift`
3. StoreKit integration: `Services/StoreKitManager.swift`
4. Push notifications: enable the **Push Notifications (APNs)** capability; client registration uses `/api/push-devices`.
5. Configure signing & capabilities with your Apple Developer account.
6. The repository also contains a screenshot-test target used for store assets; never capture production tenant data for store submissions.
7. For device testing notes, see `frontend-appstore/REAL_DEVICE_TEST_PLAN.md`.

---

## Environment Variables

### Backend (production — required)

| Variable | Description |
|----------|-------------|
| `DB_PASSWORD` | PostgreSQL password |
| `JWT_SECRET` | JWT signing secret (64+ characters recommended) |
| `GOOGLE_WEB_CLIENT_ID` | Google OAuth web client ID |
| `GOOGLE_PLAY_PACKAGE_NAME` | Android package name |
| `GOOGLE_PLAY_API_ACCESS_TOKEN` | Google Play Developer API access token |
| `IYZICO_WEBHOOK_SECRET` | Iyzico webhook signature secret |
| `APP_DEPLOY_VERSION` | Deploy version label (e.g. `2026.06.13-1`) |

### Backend — App Store & APNs

| Variable | Description |
|----------|-------------|
| `APPLE_APP_STORE_BUNDLE_ID` | App Store bundle ID (default: `com.pusula.service`) |
| `APPLE_APP_STORE_APP_APPLE_ID` | Numeric App Store app Apple ID |
| `APPLE_APP_STORE_ENVIRONMENTS` | Verification environments (default: `SANDBOX,PRODUCTION`) |
| `APPLE_APP_STORE_ROOT_CERTIFICATE_PATHS` | Paths to Apple root certificates (comma-separated) |
| `APPLE_APP_STORE_ENABLE_ONLINE_CHECKS` | Enable online App Store checks (default: `true`) |
| `APPLE_PUSH_ENABLED` | Enable APNs push delivery (default: `false`) |
| `APPLE_PUSH_KEY_PATH` | Path to APNs `.p8` auth key |
| `APPLE_PUSH_KEY_ID` | APNs key ID |
| `APPLE_PUSH_TEAM_ID` | Apple Developer Team ID |
| `APPLE_PUSH_BUNDLE_ID` | Push topic / bundle ID (default: `com.pusula.service`) |
| `PUSH_TOKEN_ENCRYPTION_KEY` | Base64-encoded 32-byte AES key for push token encryption |

### Backend (optional)

| Variable | Description |
|----------|-------------|
| `WHATSAPP_API_TOKEN` | WhatsApp notification API token |
| `WHATSAPP_PHONE_ID` | WhatsApp phone number ID |
| `WHATSAPP_API_ENABLED` | Master switch for WhatsApp delivery |
| `WHATSAPP_API_PROVIDER` / `WHATSAPP_GRAPH_API_VERSION` | Provider and Graph API version |
| `WHATSAPP_ALLOWED_COMPANY_IDS` | Explicit tenant allow-list; empty means no tenant can send |
| `WHATSAPP_TEMPLATE_LANGUAGE` | Approved template language code |
| `WHATSAPP_TEMPLATE_SERVICE_CREATED` / `WHATSAPP_TEMPLATE_SERVICE_COMPLETED` | Approved Meta template names |
| `IYZICO_API_KEY` / `IYZICO_API_SECRET` | Iyzico payments (sandbox defaults exist for dev) |
| `IYZICO_BASE_URL` / `IYZICO_CALLBACK_URL` | Iyzico API base and webhook callback URL |
| `APP_BUSINESS_TIMEZONE` | Business timezone (default: `Europe/Istanbul`) |

Templates: `backend/.env.example`, `backend/src/main/resources/application.properties`, `backend/src/main/resources/application-vps.properties`

### Web

| Variable | Description |
|----------|-------------|
| `VITE_API_BASE_URL` | Backend API URL |
| `VITE_COMPANY_ID` | Contact form tenant ID |

Template: `frontend-web/.env.example`

---

## Database Migrations

Production Flyway uses `classpath:db/migration`, baseline version `20`, with Hibernate schema mutation disabled (`ddl-auto=none`). The active sequence currently runs from V21 through V36:

| Range | Main changes |
|-------|--------------|
| `V21` | Financial integrity and sale/collection metadata |
| `V22–V24` | Ticket reopening, centralized plan limits, warranty completion, technician notes |
| `V25–V29` | Idempotent/custom-priced used parts, onboarding, scheduled windows/push tracking, fractional inventory, private assignment notes |
| `V30–V34` | Service-photo catalog/archive, current-account ledger, controlled rescheduling, admin notification center, archive indexes |
| `V35–V36` | Tenant-isolated service networks and idempotent child-company creation |

Legacy bootstrap/evolution scripts remain directly under `backend/src/main/resources/` for historical installations, but they are **not** in the active production Flyway location. Do not rename, reorder, or edit an applied migration. Add a new versioned migration instead.

Manual recovery/maintenance scripts under `backend/src/main/resources/db/manual/` are never applied automatically. Production deployment must take a verified database backup before migrations and verify `flyway_schema_history` afterwards.

---

## Testing

```bash
cd backend
mvn verify

cd ../frontend-desktop
mvn verify
```

Coverage includes:
- Auth rate limiting and JWT handling
- Payment webhook security and provider isolation
- Google Play / App Store verify idempotency and renewals
- APNs push listener / device registration behavior
- Super-admin validation & audit
- Feature/quota consistency
- Tenant isolation (e.g. vehicles) and inventory mutation security
- Finance / report semantics (pricing snapshots, current-account classification, open balances)
- Ticket reopening, warranty completion, custom/fractional part usage, photo archive, and controlled rescheduling
- Service-network tenant isolation, idempotency, concurrency, quotas, and ticket lifecycle (PostgreSQL integration suite)

GitHub Actions runs backend verification on Java 17 and desktop verification on Java 21. The service-network workflow also starts PostgreSQL 17 and compiles the iOS app for an unsigned simulator. App Store/TestFlight distribution remains a separate release action.

---

## Production Deployment

### Backend (VPS)

```bash
export DB_PASSWORD='...'
export JWT_SECRET='...'
export GOOGLE_WEB_CLIENT_ID='...'
# Other production env vars (Play, App Store, APNs, Iyzico)...

cd backend
bash deploy_vps_staging.sh
```

Spring profile: `-Dspring.profiles.active=vps`

### Web (Vercel)

Connect the `frontend-web` directory to Vercel. Build command: `npm run build` (includes SSG prerender), output directory: `dist`.

### Mobile

- **Android:** Release APK/AAB → Google Play Console
- **iOS:** Archive → App Store Connect (ensure APNs key and App Store Server verification env are configured on the API)

For post-deploy smoke tests, see **[`RUNBOOK.md`](RUNBOOK.md)**.

---

## Security

- Never commit JWT secrets, database passwords, APNs keys, or signing keys to the repository.
- `.gitignore` covers: `.env`, `local.properties`, `*.jks`, `keystore/`, `backend/scripts/` (mock data).
- Do not rely on Iyzico sandbox fallback values in production; supply all secrets via environment variables.
- Push device tokens are encrypted at rest when `PUSH_TOKEN_ENCRYPTION_KEY` is configured.
- Service-network child-account creation stores idempotency receipts but never stores or fingerprints the supplied password.
- WhatsApp delivery fails closed unless the feature is enabled and the tenant is explicitly allow-listed.
- Android HTTP logging uses `SensitiveHttpLogRedactor` to mask tokens and passwords.
- Inventory mutations and vehicle access are tenant-scoped on the backend.

---

## API Overview

| Prefix | Description |
|--------|-------------|
| `/api/auth` | Login, register, Google auth |
| `/api/tickets` | Service tickets, assignment, lifecycle, completion, signature, reopening, notes, and rescheduling |
| `/api/inventory` | Inventory management |
| `/api/service-photos` | Service photo upload, archive, filtering, thumbnail, and download metadata |
| `/api/finance` | Finance operations |
| `/api/current-accounts` | Current-account management |
| `/api/company-debts` | Company debt tracking |
| `/api/business-assets` | Business asset tracking |
| `/api/admin` | Company admin dashboard |
| `/api/superadmin` | Super-admin operations |
| `/api/subscription` | Plans, Google Play verify, App Store verify |
| `/api/payment` | Payments & webhooks |
| `/api/push-devices` | Mobile push device registration (APNs) |
| `/api/notifications` | Tenant-scoped user notification center |
| `/api/service-network` | Opt-in child-service membership, dispatch, decision, history, and status flows |
| `/api/reports` | Reporting (profitability, cash flow, open debt, etc.) |
| `/api/public` | Unauthenticated public endpoints |
| `/api/public/desktop-version` | Desktop MSI auto-update version check |

---

## Related Documentation

- [`RUNBOOK.md`](RUNBOOK.md) — Production deploy checklist, smoke test plan, env references
- [`README.tr.md`](README.tr.md) — Turkish documentation
- [`frontend-appstore/REAL_DEVICE_TEST_PLAN.md`](frontend-appstore/REAL_DEVICE_TEST_PLAN.md) — iOS real-device test plan
- [`scripts/`](scripts/) — Play Store asset generation helpers

---

## License

This is a private SaaS ecosystem. Distribution and usage rights belong to the project owner.

## Contact

- **Website:** [pusulaiklimlendirme.com](https://pusulaiklimlendirme.com)
- **Email:** pusulaiklimlendirme.didim@gmail.com
- **GitHub:** [emirrkls/Pusula-SaaS-Ecosystem](https://github.com/emirrkls/Pusula-SaaS-Ecosystem)
