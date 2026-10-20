# Pusula Production Release Runbook

## Scope

This is the canonical checklist for backend, web, desktop, Android, and iOS releases. It replaces older one-off local deployment notes. Never place production secrets, access tokens, signing files, or database passwords in this repository.

## Release Gates

Before merging or deploying:

1. Confirm the worktree contains only intended changes with `git status --short` and `git diff --check`.
2. Run the checks appropriate to the changed components:
   - Backend: `mvn --batch-mode --no-transfer-progress verify`
   - Desktop: `mvn --batch-mode --no-transfer-progress verify`
   - Web: `npm ci`, `npm run lint`, `npm run build`
   - Android: `./gradlew --no-daemon testDebugUnitTest assembleDebug`
   - iOS: use the `iOS Compile Gate` workflow for an unsigned Simulator `build-for-testing`.
3. Confirm required GitHub Actions checks are green:
   - `CI` — backend, desktop, and web
   - `Android Validation`
   - `iOS Compile Gate`
   - `Service Network Validation` — PostgreSQL integration tests
4. Review new Flyway files under `backend/src/main/resources/db/migration`. Never edit an already-applied migration.

## Backend and Database Deployment

1. Record the current deployed commit, JAR, and database migration version.
2. Create a timestamped PostgreSQL custom-format backup before replacing the JAR. Verify that the backup is non-empty and can be listed with `pg_restore --list`.
3. Build from the exact commit being deployed. Transfer the JAR to a temporary path on the VPS, then atomically replace the service JAR.
4. Restart the `pusula-backend` systemd service. The service reads secrets from its protected environment file and starts with the `vps` profile.
5. Flyway automatically applies pending migrations from `classpath:db/migration` on startup. Hibernate schema mutation is disabled in production.
6. Verify:
   - `systemctl is-active pusula-backend` reports `active`.
   - `flyway_schema_history` contains every expected version with `success=true`.
   - Recent service logs contain no startup, migration, authentication, or database errors.
   - The public health/version endpoints and one authenticated tenant request succeed.

Machine-specific deployment commands intentionally stay outside the repository. This runbook and the active Flyway directory are the source of truth; do not revive old one-off migration helpers.

## Production Environment

The authoritative names and safe placeholders are in `backend/.env.example`. The VPS values belong in its protected environment file, not in shell history or Markdown.

Core variables include database/JWT, Google Play, App Store/APNs, Iyzico, deployment version, and the WhatsApp variables below:

- `WHATSAPP_API_ENABLED`, provider/version, templates, and explicit company allow-list
- `WHATSAPP_META_APP_ID`, `WHATSAPP_META_APP_SECRET`, and `WHATSAPP_META_CONFIGURATION_ID`
- `WHATSAPP_CREDENTIAL_ENCRYPTION_KEY` for encrypted tenant credentials
- `WHATSAPP_WEBHOOK_VERIFY_TOKEN` shared only with Meta webhook configuration
- outbox and webhook-subscription retry intervals

When changing a secret, update the protected environment file, restart the backend, and verify only presence/length or a non-reversible fingerprint. Do not print the value in logs.

## WhatsApp Release Checks

1. Meta callback URL must be `https://api.pusulaiklimlendirme.com/api/public/whatsapp/webhook`.
2. The verify token in Meta must exactly match `WHATSAPP_WEBHOOK_VERIFY_TOKEN` on the VPS.
3. `GET` verification with a wrong token must return `403`; a valid Meta challenge must return the challenge as plain text.
4. Unsigned or incorrectly signed webhook `POST` requests must return `401`.
5. Confirm the Meta app is published; unpublished apps receive only dashboard test webhooks.
6. Confirm the tenant is entitled and explicitly allowed, Embedded Signup status is active, and webhook subscription status is healthy.
7. Confirm the customer has explicit WhatsApp consent before testing service-created or service-completed templates.
8. Check outbox status and retry/error fields rather than retrying manually; idempotency prevents duplicate event delivery.

## Desktop Release

1. Increment `frontend-desktop/src/main/resources/app-version.properties`.
2. Run desktop verification and build the MSI.
3. Upload the MSI to the configured public download path.
4. Update and verify `/api/public/desktop-version` only after the installer is reachable.
5. Test download, Windows administrator elevation, installation, relaunch, and version recognition from a second Windows device.

Current production desktop version at the time of this update: `3.8.13`.

## Web Release

The `frontend-web` project is deployed from its own project root. Production build is `npm run build`; output is `dist/`. After deployment, verify public routes, SPA rewrites, contact flow, privacy/terms pages, and `/whatsapp-connect` without exposing URL fragment contents in logs.

## Mobile Release

- Android releases require release signing and an AAB upload through Google Play Console. CI produces validation artifacts, not a store release.
- iOS/TestFlight distribution remains a manual Xcode Cloud/App Store Connect release action. The compile gate does not publish a build.
- Never use real tenant/customer data in App Store or Play Store screenshots.

## Post-Deploy Smoke Tests

- Authentication and one tenant-scoped read/write operation
- Service ticket create, assignment, completion, PDF, and notification routing
- Inventory mutation and idempotent part usage
- Current-account/debt transaction history and financial report totals
- Service-photo thumbnail and full download
- Super-admin operations dashboard, quota status, and diagnostic package
- Invalid Iyzico signature returns `401`
- WhatsApp verification/signature, consent gate, outbox delivery, and status callback
- Desktop version metadata and MSI URL

## Rollback

1. Disable the affected feature or tenant allow-list first when that safely stops new writes.
2. Restore the previous application artifact and restart the service.
3. Do not delete Flyway tables or run ad-hoc down migrations after production data has been written.
4. Restore a database backup only in a controlled maintenance window after evaluating data created since the backup.
5. Record the failed commit, migration state, logs, and corrective action before retrying.

## WhatsApp Coexistence preparation — 20 October 2026

This implementation is local preparation, not evidence of a successful production Embedded Signup.
Do not cancel, restart, offboard, or resubmit the existing Meta business verification.
Do not register/deregister a Coexistence phone number: Meta already registers it during this flow.

### Implementation and rollout order

1. Back up the database and apply Flyway V50 before deploying the backend. It adds optional fields and isolated sync/mirror tables; old clients and legacy integrations remain supported.
2. Deploy the backend before the connection page or updated desktop client. The completion request accepts the original four fields and an optional Coexistence flag. A phone ID may be omitted only for Coexistence; the backend resolves exactly one authorized, Cloud-API-enabled Business-app phone or fails closed.
3. Keep the configured Facebook Login for Business configuration ID and permissions unchanged. Embedded Signup v4 is selected through its product configuration; session logging payload version `3` is a different setting, not a request to downgrade signup.
4. Before a separately authorized production pilot, verify the Meta app-level subscriptions for `messages`, `account_update`, `history`, `smb_app_state_sync`, and `smb_message_echoes`. Local code cannot prove those dashboard subscriptions are active. Existing verification, access verification, permission review, app publication, sender allow-list and customer opt-in gates still apply.
5. Review the privacy notice/data-processing inventory for the optional WhatsApp contact/history mirror before enabling it in production. No privacy declaration or Meta account setting was changed by this local implementation.

### Safety and data handling

- Code and asset callbacks may arrive in either order. Cancellation invalidates late callbacks; duplicate results cannot repeat completion.
- The backend verifies both WABA membership and the actual `is_on_biz_app`/`platform_type` values. It never trusts the browser flag alone, guesses among multiple phones, or follows an arbitrary pagination URL.
- Completion is recorded against the opaque onboarding session and connection generation. A lost response can be recovered without exchanging the OAuth code a second time. The limited progress endpoint uses a JSON-body capability, not a token in an HTTP query string, and closes 24 hours after completion.
- Contacts/history requests are persisted before network dispatch, begin only after WABA webhook subscription, and have a 24-hour initiation window. Definitive HTTP 429 rejection may be retried with backoff. Timeout, worker crash or ambiguous acceptance becomes `UNKNOWN`; never reset that state or automatically repeat a one-shot request without checking the outcome.
- An accepted sync request is not completed history. History progress or Meta's explicit sharing-declined error determines readiness; a contact request has no documented final snapshot marker. Late/out-of-order callbacks cannot regress progress. Notifications wait while preparation is in progress rather than spending delivery attempts.
- A separate encrypted, tenant-scoped mirror stores contact names and a text-only message projection. It does not create customers, modify service/financial records, infer notification consent, fetch media URLs or send messages. Media without text appears as a type placeholder, not a downloaded attachment.
- `PARTNER_REMOVED` and `ACCOUNT_OFFBOARDED` invalidate the credential and purge that integration's mirror. An account-reconnected webhook does not restore old credentials; the owner must authorize a fresh connection.
- Mirror retention defaults to 30 days, clamped to 1–90 days via `whatsapp.coexistence.retention-days`. Cleanup runs daily via `whatsapp.coexistence.cleanup-cron`. The existing protected credential encryption key is reused; never put it, authorization codes, access tokens or raw webhook contents in reports.
- Desktop Settings → WhatsApp → Eşitlenen Kişiler ve Mesajlar is read-only, paginated and administrator-only. Both WABA/phone association and the authenticated company scope are enforced; the database also binds mirror records to their integration's company.

### Local verification

- Backend: `mvn test`; targeted suite: `mvn "-Dtest=WhatsApp*Test" test`.
- The V50 PostgreSQL integration test is opt-in through `PUSULA_COEXISTENCE_TEST_DB`, and refuses every target except a loopback `coexistence_test` database. It uses a disposable schema, no production data. Other PostgreSQL suites have their own independent environment switches.
- Web: `node --test scripts/*.test.mjs`, targeted ESLint for the two onboarding modules, and `npm run build`.
- The development-only `frontend-web/tests/coexistence-fixture.html` exercises the real React component with a local fake SDK/API. Scenarios: `finish-first`, `cancel`, `ambiguous`, `declined`, `pending`. The fixture blocks external fetches, is loopback/DEV-only and is not part of the production build.
- Desktop: `mvn test`. Existing service-network/support JavaFX UI suites require their explicit environment switches; a normal passing test command does not mean those opted-out suites ran.
- A genuine Meta pilot still needs a separately authorized demo WABA/phone and an unblocked onboarding flow. Local tests must not be reported as Meta approval, a production phone connection, or real message delivery.

Official contracts checked in the browser on 20 October 2026:
[Coexistence](https://developers.facebook.com/documentation/business-messaging/whatsapp/embedded-signup/onboarding-business-app-users/),
[Embedded Signup v4](https://developers.facebook.com/documentation/business-messaging/whatsapp/embedded-signup/version-4/).
