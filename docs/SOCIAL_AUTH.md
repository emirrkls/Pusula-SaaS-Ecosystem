# Social sign-in and perpetual free plan

Implementation added in October 2026. Provider configuration and a real Xcode/device validation are release gates, not implied by passing server tests. Do not upload separate intermediate iOS builds for these changes.

Provider setup status (2026-10-03): the `Pusula iOS` Google OAuth client has been created for `com.pusula.service` / `DDV3HCZRP6`, and its public IDs are saved in `OAuth.xcconfig`. Google Branding has been saved with the application name, existing public home/privacy/terms URLs and `pusulaiklimlendirme.com` authorized domain. After separate operator approval, Google Audience was published and verified as `In production` / `External`; this removes the test-user audience restriction, not the requirement to validate the client/backend integration. Only basic identity/profile/email access is requested; no sensitive or restricted scopes were added. Sign in with Apple has been saved and verified by reopening the existing App ID (primary App ID enabled); the operator approved regeneration of its provisioning profiles for future builds. The separate `Pusula Sign In with Apple` key (`BV8VBFQ73Y`) has been registered for that App ID and downloaded once. Its local copy and downloaded backup have restricted Windows ACLs and are excluded from version control; its contents must never be logged. The private key and an independent stable token-encryption key are installed on the production server with root-only permissions. Existing APNs keys, certificates and uploaded builds were not changed. Publishing Google OAuth and deploying the backend did not upload a build or submit/publish the app to App Store. Mobile and desktop source changes remain pending for the combined client release.

## Backend deployment verification (2026-10-03)

- Deployed backend source: `50131946ced76a0d1169546b18d57b3f397dff36`. The live artifact checksum was verified after activation, and `pusula-backend` is active.
- Local backend verification: 302 tests, zero failures/errors, one conditional PostgreSQL test skipped. Both CI and Service Network Validation passed for the exact source commit; the latter exercised PostgreSQL integration.
- A protected custom-format database backup was verified, restored into an isolated temporary database, and tested with V43 before production migration. Restore used `--no-owner --no-acl --role=pusula_db`. Production Flyway successfully applied V43; paid-plan dates and explicit company restrictions matched the pre-deployment state.
- Public plans/version endpoints returned 200; protected ticket/initial-password endpoints rejected anonymous access with 401. Authenticated read-only checks against the prepared review tenant returned 200 for feature context and customers without creating a customer or account.
- Apple challenge returned a valid nonce/UUID. Apple accepted the server-signed client credentials and rejected a deliberately invalid authorization code as `invalid_grant`, not `invalid_client`. This is a credential-configuration check, not a real-device sign-in or revocation test.
- Server signing key: `/etc/pusula/apple-signin/AuthKey_BV8VBFQ73Y.p8` (root, mode 600; parent directory 700). The protected environment file holds the independent stable encryption key; never print either secret or change the encryption key during routine deployment.
- Retain the protected pre-deployment database, artifact and environment backups for rollback/recovery. A code rollback must not discard the newly installed encryption key or reverse the migrated database blindly.

## Account behaviour

- Individual password, Google and Apple registration create an isolated company with `CIRAK / ACTIVE`, no trial/end date and the existing free-plan quotas. Child-service creation uses the same policy.
- `CompanyAccessPolicy` is the common authentication, feature-gate and subscription access policy. A free account does not expire after 14 days; explicit read-only restrictions and `SUSPENDED` remain effective.
- V43 removes legacy free-plan dates without modifying paid-plan dates, quotas or manual suspension/read-only flags. Production migration requires the usual verified backup.
- Social login is available on the individual sign-in/register screens; corporate users retain organization-code/password login.
- Social identities are bound by verified `(provider, subject)`, not by a requested username. Returning identities do not need a repeated Apple full name or email. A name is collected only for creating the account and never overwrites an existing user's name or role.
- Initial binding to an existing username/email requires a single match and an authoritative verified provider email. Multiple-company matches, super administrators and a different existing subject for that provider are rejected. Legacy Google custom usernames are resolved only through the exact server registration audit and its recorded user/company; no company-email heuristic is used.
- New social accounts must set an initial local password for financial confirmations and desktop login. The JWT-authenticated `/initial-password` endpoint only accepts the current user's tenant and cannot overwrite an already enabled password. Apple private-relay usernames are shown to the user. Existing password accounts keep their credentials.

## Security and endpoints

| Endpoint | Access | Purpose |
|---|---|---|
| `POST /api/auth/google` | Public, rate-limited | Verify Google ID token and resolve/create account |
| `POST /api/auth/apple/challenge` | Public, rate-limited | Create server nonce with a five-minute lifetime |
| `POST /api/auth/apple` | Public, rate-limited | Verify Apple assertion, nonce, authorization-code exchange and stable identity |
| `POST /api/auth/initial-password` | JWT required | Set a new social account's local password once |
| `DELETE /api/auth/delete-account` | JWT required | Revoke Apple token, erase personal login/provider/push data and queue signature erasure; retain a non-personal business-history tombstone (new implementation pending deployment) |

Google uses the existing web/server audience. Apple's native client audience is `com.pusula.service`; issuer, signature, allowed algorithm, audience, subject, timestamps and nonce are verified. The Apple challenge is row-locked and consumed with the login transaction. The code-exchanged ID token must have the same subject and nonce. Refresh tokens are stored only as AES-256-GCM ciphertext, never in mobile storage, logs or repository files. On account deletion, revocation failure aborts deletion so retry remains possible. Apple's authorization code is short-lived and exchanged only on the server.

The public endpoints share a 20-request/minute budget per IP. `X-Real-IP` is trusted only from a loopback reverse proxy; configure that proxy to overwrite this header. Untrusted direct clients cannot select a rate-limit bucket through forwarded headers. Database conflicts are returned as safe retry responses without SQL, token or subject details.

## Google configuration

1. Keep the existing Android/web credentials in project `pusula-android-app`. Create a separate **iOS** OAuth client for bundle `com.pusula.service`, team `DDV3HCZRP6`; no client secret belongs in the app.
2. Set `GOOGLE_IOS_CLIENT_ID` and `GOOGLE_REVERSED_CLIENT_ID` in `frontend-appstore/Config/OAuth.xcconfig`. These are public IDs; the reversed ID is the URL scheme.
3. `GOOGLE_WEB_CLIENT_ID` is the existing server audience and must match the backend configuration. The iOS SDK sends this audience through `GIDServerClientID`.
4. GoogleSignIn-iOS 9.0.0 is pinned with Swift Package Manager. Resolve/lock packages on the Mac and preserve `Package.resolved` where supported by the project.
5. Verify Google consent audience/test-user restrictions before testing outside the developer account. Only basic profile/email identity is requested.

Official setup: [Google iOS integration](https://developers.google.com/identity/sign-in/ios/start-integrating), [Google backend verification](https://developers.google.com/identity/sign-in/web/backend-auth).

## Apple configuration

1. Enable **Sign in with Apple** for the existing App ID `com.pusula.service` in team `DDV3HCZRP6`. Do not modify the old `.ios` App ID or revoke existing certificates.
2. Configure a Sign in with Apple key for this primary App ID. Download its `.p8` once and preserve a protected backup outside Git. Do not reuse an APNs-only key without verifying its service permissions.
3. Set the following server environment values; the encryption key is independent of APNs/WhatsApp credentials:
   - `APPLE_SIGN_IN_CLIENT_ID=com.pusula.service`
   - `APPLE_SIGN_IN_TEAM_ID=DDV3HCZRP6`
   - `APPLE_SIGN_IN_KEY_ID=<registered key ID>`
   - `APPLE_SIGN_IN_KEY_PATH=<absolute protected server path>`
   - `SOCIAL_AUTH_TOKEN_ENCRYPTION_KEY=<Base64-encoded random 32-byte key>`
4. Keep that encryption key stable across deployment and restore; rotating it without re-encrypting stored refresh tokens breaks revocation. Restrict `.p8` and environment-file permissions to the service/operator.
5. The iOS entitlement includes `com.apple.developer.applesignin=Default`. Xcode's managed signing must regenerate a profile containing that capability for the combined archive. No certificate replacement is required solely for this capability.

Official reference: [Apple token revocation](https://developer.apple.com/documentation/signinwithapplerestapi/revoke-tokens).

## Combined-release gates

- [x] Provider settings saved with required operator approval; public iOS Google IDs configured locally (client source publication remains pending).
- [x] Apple capability and server-only signing/encryption keys configured; challenge endpoint responds successfully on production.
- [x] V43 tested and deployed after verified database backup; existing paid and restricted accounts unchanged.
- [ ] Server/desktop verification and Android compile/unit tests pass.
- [ ] On the Mac, sync the latest shared commit, resolve Google packages, run Release simulator build/tests and archive validation.
- [ ] Real device: Google and Apple first registration, cancellation, subsequent login, hide-my-email, local password/desktop login, tenant-conflict rejection and Apple account deletion/revocation.
- [ ] Confirm free account has no trial banner/expiry and paid-plan restrictions still work.
- [ ] Increment the build number above the latest App Store Connect build only when producing the single combined build. Build 51 does not contain these changes.
- [ ] Upload that combined archive; App Review submission is a separate deliberate step after store metadata/subscription checks.
