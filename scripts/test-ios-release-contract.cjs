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

test('collection footer has its own layout area and payment helpers cannot tap through it', () => {
  const view = read(app + 'Views/Technician/CollectionView.swift');
  assert.match(view, /VStack\(spacing: 0\) \{\s+ScrollView/);
  assert.match(view, /accessibilityIdentifier\("collection\.form"\)/);
  assert.match(view, /private var submitFooter: some View/);
  assert.doesNotMatch(view, /safeAreaInset/);
  const ui = read('frontend-appstore/PusulaServiceScreenshots/CollectionPresentationTests.swift');
  assert.match(ui, /visible\.contains\(full\.frame\) && !save\.frame\.intersects\(full\.frame\)/);
  assert.match(ui, /collection\.input\.Tahsil edilen tutar.*value as\? String, "3000"/);
  assert.match(ui, /Filling an amount must not trigger collection/);
  assert.match(ui, /on: app\.navigationBars\["Tahsilat"\], timeout: 3/);
});

test('collection replay is an explicit failure state, never the presence of a frozen payload', () => {
  const state = read(app + 'Core/Billing/CollectionSubmissionState.swift');
  assert.match(state, /var showsRetry: Bool \{ phase == \.unconfirmed \}/);
  assert.match(state, /var canSubmit: Bool \{ phase == \.idle \|\| phase == \.unconfirmed \}/);
  assert.match(state, /guard canSubmit else \{ return nil \}/);
  assert.match(state, /if payload == nil \{ payload = makePayload\(\) \}/);
  assert.equal((state.match(/requestID = UUID\(\)/g) || []).length, 2, 'Only initial/corrected attempts get a key');
  assert.match(state, /guard phase == \.submitting else \{ return \}/);
  const view = read(app + 'Views/Technician/CollectionView.swift');
  assert.match(view, /if submission\.showsRetry/);
  assert.match(view, /submission\.isProcessing \? "Tahsilat kaydediliyor/);
  assert.match(view, /\.interactiveDismissDisabled\(submission\.isProcessing\)/);
  assert.match(view, /guard submission\.canSubmit else \{ return \}/);
  assert.doesNotMatch(view, /pendingRequest|400\.\.\.499/);
});

test('a confirmed collection hands off its DTO synchronously and cannot await a list read', () => {
  const view = read(app + 'Views/Technician/CollectionView.swift');
  assert.match(view, /let onComplete: @MainActor \(FieldTicketDTO\) -> Void/);
  assert.match(view, /let completed = try await submit\(request\)\s+submission\.succeed\(\)\s+guard isSubmissionSessionCurrent else \{ return \}\s+onComplete\(completed\)\s+dismiss\(\)/);
  assert.doesNotMatch(view, /await onComplete|loadTickets\(/);
  assert.match(view, /requestId: submission\.requestID, authToken: submissionSession\?\.token/);
  const service = read(app + 'Services/TicketService.swift');
  assert.match(service, /path: "\/api\/reliable-mutations\/tickets\/\\\(ticketId\)\/complete", body: request,\s+authTokenOverride: authToken, idempotencyKey: requestId/);
});

test('collection dismissal precedes parent-owned refresh and invalidates stale list snapshots', () => {
  const detail = read(app + 'Views/Technician/TicketDetailView.swift');
  assert.match(detail, /sheet\(isPresented: \$showCollection, onDismiss: finishCollectionPresentation\)/);
  assert.match(detail, /currentTicket = completed\s+completedCollection = completed/);
  assert.match(detail, /guard let completed = completedCollection else \{ return \}/);
  assert.match(detail, /if let onCollected \{\s+onCollected\(completed\)/);
  const list = read(app + 'Views/Technician/TicketListView.swift');
  assert.match(list, /\.task\(id: collectionRefresh\?\.id\)/);
  assert.match(list, /session\.isCurrent\(refresh\.session\), !Task\.isCancelled/);
  const start = list.indexOf('onCollected: { completed in');
  const handoff = list.indexOf('collectionRefresh = CollectionListRefresh', start);
  const callback = list.slice(start, list.indexOf('\n', handoff));
  assert.match(callback, /session\.isCurrent\(presentingSession\)/);
  assert.ok(callback.indexOf('loadRequest.invalidate()') < callback.indexOf('replaceTickets(updated)'));
  assert.ok(callback.indexOf('replaceTickets(updated)') < callback.indexOf('selectedTicket = nil'));
  assert.match(callback, /collectionRefresh = CollectionListRefresh\(session: presentingSession\)/);
  assert.doesNotMatch(callback, /await /);
});

test('audited forms expose keyboard Done once per destination and resign without wiping input', () => {
  const design = read(app + 'Core/PusulaDesignSystem.swift');
  const modifier = design.slice(design.indexOf('func pusulaKeyboardDismissal'));
  assert.match(modifier, /scrollDismissesKeyboard\(\.interactively\)/);
  assert.equal((modifier.match(/placement: \.keyboard/g) || []).length, 1);
  assert.match(modifier, /Button\("common.keyboard_done"\) \{ PusulaKeyboard\.dismiss\(\) \}/);
  assert.match(modifier, /UIApplication\.shared\.sendAction\(#selector\(UIResponder\.resignFirstResponder\)/);
  assert.doesNotMatch(modifier, /onTapGesture|simultaneousGesture|= ""|Task\s*\{/);
  const expected = {
    'Admin/AccountPartiesView': 1, 'Admin/BusinessAssetsView': 1, 'Admin/CatalogView': 2,
    'Admin/CompanyDebtsView': 3, 'Admin/CustomerView': 3, 'Admin/FinanceView': 2,
    'Admin/FixedExpensesView': 2, 'Admin/ProposalView': 2, 'Admin/ServiceNetworkView': 4,
    'Admin/SettingsView': 3, 'Auth/LoginView': 1, 'Auth/RegisterView': 1,
    'Shared/DesktopPasswordSetupView': 1, 'Technician/CollectionView': 1,
    'Technician/TicketDetailView': 2, 'Technician/TicketListView': 2,
    'Technician/InventoryPartPickerView': 2, 'Technician/BarcodeScannerView': 1,
    'Technician/RescheduleTicketSheet': 1, 'Technician/ServicePhotoView': 1
  };
  for (const [file, count] of Object.entries(expected)) {
    assert.equal((read(app + `Views/${file}.swift`).match(/\.pusulaKeyboardDismissal\(\)/g) || []).length, count, file);
  }
  assert.equal((read(app + 'Views/Shared/ContentView.swift').match(/PusulaKeyboard\.dismiss\(\)/g) || []).length, 2);
});

test('actual collection regressions cover slow submit, delayed failed refresh, cancel and rejection', () => {
  const ui = read('frontend-appstore/PusulaServiceScreenshots/CollectionPresentationTests.swift');
  for (const name of ['testSlowSubmitShowsProcessingNotRetryAndSavesOnlyOnce',
                     'testSlowFailedListRefreshDoesNotHoldCollectionOrDetailOpen',
                     'testCancellingCollectionKeepsTicketDetailOpen',
                     'testDefinitiveRejectionAllowsEditingAndDoesNotOfferUnconfirmedReplay']) {
    assert.ok(ui.includes(`func ${name}()`), name);
  }
  assert.match(ui, /on: refresh, timeout: 3/);
  assert.match(ui, /collection\.fixture\.attempts/);
  const fixture = read(app + 'ScreenshotSupport/CollectionFixture.swift');
  assert.ok(fixture.startsWith('#if DEBUG'));
  assert.match(fixture, /#if targetEnvironment\(simulator\)/);
  assert.match(fixture, /Task\.sleep\(nanoseconds: 6_000_000_000\)/);
  assert.match(fixture, /TicketDetailView\(ticket: CollectionFixture\.ticket, onCollected:/);
  assert.doesNotMatch(fixture, /NetworkManager\.|KeychainHelper\.|configureSession\(/);
  assert.ok(read('frontend-appstore/BillingTests/CollectionSubmissionStateTests.swift').includes('testConfirmedSuccessIsTerminalEvenIfRefreshFailsOrIsSlow'));
});

test('keyboard regression checks preserve numeric, phone, plain and multiline text with localized Done', () => {
  for (const [locale, value] of [['tr', 'Bitti'], ['en', 'Done']]) {
    const source = read(app + `${locale}.lproj/Localizable.strings`);
    const entries = [...source.matchAll(/^"common.keyboard_done"\s*=\s*"([^"]+)";/gm)];
    assert.equal(entries.length, 1);
    assert.equal(entries[0][1], value);
  }
  const ui = read('frontend-appstore/PusulaServiceScreenshots/CollectionPresentationTests.swift');
  for (const name of ['testCollectionDecimalKeyboardCanBeDismissedWithoutLosingAmount',
                     'testCustomerPhoneKeyboardDonePreservesPhoneAndCancelIsReachable',
                     'testExpenseDecimalKeyboardDonePreservesAmountAndTextStillAcceptsInput',
                     'testMultilineClosingNoteDonePreservesTextAndDoesNotSubmit']) {
    assert.ok(ui.includes(`func ${name}()`), name);
  }
  assert.match(ui, /matching\(identifier: "keyboard\.done"\)\.count, 1/);
  assert.match(ui, /exists == false.*on: app\.keyboards\.firstMatch/);
});

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
  assert.match(network, /ConnectionReliableTransport\.data\(for: request/);
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

test('diagnostic observer remains single-attempt and excludes raw secrets', () => {
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

test('automatic write replay requires the explicit server-enforced mutation contract', () => {
  const policy = read(app + 'Core/Diagnostics/ConnectionRetryPolicy.swift');
  assert.match(policy, /url\.scheme == "https"/);
  assert.match(policy, /url\.host == "api\.pusulaiklimlendirme\.com"/);
  assert.match(policy, /UUID\(uuidString: key\) != nil/);
  assert.match(policy, /let root = "\/api\/reliable-mutations"/);
  assert.match(policy, /for attempt in 0\.\.\.1/);
  assert.match(policy, /url\.code == -1200 && stream\.0 == 3 && stream\.1 == -9816/);
  assert.match(policy, /\[502, 503, 504\]/);
  assert.match(policy, /forHTTPHeaderField: "Retry-After"\) == nil/);
  assert.doesNotMatch(policy, /URLCredential|serverTrust|useCredential|allowsAnyHTTPSCertificate/);
  const controller = read('backend/src/main/java/com/pusula/backend/controller/ReliableMutationController.java');
  assert.match(controller, /@RequestMapping\("\/api\/reliable-mutations"\)/);
  assert.equal((controller.match(/@RequestHeader\("Idempotency-Key"\)/g) || []).length, 7);
  assert.match(controller, /@PostMapping\("\/tickets\/\{ticketId\}\/complete"\)/);
  assert.match(controller, /reliable\.execute\(key, "ticket\.complete"/);
  assert.match(controller, /@PostMapping\("\/tickets\/\{ticketId\}\/technician-notes"\)/);
  assert.match(controller, /reliable\.execute\(key, "ticket\.note\.create"/);
  for (const service of ['Services/TicketService.swift', 'Services/BusinessServices.swift']) {
    const source = read(app + service);
    assert.doesNotMatch(source, /\.post\("\/api\/(?:tickets|customers|inventory|finance\/expenses)"/);
    assert.match(source, /idempotencyKey: requestId/);
    assert.doesNotMatch(source, /requestId: UUID = UUID\(\)/);
  }
});

test('technician note retry keeps a frozen intent and the captured account token', () => {
  const state = read(app + 'Core/Tickets/TechnicianNoteSubmissionState.swift');
  assert.match(state, /guard !isSubmitting else \{ return nil \}/);
  assert.match(state, /intent\.ticketID == ticketID, intent\.sessionRevision == sessionRevision/);
  const service = read(app + 'Services/TicketService.swift');
  assert.match(service, /path: "\/api\/reliable-mutations\/tickets\/\\\(ticketId\)\/technician-notes"/);
  assert.match(service, /authTokenOverride: authToken, idempotencyKey: requestId/);
  const view = read(app + 'Views/Technician/TicketDetailView.swift');
  assert.match(view, /requestId: noteSubmission\.requestID, authToken: snapshot\.token/);
  assert.match(view, /if !technicianNotes\.contains\(where: \{ \$0\.id == saved\.id \}\)/);
  assert.match(view, /\.disabled\(!noteSubmission\.canEdit\)/);
  assert.match(view, /\.interactiveDismissDisabled\(isSavingTechnicianNote\)/);
  assert.match(view, /noteSubmission\.fail\(allowsCorrection: false\)/);
});

test('mutation forms keep their key and suppress concurrent submits, retries obey account changes', () => {
  for (const file of ['Admin/CustomerView', 'Admin/CatalogView', 'Admin/FinanceView', 'Technician/TicketListView']) {
    const source = read(app + `Views/${file}.swift`);
    assert.match(source, /@State private var submissionId = UUID\(\)/, file);
    assert.match(source, /requestId: submissionId/, file);
    assert.match(source, /guard !is(?:Saving|Creating) else \{ return \}/, file);
    assert.match(source, /\.interactiveDismissDisabled\(is(?:Saving|Creating)\)/, file);
  }
  const photo = read(app + 'Views/Technician/ServicePhotoView.swift');
  assert.match(photo, /requestId: id, authToken: owner.token/);
  assert.match(photo, /guard !isUploading, hasPendingPhotos/);
  assert.match(photo, /for id in pending/);
  assert.match(photo, /batch\?\.prepare\(id, payload: prepared\)/);
  assert.match(photo, /batch\?\.succeed\(id\)/);
  assert.match(photo, /photos\.contains\(where:/);
  const network = read(app + 'Services/NetworkManager.swift');
  const observer = network.slice(network.indexOf('private func observedData'), network.indexOf('private func apiErrorMessage'));
  assert.ok(observer.indexOf('let generation = credentialGeneration') < observer.indexOf('await ConnectionDiagnosticEnvironment.context()'));
  assert.equal((observer.match(/guard mayRetry\(generation: generation, authorization: authorization\)/g) || []).length, 3);
  assert.match(observer, /mayRetry: \{[\s\S]*return await self\.mayRetry/);
  assert.match(observer, /guard GuideReadOnlyGate.shared.allows/);
  assert.match(observer, /beforeSend: \{ try GuideReadOnlyGate.shared.validate/);
  assert.match(observer, /credentialGeneration == generation/);
});

test('diagnostics is local bounded protected storage with explicit user sharing and clearing', () => {
  const storage = read(app + 'Core/Diagnostics/ConnectionDiagnosticsStore.swift');
  assert.match(storage, /limit: Int = 100/);
  assert.match(storage, /retention: TimeInterval = 24 \* 60 \* 60/);
  assert.match(storage, /isExcludedFromBackup = true/);
  assert.match(storage, /completeFileProtectionUntilFirstUserAuthentication/);
  assert.match(storage, /guard generation == expected else \{ return \}/);
  assert.match(storage, /records\.append\(record\.redacted\(\)\)/);
  assert.match(storage, /read\(upToCount: 512 \* 1_024 \+ 1\)/);
  assert.doesNotMatch(storage, /attributesOfItem|creationDate|modificationDate|volumeAvailableCapacity/);
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
