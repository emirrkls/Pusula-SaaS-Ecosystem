# App Store subscription lifecycle and account erasure

Deployed on 7 October 2026. **Both App Store Connect notification URLs are saved;
genuine signed sandbox V2 delivery is verified. Production API testing remains a first-release gate.**
Do not call the entire subscription lifecycle production-validated: a sandbox `TEST` is not a
purchase/renewal/refund test and does not prove production API authorization.

## Pre-deployment production baseline (read-only)

- Live backend release: `694e52e3c0ca230b7e5c84764814fc4294ca316c`; Flyway V43; service active.
- Bundle ID `com.pusula.service`, Apple app ID `6801256557`, Sandbox and Production enabled.
- Apple trust-root files readable; online certificate checks enabled. No secret values copied.
- No duplicate active Apple subscription bindings. Review demo account remains active.
- Local backend source matched this release before the changes below.

## V2 notification receiver

Both production and sandbox URL settings now use this HTTPS endpoint:

`https://api.pusulaiklimlendirme.com/api/public/apple/app-store-notifications`

The current App Store Connect URL dialogs did not expose a version selector. The receiver requires
V2, and Apple's genuine sandbox `TEST` was V2. Per Apple's documentation, `TEST` is always V2 even
if a URL is configured for V1: this test alone cannot establish the version of future lifecycle events.
Confirm ordinary signed sandbox lifecycle delivery before describing that configuration as validated.

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
- Before deployment, App Store Connect showed empty production/sandbox URL settings and
  version 1.0/build 53 waiting for review. After explicit operator approval, both URLs were saved.
  The review submission and selected build were not changed. No real account was deleted.

Production verification on 7 October 2026:

- Backend revision `58677f3cb579921124792bbc9dece701ab487ba6` is live; JAR SHA-256
  `da847ad52fdad2f8165d6a432bc4d9f3374cb8da697f4714653b217d5357152e` was checked before/after replacement.
- Verified pre-deployment backups are retained at
  `/root/backups/apple-lifecycle-58677f3-20261007T130633Z` (database, previous JAR and protected environment).
- Flyway V44/V45 succeeded, service/public HTTPS checks passed, protected tenant GET-only smoke
  checks passed, review demo account stayed active and no service errors appeared since restart.
  Environment secrets did not change; only the deployment revision did.
- CI backend/desktop/web and the dedicated PostgreSQL service-network workflow passed for the
  backend release. The web/privacy release `87372688c93e614417e2663cfc8f09fae03a5fe8` also passed
  backend/desktop/web CI and production Vercel deployment. `/privacy` shows the 7 October policy.
- The separately approved `Pusula App Store Server` In-App Purchase key is stored only at
  `/etc/pusula/apple-app-store/SubscriptionKey_C8XBH4JQDG.p8` (root, mode 600; parent mode 700).
  Its downloaded local copy was removed after byte-for-byte transfer verification. No private key,
  JWT or signed Apple payload was committed or printed; existing keys were not changed.
- Apple's sandbox TEST request/status both returned HTTP 200. `sendAttemptResult=SUCCESS`,
  notification version `2.0`, UUID `0345877d-a8ec-4f7a-bfd4-83a1c3be3fb4`. The receiver returned HTTP 204
  and stored the matching verified `TEST / Sandbox / IGNORED` event with no company binding.
  The official Apple Java SDK 5.2.0 independently confirmed `SUCCESS`. No plan changed.
- Production TEST requests returned HTTP 401 before a test token was issued, including a request
  using the official Apple Java SDK 5.2.0. Server NTP is synchronized; the same key/issuer/bundle
  works in sandbox. A fresh retry still returned 401. An Apple App Store Commerce Engineer
  [explains that production API access is unavailable before a production release](https://developer.apple.com/forums/thread/806452).
  This matches this app's first version still waiting for review and its functioning sandbox API;
  it is not evidence of an invalid signing key or a failed public receiver. Keep production delivery
  explicitly unverified until a post-release TEST succeeds. Do not rotate keys, weaken signature
  verification or publish/cancel review merely to make this pre-release diagnostic pass. If 401
  persists after production release, raise a support case with sanitized diagnostics.
- The focused Apple verification/lifecycle suite was rerun: 49 tests, zero failures/errors/skips.
  These are automated verifier/policy tests, not proof of a real device renewal, cancellation or refund.
- Read-only Apple sandbox notification history for the preceding 24 hours returned HTTP 200:
  one V2 TEST, one SUCCESS delivery, no ordinary lifecycle events. The backend likewise had one
  verified TEST and one previously processed device purchase. The only existing Apple-bound company
  predates V44; no test entitlement was rewritten directly in the database.
- The existing sandbox tester is in Türkiye and has monthly renewals every five minutes; no
  tester settings, purchase history, identity, production contract or app review state were changed.

1. Run `mvn --batch-mode --no-transfer-progress verify` and web lint/build/test checks.
2. Test V44/V45 and the ownership uniqueness rule on an isolated PostgreSQL database.
3. Follow RUNBOOK: verified database/artifact/env backups, exact revision build, atomic replacement,
   service/public API/version/Flyway checks, protected tenant smoke tests. Never reverse migrations blindly.
4. Deploy the new privacy text together with the account-deletion implementation, not before it.
5. Save BOTH notification URLs after operator approval and verify them; check the version selector
   if available. If not, verify ordinary V2 lifecycle delivery rather than inferring it from `TEST`.
6. Signed sandbox TEST/204/durable event verification is complete. Still test renewal, auto-renew off,
   grace/expiry, refund/reversal and restore with a disposable sandbox account; production TEST
   authorization remains unverified until production release (Apple's pre-release 401 restriction).
7. Test self-deletion only with a designated disposable account, never the review demo or real tenants.

The App Store Server API test request needs an **In-App Purchase API key**, its issuer ID and key ID.
An APNs or Sign in with Apple key is not a substitute. Request approval before creating persistent
credentials; do not log the private key or signed payload. A failed fake-JWS request verifies rejection,
not successful signed Apple delivery. Do not cancel the existing review submission to configure this.

Sources: [Apple Java library](https://github.com/apple/app-store-server-library-java),
[notification setup](https://developer.apple.com/help/app-store-connect/configure-in-app-purchase-settings/enter-server-urls-for-app-store-server-notifications),
[signed snapshot ordering](https://developer.apple.com/documentation/appstoreservernotifications/signeddate),
[account deletion](https://developer.apple.com/help/app-review/guideline-reference/5-1-1-account-deletion/).

## iOS responsiveness gate — 10 October 2026

The supplied build 53 report (`PusulaService-2026-10-10-122132.ips`) records a
FRONTBOARD `0x8BADF00D` watchdog, `process-exit` in the background, and failure to
terminate gracefully within five seconds. The main-thread sample contains ICU /
NSDateFormatter parsing during a SwiftUI body update. It is not a memory-access
exception, and a sampled date-parser frame alone does not establish the entire
root cause. The user reports termination after tapping the app icon and other
users report UI freezes. Foreground restoration must be tested as well as launch.
Do not commit the original device report or its private identifiers.

Changes prepared for the next build (not present in uploaded build 53):

- `Core/Tickets` provides bounded, strict ISO/Gregorian parsing, with Istanbul
  wall-time semantics for zone-less backend dates and explicit offset support.
- An independent `TicketListProcessor` actor builds parsed/searchable rows and
  both sort orders once per data revision. Query changes reuse that index;
  SwiftUI bodies and sort comparators do not parse dates or build formatters.
- Card schedule labels are prepared in the worker. Background/disappearing
  screens cancel projection; cancelled, old-query/data/tenant results are rejected.
  Date boundaries refresh on returning to the foreground. Cancellation is not an alert.
- A DEBUG-only offline fixture exercises the actual list with 10,000 synthetic
  tickets. It cannot enable production demo authentication or make ticket mutations;
  Release builds omit the fixture and the existing screenshot mode stays disabled.
- Foundation regressions test strict dates, offsets/fractions, date boundaries,
  filtering/sorting, Turkish search, linear parse counts, actor execution/index reuse,
  cancellation and stale results. Simulator regressions cover large-list search,
  foreground restoration and backgrounding during initial projection.

### Local verification — 10 October 2026

- Swift 6.0.3 on Linux, official `swift:6.0` image pinned to
  `sha256:fc2fe3b78e138702f0d69fcf1420372eb044194450c8eb6ba778ab1c87ff7b1b`:
  **39 tests passed, zero failures** (16 routing/session, 6 billing, 17 ticket/date).
  The temporary container had no network access and mounted app sources read-only;
  tests did not use the VPS, real customer data or production credentials.
- The 17 ticket/date tests passed separately in each of `UTC`, `Europe/Istanbul`
  and `America/Los_Angeles`. Turkish search lowercases before accent folding, so
  `İKLİMLENDİRME` matches `iklimlendirme` consistently across ICU implementations.
- The 10,000-record regression confirms exactly 30,000 date-parser calls for its
  three indexed fields and no additional calls across 40 subsequent queries.
  This is an algorithmic regression check, not a real-iPhone frame-time benchmark.
- Swift syntax parsing passed for application/UI-test sources with and without
  `DEBUG`. Linux cannot type-check SwiftUI/UIKit, run Xcode simulator tests, sign
  an archive or validate device performance. These remain explicit Mac gates.
- `git diff --check` passed for the iOS, workflow and lifecycle-document changes.

### Evening Mac handoff

1. Preserve dirty/old Mac working copies; fetch the repository and use a clean,
   fast-forwarded `main` only after these changes have been pushed. Do not use an
   old source tree/archive. Record the source SHA and clean/ahead-behind status.
2. First symbolicate the **old build 53** report with its original archive/dSYM,
   not the new build's symbols. The expected arm64 UUID is
   `72D4427E-E8EF-322B-BFDF-9F99BE471702`. Verify with `xcrun dwarfdump --uuid`.
   Known old archive: `~/Library/Developer/Xcode/Archives/2026-10-04/PusulaService-1.0-53-2bea0e7.xcarchive`.
   Load address: `0x1045dc000`. App frame addresses:
   `0x1048d5420 0x1048d269c 0x1048b35b0 0x1048b5764 0x1048b38f8 0x1048e2da0`.
   Use `xcrun atos -arch arm64 -o <matching-DWARF-file> -l 0x1045dc000 <addresses>`.
   If the UUID differs or dSYM is unavailable, report that limitation; do not invent symbols.
3. Run `swift test --package-path frontend-appstore`, then the ticket/date suite
   with `TZ=UTC`, `TZ=Europe/Istanbul`, and `TZ=America/Los_Angeles`.
4. Build the simulator Debug app/UI tests and run `StartupSmokeTests`,
   `AuthPresentationTests`, and `TicketListPerformanceTests`. Save xcresult files
   outside Git and inspect failures instead of loosening responsiveness checks.
5. Complete a clean Release simulator build, then choose the next unused App Store
   build number (54 only if still unused). Keep marketing version 1.0, bundle
   `com.pusula.service`, team `DDV3HCZRP6`, app ID `6801256557`, and account
   `emirrkeles@gmail.com`. Do not create/revoke certificates or change capabilities.
6. Archive fresh source, validate, export and upload using the existing approved
   account/signing workflow. Retain the new archive/dSYM and confirm Apple processing.
   Do not change the review submission, selected review build or publication status
   without separate approval. Xcode Cloud is not required.
7. Finally verify the new TestFlight build on a real device: cold launch, saved
   session/admin/technician loading, repeated foreground restoration, a populated
   ticket list, search/filter/scroll, and cold/warm push-to-ticket navigation.
   Passing a synthetic fixture is not proof that the reported device crash is resolved.

The Windows preparation does not include a new iOS archive or App Store upload.
The existing App Review submission is unchanged. This client-side change does not
require a backend deployment or database rewrite.
