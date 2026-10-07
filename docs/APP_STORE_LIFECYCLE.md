# App Store subscription lifecycle and account erasure

Implementation prepared on 7 October 2026. **Deployment and App Store Connect URL setup are pending.**
Do not call the integration production-ready until signed Apple delivery is verified.

## Verified production baseline (read-only)

- Live backend release: `694e52e3c0ca230b7e5c84764814fc4294ca316c`; Flyway V43; service active.
- Bundle ID `com.pusula.service`, Apple app ID `6801256557`, Sandbox and Production enabled.
- Apple trust-root files readable; online certificate checks enabled. No secret values copied.
- No duplicate active Apple subscription bindings. Review demo account remains active.
- Local backend source matched this release before the changes below.

## V2 notification receiver

Both production and sandbox settings should use Version 2 and this HTTPS endpoint:

`https://api.pusulaiklimlendirme.com/api/public/apple/app-store-notifications`

The public POST accepts `{"signedPayload":"<Apple-signed JWS>"}`. The official Apple Java library
verifies the outer JWS, nested transaction and signed renewal data against configured trust roots,
bundle, app ID and environment. No shared URL secret or unsigned JSON grants entitlement.
The existing purchase endpoint still rejects expired/revoked transactions; notifications must accept
verified historical snapshots to process expiry and refunds.

- Notification UUID is unique and processing is transactional/idempotent.
- Only authenticated, existing subscription-to-company bindings can change plans.
- Unknown owners wait in a durable queue, retry fairly every five minutes and become `UNBOUND`
  after 30 days. Notifications never create companies or claim a subscription for an arbitrary ID.
- Purchase time and signed snapshot time prevent older events from undoing newer state. A refund
  for a different older transaction cannot revoke the current transaction. Environments stay separate.
- Renewal/upgrade uses the current transaction product. Scheduled downgrade and auto-renew disable
  do not remove an already-paid period. Only verified Apple billing grace extends entitlement.
- Expiry/revocation makes the paid account read-only, preserving business records and plan limits.
  Manual administrative restrictions remain in force. Other payment providers keep their existing rules.
- Raw transaction IDs, JWS payloads, passwords and tokens are not stored in notification history/logs.

V44 adds the event table, company ordering fields and an active Apple ownership uniqueness index.
Preflight duplicate ownership before applying it; migration is additive and has no business-data rewrite.

## Account deletion

`DELETE /api/auth/delete-account` is authenticated and locks the requested user within its company.
Apple refresh-token revocation must succeed before personal-data removal is reported as successful.
The operation removes provider bindings, push devices, personal authentication history and user-profile
audit snapshots; anonymizes business audit actors; replaces login/name/password fields and clears
the signature. A non-personal tombstone preserves historical foreign keys. Shared operational and
financial records are not bulk deleted. The last user's personal auto-registration defaults are cleared.

V45 adds a durable signature-file erasure queue. Its worker deletes only the explicitly recorded file
inside that user's signature directory, rejects path/symlink escapes and retries failures. No existing
users or historical files are bulk erased by either migration. Subscription cancellation remains an
independent Apple operation, as explained in the in-app confirmation and public privacy page.

The policy at `/privacy` now describes actual data categories, permissions, providers, storage,
deletion, backups, subscription cancellation and contact/rights. The route is prerendered so reviewers
can read it without executing JavaScript. This technical policy update is not a legal compliance opinion;
the operator must maintain the applicable retention schedule and backup-erasure/recovery procedures.

## Release verification

Local verification on 7 October 2026:

- Backend `verify`: 344 tests, zero failures/errors; one existing network migration test skipped
  because its dedicated `NETWORK_TEST_JDBC_URL` is not configured.
- Web lint, production build and all 61 web tests passed. `/privacy` was checked in a browser.
- V44/V45 executed successfully on an isolated local PostgreSQL 17 database with synthetic data;
  duplicate active Apple ownership was rejected, existing companies and both queues were preserved.
  The task-specific container and its temporary volume were removed after verification.
- App Store Connect still showed empty production/sandbox URL settings and version 1.0/build 53
  waiting for review. No settings, review state, real accounts or production data were changed.

1. Run `mvn --batch-mode --no-transfer-progress verify` and web lint/build/test checks.
2. Test V44/V45 and the ownership uniqueness rule on an isolated PostgreSQL database.
3. Follow RUNBOOK: verified database/artifact/env backups, exact revision build, atomic replacement,
   service/public API/version/Flyway checks, protected tenant smoke tests. Never reverse migrations blindly.
4. Deploy the new privacy text together with the account-deletion implementation, not before it.
5. After operator approval, save BOTH notification URLs as V2 in App Store Connect and reopen to verify.
6. Request a signed sandbox test notification, verify 204 and durable `TEST` event; then test renewal,
   auto-renew off, grace/expiry, refund/reversal and restore with a disposable sandbox account.
7. Test self-deletion only with a designated disposable account, never the review demo or real tenants.

The App Store Server API test request needs an **In-App Purchase API key**, its issuer ID and key ID.
An APNs or Sign in with Apple key is not a substitute. Request approval before creating persistent
credentials; do not log the private key or signed payload. A failed fake-JWS request verifies rejection,
not successful signed Apple delivery. Do not cancel the existing review submission to configure this.

Sources: [Apple Java library](https://github.com/apple/app-store-server-library-java),
[notification setup](https://developer.apple.com/help/app-store-connect/configure-in-app-purchase-settings/enter-server-urls-for-app-store-server-notifications),
[signed snapshot ordering](https://developer.apple.com/documentation/appstoreservernotifications/signeddate),
[account deletion](https://developer.apple.com/help/app-review/guideline-reference/5-1-1-account-deletion/).
