# Pusula Release Runbook

## Scope

This runbook covers Sprint 2-4 operational rollout items:
- subscription and quota operations
- super-admin operational tools
- observability dashboard

## Pre-Deploy Checklist

- Backend compile
  - `mvn -DskipTests compile`
- Targeted test suite
  - `mvn test`
  - At minimum verify subscription idempotency, quota enforcement, and super-admin validation tests
- Super admin panel build
  - `npm run build` in `Pusula-Super-Admin-Panel`
- DB migration check
  - Ensure latest SQL migration files are applied, including:
    - `V6__super_admin_global_tenant_support.sql`

## Required Environment Variables (Production)

- `DB_PASSWORD`
- `JWT_SECRET`
- `GOOGLE_WEB_CLIENT_ID`
- `GOOGLE_PLAY_PACKAGE_NAME`
- `GOOGLE_PLAY_API_ACCESS_TOKEN`
- `IYZICO_WEBHOOK_SECRET`
- `APP_DEPLOY_VERSION`

Recommended:
- `APP_DEPLOY_VERSION` should be unique per deployment (example: `2026.05.01-1`).

## Where These Variables Are Referenced

### Backend

- `backend/src/main/resources/application-vps.properties`
- `backend/src/main/resources/application.properties`

Look for:
- `spring.datasource.password=${DB_PASSWORD}`
- `jwt.secret=${JWT_SECRET}`
- `google.oauth.web-client-id=${GOOGLE_WEB_CLIENT_ID:}`
- `google.play.package-name=${GOOGLE_PLAY_PACKAGE_NAME:}`
- `google.play.api-access-token=${GOOGLE_PLAY_API_ACCESS_TOKEN:}`
- `iyzico.webhook.secret=${IYZICO_WEBHOOK_SECRET:}`
- `app.deploy.version=${APP_DEPLOY_VERSION:...}`

### Google Play In-App Purchase Related Flow

- Backend verification endpoint:
  - `POST /api/subscription/google-verify`
- Backend verification service:
  - `backend/src/main/java/com/pusula/backend/service/GooglePlayVerificationServiceImpl.java`
- Mobile purchase client flow (Android/iOS):
  - `frontend-playstore` purchase integration files
  - `frontend-appstore` StoreKit/in-app purchase integration files

## Smoke Test Plan (Post-Deploy)

- `GET /api/superadmin/operations-dashboard`
  - Check deploy version and alert list
- `GET /api/superadmin/companies/{id}/quota-status`
  - Confirm plan + usage visibility
- `POST /api/subscription/google-verify`
  - Verify success path and idempotent replay behavior
- `POST /api/payment/webhook/iyzico`
  - Invalid signature must return `401` with structured error body
- `GET /api/superadmin/support/diagnostic-package/{companyId}`
  - Validate auth failure and webhook diagnostics sections

## Notes

- Payment strategy is currently in-app purchase first. Existing Iyzico webhook/security implementation remains in codebase intentionally as optional/future-compatible path.
