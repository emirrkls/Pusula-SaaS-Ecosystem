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

test('detail failures and cancellations cannot masquerade as empty data', () => {
  const state = read(app + 'Core/Tickets/TicketDetailSectionState.swift');
  const view = read(app + 'Views/Technician/TicketDetailView.swift');
  assert.match(state, /var showsEmpty: Bool \{ isLoaded && items\.isEmpty \}/);
  assert.match(state, /guard activeRequest == request else \{ return \}/);
  assert.match(state, /phase = previousPhase == \.idle \? \.cancelled : previousPhase/);
  for (const section of ['parts', 'notes', 'timeline']) {
    assert.match(view, new RegExp(`${section}State\\.showsEmpty`));
    assert.ok(view.includes(`id: "ticket.${section}"`));
  }
  const loader = view.slice(view.indexOf('private func loadSection'), view.indexOf('private func isDetailSessionCurrent'));
  assert.match(loader, /TicketDetailLoadFailure\.isCancellation\(error\)/);
  assert.match(loader, /isDetailSessionCurrent\(snapshot\)/);
  assert.doesNotMatch(loader, /errorMessage =/);
  assert.match(view, /partsState\.isLoaded \? totalPartsValue : nil/);
});

test('shared read gates ignore cancellation but reject stale and other-account results', () => {
  const policy = read(app + 'Core/Routing/RefreshRequestState.swift');
  assert.match(policy, /error is CancellationError/);
  assert.match(policy, /error\.domain == NSURLErrorDomain && error\.code == URLError\.cancelled\.rawValue/);
  assert.match(policy, /!isCancelled && activeRequest == request && requestSession == session/);
  const customer = read(app + 'Views/Admin/CustomerView.swift');
  assert.match(customer, /\.refreshable \{ await load\(\) \}/);
  assert.match(customer, /LoadCancellationPolicy\.isCancellation/);
  assert.match(customer, /\.task\(id: readSnapshot\)/);
  assert.doesNotMatch(customer, /if isLoading \{\s*ProgressView\(\)\s*\} else if filtered\.isEmpty/);
});

test('all audited read screens use cancellation guards without weakening transport security', () => {
  const files = [
    'Admin/CustomerView', 'Admin/ProposalView', 'Admin/CatalogView',
    'Admin/AdminNotificationsView', 'Admin/ServiceQualityView',
    'Admin/AccountPartiesView', 'Admin/BusinessAssetsView', 'Admin/FixedExpensesView',
    'Admin/CompanyDebtsView', 'Admin/FinanceView', 'Admin/FieldRadarView',
    'Admin/ServiceNetworkView', 'Admin/SettingsView', 'Admin/ProfitAnalysisView',
    'Admin/PlanUpgradeView', 'Technician/InventoryPartPickerView',
    'Technician/ServicePhotoView', 'Technician/TicketListView',
  ];
  for (const file of files) {
    assert.match(read(app + `Views/${file}.swift`), /LoadCancellationPolicy\.isCancellation/, file);
  }
  const network = read(app + 'Services/NetworkManager.swift');
  assert.match(network, /ConnectionDiagnosticsTransport\.data\(for: request/);
  const transport = read(app + 'Core/Diagnostics/ConnectionDiagnosticsTransport.swift');
  assert.match(transport, /session: URLSession = \.shared/);
  assert.match(transport, /session\.data\(for: request, delegate: observer\)/);
  assert.doesNotMatch(network + transport, /URLCredential\(trust:|serverTrust|useCredential|allowsAnyHTTPSCertificate/);
});

test('new read feedback is translated through unique matching Turkish and English keys', () => {
  const prefixes = /^(common\.|customers\.|ticket\.detail\.)/;
  const catalogs = ['tr', 'en'].map(locale => {
    const entries = [...read(app + `${locale}.lproj/Localizable.strings`).matchAll(/^"([^"]+)"\s*=\s*"((?:[^"\\]|\\.)*)";/gm)]
      .filter(match => prefixes.test(match[1]));
    assert.equal(entries.length, new Set(entries.map(match => match[1])).size, `${locale}: duplicate feedback key`);
    return new Map(entries.map(match => [match[1], match[2]]));
  });
  assert.deepEqual([...catalogs[0].keys()].sort(), [...catalogs[1].keys()].sort());
  for (const key of ['common.retry', 'customers.retry', 'ticket.detail.secure_connection_error',
                     'ticket.detail.parts.empty', 'ticket.detail.notes.completed_empty',
                     'ticket.detail.timeline.empty', 'ticket.detail.amount_unavailable']) {
    for (const catalog of catalogs) assert.ok(catalog.get(key)?.length > 0, key);
  }
});

test('failure fixtures are offline DEBUG simulator hooks and excluded from Release validation', () => {
  const router = read(app + 'ScreenshotSupport/AppStoreScreenshotView.swift');
  const debugStart = router.indexOf('#if DEBUG', router.indexOf('var body: some View'));
  const releaseStart = router.indexOf('#else', debugStart);
  for (const fixture of ['TicketDetailLoad', 'CustomerRefresh']) {
    const source = read(app + `ScreenshotSupport/${fixture}Fixture.swift`);
    assert.ok(source.startsWith('#if DEBUG'));
    assert.match(source, /#if targetEnvironment\(simulator\)/);
    assert.match(source, /AppStoreScreenshotMode\.isEnabled/);
    assert.doesNotMatch(source, /NetworkManager\.|KeychainHelper\.|\.configureSession\(/);
    assert.ok(router.slice(debugStart, releaseStart).includes(`${fixture}Fixture.isEnabled`));
    assert.ok(read('scripts/verify-ios-export.py').includes(
      fixture === 'TicketDetailLoad' ? 'PUSULA_TICKET_DETAIL_LOAD_FIXTURE' : 'PUSULA_CUSTOMER_REFRESH_FIXTURE'));
  }
});

test('iPhone and iPad CI both run the actual detail and customer failure regressions', () => {
  const workflow = read('.github/workflows/ios-validation.yml');
  for (const name of ['TicketDetailLoadTests', 'CustomerRefreshTests']) {
    assert.equal(workflow.split(`-only-testing:PusulaServiceScreenshots/${name}`).length - 1, 2, name);
  }
  assert.ok(read('frontend-appstore/TicketTests/TicketDetailSectionStateTests.swift').includes('testFailedInitialReadIsNotEmpty'));
  assert.ok(read('frontend-appstore/RoutingTests/RefreshRequestStateTests.swift').includes('LoadCancellationPolicy'));
});

test('diagnostics observes every common transport without automatic retries or raw secrets', () => {
  const network = read(app + 'Services/NetworkManager.swift');
  for (const operation of ['api', 'download', 'upload']) {
    assert.ok(network.includes(`observedData(for: request, operation: .${operation})`));
  }
  const model = read(app + 'Core/Diagnostics/ConnectionDiagnosticRecord.swift');
  assert.doesNotMatch(model, /localizedDescription|allHTTPHeaderFields|httpBody|UIDevice|identifierForVendor/);
  assert.match(model, /ConnectionErrorDomain\(rawValue: value\.domain\) \?\? \.other/);
  assert.match(model, /result\.count < 4/);
  const transport = read(app + 'Core/Diagnostics/ConnectionDiagnosticsTransport.swift');
  assert.equal((transport.match(/session\.data\(for:/g) || []).length, 1);
  assert.match(transport, /throw error \/\/ Preserve exact error\/cancellation/);
  assert.doesNotMatch(transport, /CFNETWORK_DIAGNOSTICS|URLCredential|didReceive.*challenge|while |for attempt/);
  const photos = read(app + 'Views/Technician/ServicePhotoView.swift');
  assert.match(photos, /local\(operation: \.photoLibraryRead/);
  assert.match(photos, /local\(operation: \.photoPreparation/);
});

test('diagnostics is local bounded protected storage with explicit user sharing and clearing', () => {
  const storage = read(app + 'Core/Diagnostics/ConnectionDiagnosticsStore.swift');
  assert.match(storage, /limit: Int = 100/);
  assert.match(storage, /retention: TimeInterval = 24 \* 60 \* 60/);
  assert.match(storage, /isExcludedFromBackup = true/);
  assert.match(storage, /completeFileProtectionUntilFirstUserAuthentication/);
  assert.match(storage, /guard generation == expected else \{ return \}/);
  assert.match(storage, /records\.append\(record\.redacted\(\)\)/);
  assert.doesNotMatch(storage, /URLSession|NetworkManager|AuthService|UserDefaults/);
  const view = read(app + 'Views/Shared/ConnectionDiagnosticsView.swift');
  assert.match(view, /ShareLink\(item: exportURL\)/);
  assert.match(view, /await store\.clear\(\)/);
  for (const file of ['Views/Auth/LoginView.swift', 'Views/Shared/ProfileView.swift', 'Views/Admin/SettingsView.swift']) {
    assert.match(read(app + file), /ConnectionDiagnosticsView\(\)/, file);
  }
});

test('diagnostics UI has matching translations and simulator-only offline coverage on both devices', () => {
  const catalogs = ['tr', 'en'].map(locale => {
    const entries = [...read(app + `${locale}.lproj/Localizable.strings`).matchAll(/^"(diagnostics\.[^"]+)"\s*=\s*"((?:[^"\\]|\\.)*)";/gm)];
    assert.equal(entries.length, new Set(entries.map(match => match[1])).size);
    return new Map(entries.map(match => [match[1], match[2]]));
  });
  assert.deepEqual([...catalogs[0].keys()].sort(), [...catalogs[1].keys()].sort());
  for (const [, key] of read(app + 'Views/Shared/ConnectionDiagnosticsView.swift').matchAll(/"(diagnostics\.[a-z_]+)"/g)) {
    for (const catalog of catalogs) assert.ok(catalog.has(key), key);
  }
  const fixture = read(app + 'ScreenshotSupport/ConnectionDiagnosticsFixture.swift');
  assert.ok(fixture.startsWith('#if DEBUG'));
  assert.match(fixture, /#if targetEnvironment\(simulator\)/);
  assert.match(fixture, /AppStoreScreenshotMode\.isEnabled/);
  assert.doesNotMatch(fixture, /NetworkManager|configureSession|KeychainHelper|URLSession/);
  assert.ok(read('scripts/verify-ios-export.py').includes('PUSULA_CONNECTION_DIAGNOSTICS_FIXTURE'));
  assert.equal(read('.github/workflows/ios-validation.yml').split('-only-testing:PusulaServiceScreenshots/ConnectionDiagnosticsPresentationTests').length - 1, 2);
});
