// Source-contract checks complement (not replace) Swift/Xcode/device tests.
// These can run on the Windows preparation host without credentials or an SDK.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync, existsSync } = require('node:fs');
const { resolve } = require('node:path');

const root = resolve(__dirname, '..');
const read = path => readFileSync(resolve(root, path), 'utf8');
const app = 'frontend-appstore/PusulaService/';
const social = read(app + 'Views/Auth/SocialAuthButtons.swift');
const profile = read(app + 'Views/Shared/ProfileView.swift');
const desktop = read(app + 'Views/Shared/DesktopPasswordSetupView.swift');
const session = read(app + 'Core/SessionManager.swift');

test('verified social sign-in has no local-password gate or modal', () => {
  assert.doesNotMatch(social, /requiresPasswordSetup|showPasswordSetup|pendingResponse|\.sheet\(|DesktopPasswordSetupView|logout\(/);
  assert.equal((social.match(/onSuccess\(response\)/g) || []).length, 1);
  assert.match(social, /manager\.signInWithApple\(\)/);
  assert.match(social, /manager\.signInWithGoogle\(\)/);
  assert.ok(!existsSync(resolve(root, app + 'Views/Auth/SocialPasswordSetupView.swift')));
});

test('desktop credential form is opened explicitly from the observed account view', () => {
  assert.match(profile, /@ObservedObject private var session/);
  assert.match(profile, /if session\.canSetDesktopPassword/);
  assert.match(profile, /Button\(action: \{ showDesktopPasswordSetup = true \}\)/);
  assert.match(profile, /savePassword: \{ password in try await session\.setDesktopPassword\(password\) \}/);
  assert.match(profile, /onCancel: \{ showDesktopPasswordSetup = false \}/);
});

test('optional desktop form never logs out and can be dismissed unless saving', () => {
  assert.doesNotMatch(desktop, /AuthService\.|SessionManager\.|logout\(/);
  assert.match(desktop, /try await savePassword\(password\)/);
  assert.match(desktop, /interactiveDismissDisabled\(isSaving\)/);
  assert.match(desktop, /password\.utf8\.count <= 72/);
  assert.match(desktop, /password == confirmation/);
});

test('optional credentials use the captured authenticated session and reject stale results', () => {
  const service = read(app + 'Services/AuthService.swift');
  assert.match(service, /setInitialSocialPassword\(_ password: String, authToken: String\)/);
  assert.match(service, /authTokenOverride: authToken/);
  assert.match(session, /setInitialSocialPassword\(password, authToken: requestToken\)/);
  assert.match(session, /revision == sessionRevision/);
  assert.match(session, /desktopLoginOptions = desktopLoginOptions\.afterSettingPassword/);
});

test('profile restore refreshes optional credentials and logout clears them', () => {
  assert.match(session, /self\.desktopLoginOptions = response\.desktopLoginOptions/);
  assert.match(session, /desktopLoginOptions = profile\.desktopLoginOptions/);
  assert.match(session, /desktopLoginOptions = \.unavailable/);
  const dto = read(app + 'Models/DTOs.swift').split('struct AuthProfileResponse: Codable {')[1].split('struct QuotaDTO')[0];
  assert.match(dto, /requiresPasswordSetup: Bool\?/);
  assert.match(dto, /loginUsername: String\?/);
});

test('new optional credential strings have complete, unique Turkish and English translations', () => {
  const keys = new Set((desktop + profile).match(/auth\.desktop_password\.[a-z_]+/g));
  assert.equal(keys.size, 10);
  for (const locale of ['tr', 'en']) {
    const translations = read(app + locale + '.lproj/Localizable.strings');
    for (const key of keys) {
      const matches = translations.match(new RegExp('"' + key.replaceAll('.', '\\.') + '"\\s*=\\s*"(?:[^"\\\\]|\\\\.)*";', 'g')) || [];
      assert.equal(matches.length, 1, `${locale}: ${key}`);
    }
  }
});

test('offline authentication fixture is DEBUG/simulator only and cannot authenticate a real session', () => {
  const fixture = read(app + 'ScreenshotSupport/SocialAuthFlowFixture.swift');
  assert.ok(fixture.startsWith('#if DEBUG'));
  assert.ok(fixture.trimEnd().endsWith('#endif'));
  assert.match(fixture, /#if targetEnvironment\(simulator\)/);
  assert.match(fixture, /AppStoreScreenshotMode\.isEnabled/);
  assert.doesNotMatch(fixture, /SessionManager\.shared|KeychainHelper\.|NetworkManager\.|AuthService\.|StoreKitManager\./);
  assert.match(social, /#if DEBUG[\s\S]*?var testAuthenticate:[\s\S]*?#endif/);
  assert.ok(social.indexOf('var testAuthenticate:') < social.indexOf('var onSuccess:'),
    'production completion must remain the last initializer closure');
  const mode = read(app + 'ScreenshotSupport/AppStoreScreenshotView.swift');
  assert.match(mode, /#if DEBUG\s+ProcessInfo[\s\S]*?#else\s+false\s+#endif/);
  assert.match(mode, /#if DEBUG\s+if SocialAuthFlowFixture\.isEnabled/);
});

test('CI includes new policy tests and iPhone/iPad social-flow regressions', () => {
  const manifest = read('frontend-appstore/Package.swift');
  assert.match(manifest, /\.testTarget\(name: "PusulaAuthTests"/);
  const workflow = read('.github/workflows/ios-validation.yml');
  assert.equal((workflow.match(/only-testing:PusulaServiceScreenshots\/SocialSignInFlowTests/g) || []).length, 2);
  assert.match(workflow, /d\["name"\]\.startswith\("iPad"\)/);
});
