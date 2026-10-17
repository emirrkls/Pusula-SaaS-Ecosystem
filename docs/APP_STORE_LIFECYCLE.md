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
3. Run `node --test scripts/test-ios-auth-contract.cjs` and
   `swift test --package-path frontend-appstore`, then the ticket/date suite
   with `TZ=UTC`, `TZ=Europe/Istanbul`, and `TZ=America/Los_Angeles`.
4. Build the simulator Debug app/UI tests and run `StartupSmokeTests`,
   `AuthPresentationTests`, `SocialSignInFlowTests` (iPhone and iPad), and
   `TicketListPerformanceTests`. Save xcresult files
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

### Guideline 4 — combined-build addition (11 October 2026)

The reviewer of 1.0 (53), on iPad Air 11-inch (M3), reported that Sign in with
Apple requires creating another password. The shared iOS social flow now invokes
its successful-session callback immediately after verified Apple/Google login,
without a password sheet. Desktop password creation is a separate, optional
Account action, with Turkish/English translations and unchanged server-side
one-time, authenticated-user/tenant checks. Restored sessions read the same
capability metadata. No backend deploy is needed; the legacy response field is
retained for existing Android compatibility.

Run the new credential policy tests and `SocialSignInFlowTests` alongside the
crash/launch/list tests in the same source tree/archive. In addition to simulator
tests, verify real first/returning Apple sign-in (including Hide My Email),
optional desktop setup cancellation/save and unchanged access restrictions on
iPhone and iPad. No new archive, upload, selected-build change, reviewer reply
or resubmission is implied by these source changes. Choose the next unused
build number on the Mac only after inspecting App Store Connect.

### Purchase/session/privacy preflight (12 October 2026)

The later preflight is broader than the passwordless UI-only correction above.
Commit `75a510101913db63765768936b92d80495ba5224` adds stable company purchase
UUIDs (V46), signed-transaction ownership checks and legacy ownership-ledger
validation. It also pins asynchronous purchase/restore/deletion requests to the
captured session, deduplicates transaction verification, preserves credentials
on temporary restore failures, serializes camera capture off the UI thread and
aligns the privacy manifest/account-deletion/subscription-management wording.
Unlike the UI-only change, this release **requires V46 on the backend before the
new iOS build is distributed**.

Validation performed from clean backend snapshots (including the final
`bb9eb1cdfda390c24fe8ec535154043baafde858` migration correction):

- Maven verify: 353 tests discovered, 352 passed, zero failures/errors and one
  opt-in PostgreSQL network test skipped. The separate
  [Service Network Validation](https://github.com/emirrkls/Pusula-SaaS-Ecosystem/actions/runs/38307251106)
  passed on PostgreSQL.
- Both iOS source-contract suites: 14 passed. These do not replace Xcode or
  real-device validation.
- The first schema-only rehearsal did not retain production object owners and
  therefore missed a legacy DDL ownership mismatch. The first rollout failed
  when the non-superuser app role tried to index the postgres-owned
  `payment_events` table. PostgreSQL rolled back the entire V46 transaction;
  the release guard restored the previous application/environment and health
  returned HTTP 200. No failed Flyway entry or partial UUID column remained.
- Commit `bb9eb1c` permits the legacy table owner to prepare the index first;
  V46 retains that exact index. The corrected rehearsal preserved the real
  schema, owners and privileges, then ran the exact migration as the
  non-superuser application role. It passed on PostgreSQL 16.15; 1,001 synthetic
  UUIDs were unique, existing values remained stable and a duplicate was
  rejected. The disposable test database was removed. No application database
  privileges or table owners were changed.
- A custom-format database backup and previous application/environment were
  saved server-side; the dump was non-empty and `pg_restore --list` succeeded.
- The first Mac Swift-package check exposed an undeclared macOS concurrency
  deployment baseline. Commit `8bc5d73` declares macOS 12 for the host regression
  package only; the iOS app deployment target is unchanged.
- [iOS validation on `8bc5d73`](https://github.com/emirrkls/Pusula-SaaS-Ecosystem/actions/runs/38305404375):
  51 Swift tests passed; all 17 ticket/date tests passed in each of three time
  zones. Simulator application and UI-test compilation passed. The 3 auth
  presentation, 7 social sign-in and 1 startup UI tests passed. The 2 large-list
  UI tests failed to find the expected 10,000-ticket summary within 30 seconds;
  iPad social-flow tests were consequently skipped. Do not treat this workflow
  as green or claim real-device performance is fixed. Resolve these failures
  and rerun the iPhone/iPad gates before the next store build/submission.

Previously bound legacy subscriptions remain restorable for their recorded
company. Unclaimed transactions from tokenless older builds are rejected with
409 rather than attached to the current login; never instruct a user to pay
again or bypass ownership verification. The new signed UUID purchase flow
still needs a real TestFlight/Sandbox purchase after the new build is uploaded.

Production rollout of backend `bb9eb1cdfda390c24fe8ec535154043baafde858`
completed after the owner-aware rehearsal and a fresh verified database backup.
The deployed JAR's SHA-256 is
`f8ab30816221795d0630d45821e7936c68819ce59a213d87d268c4be28248fa0`.
V46 is successful, every company has a non-null unique purchase UUID, and there
are no failed Flyway migrations. The protected environment is byte-for-byte
unchanged except the deployment-version line; existing Apple/JWT/provider
credentials and table/role ownership were not changed.

Two existing tenants' authenticated feature contexts returned stable, distinct
UUIDs matching their database rows, and profile company IDs matched their
authenticated tenant. The App Review demo account remained active, with
authenticated feature-context and customer GET requests returning HTTP 200.
Public HTTPS version/plans returned 200, anonymous subscription context 401,
and an invalid signed Apple notification 422. No real purchase, business-data
mutation, account deletion or password change was used for these smoke checks.
The previous JAR/environment and custom-format database dump remain protected
on the VPS for recovery. No iOS archive/upload, TestFlight rollout or App Review
resubmission was performed. At this point, the large-list UI failures above
remained an explicit gate for the next mobile release; see the follow-up below.

### Large-list UI gate follow-up (13 October 2026)

Commit `ceeec0f1112642e3e9f46cbe065ed1302aa8206f` replaces the brittle literal
`10000 iş emri` lookup with a stable summary accessibility identifier and an
exact, locale-independent value taken from `filteredTickets.count`. The visible
localized text is unchanged. The runtime log confirms the cause: the summary
reads `10.000 iş emri` in Turkish and `10,000 iş emri` in US English, while its
actual count is 10000 in both cases. The old literal lookup therefore rejected
a successfully loaded list; these two failures were not evidence of a stalled
projection. The tests still require all 10,000 projected rows
within 30 seconds, search for ticket 9999, validate its customer, repeat three
background/foreground cycles, and restore all 10,000 rows within 10 seconds
after clearing search. The two scenarios explicitly use Turkish and US English
locales; no fixture size, timeout or responsiveness assertion was relaxed.

[Validation on `ceeec0f`](https://github.com/emirrkls/Pusula-SaaS-Ecosystem/actions/runs/38308625141)
passed all gates: 15 source guards, 51 Swift regressions, all 17 ticket/date
tests in each of three time zones, simulator compilation, 13 iPhone UI tests
and 7 iPad social-flow tests. Both previously failing list scenarios passed
(14.812 seconds for foreground recovery and 28.225 seconds for the complete
search/three-foreground-cycles scenario), with no failures or skipped UI tests.
The general repository CI on the same source also passed. The former large-list
UI blocker is resolved; the Mac archive and real-device release checks remain.

Failures now retain synthetic-screen accessibility/screenshot attachments and
the simulator xcresult/logs for seven days. No production session or customer
data is used by the list fixture. These simulator checks do not replace the
real-device crash, push, sign-in and Sandbox purchase checks before distribution.
No iOS archive, upload, App Review change or backend deployment is part of this
test correction.

### Intermittent connection failures: local reliability work (15 October 2026)

#### Technician-note follow-up and collector repair (17 October 2026)

- The supplied report is from **1.0 (55)**, not the newer client source. Ticket
  999's note POST failed at 12:58:54 UTC with -1200/stream 3/-9816; the later
  photo POST failed at 12:59:58 UTC with -1005. A read-only database inspection
  found no note and two earlier successfully uploaded photos. The ticket was
  subsequently completed successfully; failed attempts were not silently saved.
- The VPS reboot on 16 October removed the collector's `/run` directory. Its
  service's `ReadWritePaths` referred to that directory before Python could
  create it, causing systemd 226/NAMESPACE failures. There is **no matching
  packet evidence** for these 17 October incidents. Do not assign a carrier or
  hosting-provider root cause from this report alone.
- With owner approval, the live temporary collector now uses systemd
  `RuntimeDirectory` and mode 0750. A stop/start with the directory absent
  restored its socket; controlled build-55/build-57 HTTP probes were logged,
  and an external HTTPS probe matched both HTTP and 19 sanitized TCP records.
  Backend and Nginx master PIDs were unchanged; only a graceful Nginx logging
  reload was used. Cleanup ownership hashes were updated with the repaired
  unit. The existing **32 MiB** cap, metadata-only scope, build allowlist and
  expiry **18 October 18:19:03 Europe/Istanbul** remain unchanged. Old gap data
  cannot be recovered. No raw IPs, bodies or credentials were added to storage.
- The contract introduced in `b4bc03f` is
  `POST /api/reliable-mutations/tickets/{ticketId}/technician-notes`. Its receipt
  binds normalized content, important flag and ticket ID to the authenticated
  tenant/user/key. Note creation, audit and important-note notification join
  the existing receipt transaction. A replay rechecks tenant/assignment and
  reloads only the original note, including after the ticket was completed.
  Deleted/unavailable results, changed input/target, expired keys and revoked
  access cannot produce replacement writes. Existing legacy clients/routes
  are unchanged; no database migration is needed beyond existing V47.
- iOS uses the versioned path and the original session token. A selected
  transient failure may receive **one** automatic retry with the same key/body;
  trust/certificate failures, cancellation and HTTP 4xx remain non-retryable.
  An unconfirmed manual retry keeps the frozen note, important flag, ticket,
  session revision and key; editing is disabled until a confirmed result or
  definite rejection. Confirmed results are inserted into the visible list by
  ID, so a replay does not duplicate a row. This is in-memory, within-form
  recovery, **not** a durable outbox or cross-form/app-restart deduplication.
- The original implementation was local-only. The subsequent approved backend
  release is recorded in `outputs/note-release-20261017` in its worktree. A
  read-only follow-up confirmed the active service, deployed revision
  `b4bc03fd3b5a716de79c004d636d3764ffa423c9`, matching JAR SHA-256
  `1a0b921ba7688f6a8d1a9b223523bcb1573ae55b22e2b15bf5afc69175c0a796`, and
  public HTTPS 200. No repeat backend deployment or production note write was
  performed by the mobile-guide release check.
- The guide/support-entry branch includes this commit through merge `45a9fae`.
  Combined-source checks passed: 435 backend tests (11 opt-in tests skipped),
  20/20 reliable-mutation tests on an isolated loopback-only PostgreSQL 17.7
  database, 72 source-contract tests (one unrelated admin-checkout skip), and
  four offline diagnostic-runtime tests. A new iOS build from the combined
  source must still pass the required iPhone/iPad gates; guide build 64 uses
  the earlier `11fd695` source. Real-device note retry checks remain necessary.
  App Review build 54 is unchanged.

This work is **local only**, at the owner's request. No push, backend deployment,
archive/upload or change to the reviewed build 54 is authorized by this step.
The new backend contract must be deployed and verified before distributing an
iOS build that uses it. Existing builds continue using the legacy routes.

- New POST routes under `/api/reliable-mutations` cover customer, ticket,
  inventory and manual expense creation, plus ticket-photo upload. A UUID `Idempotency-Key` is
  mandatory. There is deliberately no fallback to a legacy POST: an older
  server must fail closed instead of silently ignoring the key.
- A unique tenant/user/key claim, the business mutation and its result ID commit
  in one transaction. Concurrent identical attempts wait and reload the same
  authorized resource. Reusing a key for different input/operations returns
  409. Existing feature, role, tenant, assignment, quota and read-only checks
  remain in force; the actual write and quota consumption run only once.
- V47 stores only actor/tenant IDs, UUID, operation, a SHA-256 fingerprint,
  result ID and creation time, not request/response content or files. The retry
  contract lasts 24 hours; minimal receipts remain for 30 days before pruning.
  Rolled-back photo writes remove only their own new original/thumbnail files.
- The client permits at most one automatic retry, with a short backoff, for
  reads or these explicit keyed writes. Eligible failures are selected transient
  network errors, the observed TLS -1200 / stream-domain 3 / -9816 combination,
  or HTTP 502/503/504 without `Retry-After`. Cancellation, offline state, generic
  TLS/certificate/trust failures, HTTP 4xx, other finance/auth writes and legacy
  POSTs are not automatically retried. TLS validation is unchanged.
- An automatic retry uses the exact encoded body/multipart boundary and mutation
  key, but a distinct diagnostic UUID. Credentials are checked before sending,
  before/after retry backoff and before returning results; stale-account work is
  cancelled. Forms block simultaneous saves and dismissal while saving, and
  retain the same key for manual retries within that form. A new form/app session
  is a **new intent**, not content-based duplicate detection. After an uncertain
  result, check the list before starting a fresh form; never promise cross-form
  deduplication.

Local validation covers the backend suite (including one pre-existing opt-in
external-database test skipped), actual PostgreSQL 17
concurrent/rollback receipt tests, V47 rerun safety, Swift policy regressions,
and source guards. Linux policy/type checks are not an iPhone/iPad Xcode build.
Before a later release, run the Mac simulator compile/UI gates and real-device
lost-response, cancellation, account-switch and photo retry checks. No live
customer mutation was used to test this local implementation.

The 14 October ticket-create failure still has no matching HTTP request on the
server; its later manual retry created one ticket, not a duplicate. TCP reset
metadata around that time is relevant but cannot identify the exact pre-HTTP
request or conclusively attribute the cause. Resilience is **not** evidence of
a root-cause fix. Client reports now retain fractional-second timestamps and
bounded numeric local-port/address-family hints, never raw IP addresses.

Separately authorized temporary VPS diagnostics were extended to **18 October
2026, 18:19:03 Europe/Istanbul** (15:19:03 UTC), with automatic cleanup and the
same 10 MiB cap. Only sanitized transport metadata is persisted; TCP
sequence/acknowledgment numbers improve reset/retransmission correlation without
recording payloads, packet-capture files or raw IPs. Recording can stop earlier
if the size cap is reached. HTTP correlation currently targets build 55 only;
review that allowlist before a future build's diagnostic session. The backend,
TLS trust/protocol configuration and Nginx master were not restarted/changed.

The owner also authorized manual expense creation on 15 October. The explicit
`POST /api/reliable-mutations/expenses` contract requires `FINANCE_MODULE`, an
administrator role and a valid key. Dedicated validated input excludes client
record/tenant IDs and managed fixed-expense/source links. Expense creation,
audit and closed-day reconciliation join the receipt transaction, so a rollback
reverts all of them and a replay does not repeat any of them. Updates, fixed
expense payments, debt collections and other financial writes remain outside
automatic replay. The iOS expense sheet keeps its intent UUID and disables
duplicate saves/dismissal while a write is pending. These additions are local
only; they do not change build 54, build 55 or the live backend.

Expense follow-up validation: 378 backend tests passed with one existing opt-in
database test skipped (379 discovered), all 100 Swift policy tests and 18 source
guards passed. The 14 receipt integration scenarios also passed independently on
an isolated PostgreSQL 17 instance, including eight concurrent expense copies,
one audit/reconciliation effect, full rollback and safe replay. The temporary
test database is disposable; no production expense was created for this check.
These host-side checks still do not replace iPhone/iPad Xcode compilation and
real-device validation before a later separately authorized rollout.

### GitHub signed release and upload (13 October 2026)

The owner authorized a manual GitHub-hosted Mac build and TestFlight upload using
existing access. The new main-only `iOS TestFlight (manual)` workflow verified the
existing API key, current app/team/capabilities, unused build number and passing
iPhone/iPad gate. After source guards and 51 Swift regressions, it created a fresh
Release archive, automatically signed/exported it, verified the actual IPA and
matching dSYMs, and uploaded **1.0 (54)** from
`fdf8dd5e243455e82529389394642f517f888470`.

[Release run 38314633688](https://github.com/emirrkls/Pusula-SaaS-Ecosystem/actions/runs/38314633688)
completed successfully. Apple's API confirmed processing **VALID**, and the
App Store Connect UI independently displayed **Build 54 — Ready to Submit**.
This resolves the Mac archive/upload gate without using Xcode Cloud. The archive
and symbols are retained only as an encrypted recovery artifact, with an
encrypted local copy. See [release identity, UUID, backup and operating guide](IOS_GITHUB_TESTFLIGHT.md).

App Review submission, publication, the store-selected build, tester groups,
backend and retired app were not changed. The passwordless Apple sign-in,
startup/foreground, push routing and Sandbox purchase/restore real-device checks
still apply before the separately authorized review resubmission.
