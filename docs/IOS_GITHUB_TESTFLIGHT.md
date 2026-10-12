# Manual GitHub → TestFlight release

Target: **Pusula Servis Yönetimi**, Apple ID **6801256557**, bundle
**com.pusula.service**, team **DDV3HCZRP6**, marketing version **1.0**.
The retired `com.pusula.service.ios` app/profile must not be used or deleted by
this workflow. No App Review submission, store publication, tester-group change,
certificate revocation or backend deployment is performed.

## Credentials and gates

Existing repository secrets only:

- `APP_STORE_CONNECT_ISSUER_ID`
- `APP_STORE_CONNECT_KEY_ID`
- `APP_STORE_CONNECT_PRIVATE_KEY` (P-256 PKCS#8 PEM, escaped PEM, or base64 PEM)

The issuer must match the existing Apple team. API access is verified against the
actual current application, bundle capabilities, recent uploads and certificate
metadata. API checks are GET-only. Private-key values and JWTs are never logged.
Only a manual dispatch from `main` can run, and uploads are serialized.

The workflow verifies a successful **iOS Compile Gate** for an ancestor with
unchanged iOS source/contract tests/workflow. Source guards and Swift package
tests are rerun before an upload. If iOS source changed, first obtain a new green
iPhone/iPad validation run; never bypass that gate.

## Run

1. Open **Actions → iOS TestFlight (manual) → Run workflow**, branch `main`.
2. Choose `preflight` first. Enter an unused numeric build number, minimum `54`.
   The field deliberately has no reusable default; after build 54, start at 55.
   This mode verifies existing access only; it does not archive, upload, create
   signing assets or change an Apple record.
3. After preflight succeeds, run again with `upload` and the same still-unused
   number. Apple is checked again to prevent duplicate/reused build numbers.
4. The Release archive is built fresh using that build-number override. The
   repository's `CURRENT_PROJECT_VERSION` is not rewritten by CI; the manifest,
   actual IPA and Apple upload record contain the selected number and source SHA.
5. Xcode automatic provisioning may create the development signing assets needed
   by the ephemeral runner. It will not revoke existing certificates. App Store
   export uses Apple's automatic/cloud-managed distribution signing. If Apple
   denies provisioning or a certificate limit is reached, stop and diagnose; do
   not delete certificates or use the old app's profile.
6. Before upload, the actual IPA is checked for Apple Distribution signing,
   correct team/bundle/version/build, production APNs, Sign in with Apple,
   App Store provisioning, production API, the font and matching archive dSYMs.
7. An upload is not considered confirmed until Apple's API reports `VALID`.
   If the polling times out, inspect Apple before retrying; the upload may already
   have succeeded. No purchase-flow or real-device test is implied by upload.

This uses a GitHub-hosted Mac, not Xcode Cloud. GitHub runner/artifact policies
and quotas apply independently.

## Private recovery backup

This is a public repository. **Never upload plaintext IPA, archive, dSYMs,
provisioning profiles, keychains or signing keys as an Actions artifact.**
The recovery artifact contains only `backup.pusula.enc`: AES-256-GCM authenticated
encryption of the archive/dSYMs, exported IPA and diagnostics. Its key is derived
from the existing ASC private key with HKDF-SHA256, a fresh random salt, and a
release-specific context. The private key and CI keychain are not in the backup.
Ephemeral credentials and plaintext outputs are removed even on failure.

Artifacts expire after **90 days**. Download and preserve the encrypted backup
in private release storage before that date. Recovery requires the **original**
ASC private key used for that run; GitHub cannot reveal secret values again.
Preserve the key securely before any future secret replacement/deletion. Key
revocation does not decrypt or erase previously saved backups.

With a securely held copy of that original key, recover locally:

```sh
node scripts/ios-release.cjs decrypt backup.pusula.enc recovered.tar.gz /secure/path/AuthKey.p8
```

Recovery refuses to overwrite an existing destination and deletes partial output
if authentication fails. Do not publish the recovered files or commit them.

## After upload

Record the run URL, source SHA, version/build, Apple processing state and dSYM
UUIDs. Test on a real device: passwordless Apple/Google onboarding, startup and
foreground responsiveness, push → work order routing, purchase/restore and
account deletion. Select the new build and submit for review only as a separately
authorized store operation. Old build-53 crash reports still require build-53's
own matching dSYMs, not this new archive's symbols.

## Verified first upload — 13 October 2026 (Türkiye)

- [Read-only credential preflight](https://github.com/emirrkls/Pusula-SaaS-Ecosystem/actions/runs/38313750699): **passed** using the three existing repository secrets. Apple returned the correct app, current bundle record `SFR2AS2592` (`UNIVERSAL`), team prefix, required capabilities and highest uploaded build `53`.
- [Signed release/upload run](https://github.com/emirrkls/Pusula-SaaS-Ecosystem/actions/runs/38314633688): **success**.
- Fresh archive source: `fdf8dd5e243455e82529389394642f517f888470`, version **1.0 (54)**. Xcode **26.6 (17F113)** on the GitHub-hosted Mac; **Xcode Cloud was not used**.
- The run passed 10 release-tooling guards, 15 auth/release source guards and 51 Swift package tests. It verified the previous successful iPhone/iPad gate against unchanged iOS source before archiving.
- Archive, automatic provisioning, App Store cloud-managed export, actual-IPA checks and upload passed. Checks included Apple Distribution, team/bundle/build, production APNs, Sign in with Apple, production API, absence of DEBUG fixture hooks, the Google font, and matching dSYMs.
- Upload accepted at **01:11:17 Türkiye time**; Apple processing API returned **VALID** at **01:12:49**. App Store Connect independently showed **Build 54 — Ready to Submit**.
- Apple build ID: `f044fe34-86c2-4e39-b659-8a0ee47848d3`.
- App/dSYM UUID: `A6CCFC72-7770-32B7-948D-912BDC92DEC8`.
- Encrypted recovery artifact: `ios-encrypted-archive-54-38314633688`, artifact ID `11775829195`. SHA-256 of `backup.pusula.enc`: `6638d9a55da7ee9a675dcee4782a8422217c94abbb0cba3e9d15b38e09c774ac`. An encrypted copy was downloaded to the owner's ignored local outputs directory before handoff.

The earlier run `38313891613` archived/exported successfully but stopped before
upload because the verifier expected XML while codesign's default output is
human-readable DER diagnostics. The verifier now explicitly requests XML, parses
only a complete plist, and retains all entitlement assertions. A portable parser
regression was added after the successful upload. Do **not** use the earlier,
never-uploaded archive's symbols to diagnose the uploaded build 54.

No App Review submission/publication, store-selected-build change, tester-group
change, old-app deletion, new ASC API key or certificate revocation was requested.
Xcode automatic provisioning did create the development signing assets needed
by the new runner. Real-device checks and explicitly authorized review submission
remain separate. Future uploads must select a fresh number, starting at `55`,
and pass preflight again; certificate/provisioning limits are not bypassed.
