// Structural release guards, not a substitute for Swift, StoreKit or device tests.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const root = resolve(__dirname, '..');
const read = path => readFileSync(resolve(root, path), 'utf8');
const app = 'frontend-appstore/PusulaService/';
const store = read(app + 'Services/StoreKitManager.swift');
const session = read(app + 'Core/SessionManager.swift');

test('large-list UI gates assert the projected count independently of localized number grouping', () => {
  const list = read(app + 'Views/Technician/TicketListView.swift');
  const ui = read('frontend-appstore/PusulaServiceScreenshots/TicketListPerformanceTests.swift');
  assert.match(list, /accessibilityIdentifier\("ticket\.summary\.count"\)/);
  assert.match(list, /accessibilityValue\(Text\(verbatim: String\(filteredTickets\.count\)\)\)/);
  assert.match(ui, /app\.staticTexts\["ticket\.summary\.count"\]/);
  assert.match(ui, /exists == true AND value == %@/);
  assert.equal((ui.match(/assertTicketCount\(10_000, in: app, timeout: 30\)/g) || []).length, 2);
  assert.match(ui, /assertTicketCount\(10_000, in: app, timeout: 10\)/);
  assert.match(ui, /assertTicketCount\(1, in: app, timeout: 10\)/);
  assert.match(ui, /search\.typeText\("9999"\)/);
  assert.match(ui, /for _ in 0\.\.<3/);
  assert.doesNotMatch(ui, /staticTexts\["10000 iş emri"\]/);
});

test('purchases use the server account UUID and pinned session token', () => {
  assert.match(store, /product\.purchase\(options: \[\.appAccountToken\(accountToken\)\]\)/);
  assert.match(store, /authTokenOverride: snapshot\.token/);
  assert.match(store, /PurchaseAccountPolicy\.canProcess/);
  assert.match(store, /transactionProcessing\[transaction\.id\]/);
  assert.doesNotMatch(store, /product\.purchase\(\)/);
  assert.match(store, /refreshSubscriptionContext\(expectedSession: snapshot\)/);
  assert.match(store, /refreshed \? "billing\.verified" : "billing\.verified_refresh_pending"/);
});

test('session recovery preserves credentials on temporary failures and provides a retry UI', () => {
  assert.match(session, /SessionRestorePolicy\.shouldDiscardCredentials/);
  assert.match(session, /restoreNeedsRetry = true/);
  assert.match(session, /fetchAuthProfile\(authToken: expectedToken\)/);
  assert.match(session, /refreshFeatureContext\(authToken: expectedToken\)/);
  const view = read(app + 'Views/Shared/ContentView.swift');
  assert.match(view, /session\.retrySessionRestore\(\)/);
  assert.match(view, /session\.restoreNeedsRetry/);
});

test('capture configure/start/stop are serialized away from the main queue', () => {
  const scanner = read(app + 'Views/Technician/BarcodeScannerView.swift');
  const controller = scanner.slice(scanner.indexOf('class CameraScannerController'), scanner.indexOf('private final class BarcodeCaptureDriver'));
  assert.doesNotMatch(controller, /\.stopRunning\(|\.startRunning\(|beginConfiguration\(/);
  assert.match(scanner, /sessionQueue\.async \{ \[self\] in[\s\S]*session\.stopRunning\(\)/);
  assert.match(scanner, /self\.isVisible/);
  assert.match(scanner, /dismantleUIViewController/);
});

test('privacy manifest covers first-party preferences and linked, non-tracking push device IDs', () => {
  const manifest = read(app + 'PrivacyInfo.xcprivacy');
  assert.match(manifest, /NSPrivacyAccessedAPICategoryUserDefaults/);
  assert.match(manifest, /CA92\.1/);
  assert.match(manifest, /NSPrivacyCollectedDataTypeDeviceID<\/string>[\s\S]*?NSPrivacyCollectedDataTypeLinked<\/key>\s*<true\/>[\s\S]*?NSPrivacyCollectedDataTypeTracking<\/key>\s*<false\/>/);
  const push = read(app + 'Services/PushNotificationManager.swift');
  assert.doesNotMatch(push, /UserDefaults\.standard\.(set|string)\(/);
  assert.match(push, /registerForRemoteNotifications\(\)/);
  assert.match(push, /authTokenOverride: snapshot\.token/);
});

test('new release messages have unique and complete Turkish/English resources', () => {
  const catalogs = ['tr', 'en'].map(lang => {
    const entries = [...read(app + `${lang}.lproj/Localizable.strings`).matchAll(/^"([^"]+)"\s*=\s*"((?:[^"\\]|\\.)*)";/gm)];
    const selected = entries.filter(e => /^(session|billing|account)\./.test(e[1]));
    assert.equal(new Set(selected.map(e => e[1])).size, selected.length, `${lang}: duplicate keys`);
    return new Map(selected.map(e => [e[1], e[2]]));
  });
  assert.deepEqual([...catalogs[0].keys()].sort(), [...catalogs[1].keys()].sort());
  for (const [key, value] of catalogs[0]) {
    const placeholders = text => [...text.matchAll(/%(?:\d+\$)?(?:ld|@|d|s)/g)].map(match => match[0]).sort();
    assert.deepEqual(placeholders(value), placeholders(catalogs[1].get(key)), `${key}: format arguments differ`);
  }
  const localizedSources = [store, session, read(app + 'Views/Admin/PlanUpgradeView.swift'),
    read(app + 'Views/Shared/ContentView.swift'), read(app + 'Views/Shared/ProfileView.swift')].join('\n');
  for (const [, key] of localizedSources.matchAll(/"((?:session|billing|account)\.[a-z_]+)"/g)) {
    for (const catalog of catalogs) assert.ok(catalog.has(key), `missing resource: ${key}`);
  }
  for (const catalog of catalogs) {
    for (const key of ['session.restore_retry_message', 'billing.account_mismatch', 'billing.verified_refresh_pending', 'account.delete_explanation']) {
      assert.ok(catalog.get(key)?.length > 15, key);
    }
  }
  assert.doesNotMatch(read(app + 'Views/Shared/ProfileView.swift'), /tüm verilerinizi kalıcı/);
});

test('server trusts Apple-signed ownership and refuses unclaimed legacy assignment', () => {
  const service = read('backend/src/main/java/com/pusula/backend/service/SubscriptionService.java');
  assert.match(service, /verification\.appAccountToken\(\)\.equals\(target\.getAppStoreAccountToken\(\)\)/);
  assert.match(service, /recordedOwner\.isEmpty\(\)/);
  const validator = read('backend/src/main/java/com/pusula/backend/service/AppleTransactionPayloadValidator.java');
  assert.match(validator, /payload\.getAppAccountToken\(\)/);
});
