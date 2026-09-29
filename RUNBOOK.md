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

Current production desktop version at the time of this update: `3.8.12`.

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
