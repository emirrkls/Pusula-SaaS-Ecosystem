// Offline source guards; they do not replace Xcode simulator compilation/UI tests.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const root = resolve(__dirname, '..');
const read = path => readFileSync(resolve(root, path), 'utf8');
const app = 'frontend-appstore/PusulaService/';
const source = read(app + 'Views/Admin/CustomerView.swift');
const editor = source.slice(source.indexOf('struct CustomerEditorSheet:'), source.indexOf('struct CreateTicketFromCustomerSheet:'));

test('customer save feedback stays outside the scrollable form and represents actual saving', () => {
  assert.match(editor, /safeAreaInset\(edge: \.bottom\) \{\s+if isSaving \{ savingStatus \}/);
  assert.match(editor, /private var savingStatus: some View/);
  assert.match(editor, /ProgressView\(\)/);
  assert.match(editor, /Text\("customers\.save\.saving"\)/);
  assert.match(editor, /Text\("customers\.save\.wait_message"\)/);
  assert.match(editor, /accessibilityElement\(children: \.combine\)/);
  assert.match(editor, /accessibilityIdentifier\("customers\.save\.progress"\)/);
  assert.doesNotMatch(editor, /Task\.sleep|DispatchQueue|Thread\.sleep|retriesRemaining/);
});

test('saving disables edits, duplicate submit and dismissal without changing the replay contract', () => {
  assert.match(editor, /Form \{[\s\S]*?\}\s+\.disabled\(isSaving\)/);
  assert.match(editor, /interactiveDismissDisabled\(isSaving\)/);
  assert.match(editor, /guard !isSaving else \{ return \}/);
  assert.match(editor, /isSaving = true\s+defer \{ isSaving = false \}/);
  assert.match(editor, /Button\(isSaving \? String\(localized: "customers\.save\.saving"\)/);
  assert.match(editor, /CustomerService\.createCustomer\(dto, requestId: submissionId\)/);
  assert.match(editor, /CustomerService\.updateCustomer\(id: id, customer: dto\)/);
  assert.match(editor, /init\(customer: CustomerDTO\?, onCustomerSaved: \(\(CustomerDTO\) -> Void\)\? = nil,\s+onSaved: @escaping \(\) async -> Void\)/);
  assert.equal((editor.match(/submissionId = UUID\(\)/g) || []).length, 1);
  assert.match(editor, /onCustomerSaved\?\(savedCustomer\)\s+await onSaved\(\)\s+dismiss\(\)/);
  assert.match(editor, /catch \{\s+errorMessage = error\.localizedDescription/);
});

test('saving messages have exactly one Turkish and English translation without claiming success or retry', () => {
  for (const language of ['tr', 'en']) {
    const strings = read(app + language + '.lproj/Localizable.strings');
    for (const key of ['action', 'saving', 'wait_message']) {
      const pattern = new RegExp('^"customers\\.save\\.' + key + '"\\s*=\\s*"[^"\\n]+";', 'gm');
      assert.equal([...strings.matchAll(pattern)].length, 1, language + ':' + key);
    }
  }
  assert.match(read(app + 'tr.lproj/Localizable.strings'), /"customers.save.saving" = "Kaydediliyor…"/);
  assert.match(read(app + 'en.lproj/Localizable.strings'), /"customers.save.saving" = "Saving…"/);
});

test('slow-success and recoverable-failure harness is debug/simulator-only and never contacts a live API', () => {
  const fixture = read(app + 'ScreenshotSupport/CustomerSaveFixture.swift');
  assert.ok(fixture.startsWith('#if DEBUG'));
  assert.match(fixture, /#if targetEnvironment\(simulator\)/);
  assert.match(fixture, /AppStoreScreenshotMode\.isEnabled/);
  assert.match(fixture, /Task\.sleep\(nanoseconds: 6_000_000_000\)/);
  assert.match(fixture, /firstKey != key/);
  assert.match(fixture, /throw URLError\(\.timedOut\)/);
  assert.doesNotMatch(fixture, /NetworkManager\.|CustomerService\.|KeychainHelper\.|configureSession\(/);
  assert.match(editor, /#if DEBUG\s+var testSave:/);
  assert.match(editor, /#if DEBUG\s+if let testSave/);
  assert.match(read(app + 'ScreenshotSupport/AppStoreScreenshotView.swift'), /CustomerSaveFixture\.isEnabled \{\s+CustomerSaveHarness\(\)/);
});

test('UI regressions cover create, update, failure cleanup, duplicate actions, retained input and intent', () => {
  const ui = read('frontend-appstore/PusulaServiceScreenshots/CustomerSaveTests.swift');
  for (const name of ['testSlowCreateShowsSavingAndBlocksDuplicateActionsUntilSuccess',
    'testSlowUpdateShowsLocalizedSavingInDarkModeAndPreservesCustomerID',
    'testFailureRemovesSavingRestoresInputAndRetainsIntentForManualRetry']) {
    assert.ok(ui.includes('func ' + name + '()'), name);
  }
  for (const id of ['customers.save.progress', 'customers.save.submit', 'customers.save.cancel',
                    'customers.save.name', 'customers.save.fixture.completed', 'customers.save.fixture.attempts']) {
    assert.ok(ui.includes(id), id);
  }
  assert.match(ui, /XCTAssertFalse\(app\.buttons\["customers.save.submit"\]\.isEnabled/);
  assert.match(ui, /XCTAssertTrue\(app\.buttons\["customers.save.submit"\]\.isEnabled/);
  assert.match(ui, /id: "902", attempts: "2"/);
});
