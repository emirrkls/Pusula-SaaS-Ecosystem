// Offline source guards, not a substitute for an iPhone/iPad Xcode UI run.
const { test } = require('node:test');
const assert = require('node:assert/strict');
const { readFileSync } = require('node:fs');
const { resolve } = require('node:path');
const root = resolve(__dirname, '..');
const read = path => readFileSync(resolve(root, path), 'utf8');
const app = 'frontend-appstore/PusulaService/';
const preview = read(app + 'Views/Shared/TicketFilters.swift');
const bridge = read(app + 'Services/PDFShareButton.swift');

test('all PDF preview consumers retain the common controlled share path', () => {
  assert.match(preview, /PDFShareButton\(url: item\.url, isSharing: \$isSharing\)/);
  assert.doesNotMatch(preview, /ShareLink/);
  assert.match(preview, /alert\("pdf.share.failure_title"/);
  assert.match(preview, /interactiveDismissDisabled\(isSharing\)/);
  for (const path of ['Technician/TicketDetailView', 'Admin/ProposalView', 'Admin/FinanceView', 'Admin/CompanyDebtsView', 'Admin/BusinessAssetsView']) {
    assert.match(read(app + 'Views/' + path + '.swift'), /PDFPreviewSheet\(item:/, path);
  }
});

test('activity uses the actual source button and its current modal presenter, never a global root', () => {
  assert.match(bridge, /UIActivityViewController\(activityItems: \[url\], applicationActivities: nil\)/);
  assert.match(bridge, /popover\.sourceView = source/);
  assert.match(bridge, /popover\.sourceRect = source\.bounds/);
  assert.match(bridge, /responder = current\.next/);
  assert.match(bridge, /parent\.viewIfLoaded\?\.window === window/);
  assert.doesNotMatch(bridge, /UIApplication\.shared|connectedScenes|keyWindow|rootViewController/);
  assert.doesNotMatch(bridge, /presenter\.dismiss|parent\.dismiss/);
});

test('file validation precedes presentation; errors are visible and cancellation is not an error', () => {
  assert.ok(bridge.indexOf('PDFShareFile.validate(url)') < bridge.indexOf('presenter.present(controller'));
  assert.match(bridge, /let failed = error != nil/);
  assert.match(bridge, /failed \? \.systemFailure : nil/);
  assert.match(bridge, /finish\(lease, failure: nil\)/);
  assert.match(bridge, /isAwaitingPresentation\(lease\)/);
  assert.match(bridge, /self\.onBusy\(false\)/);
  assert.match(bridge, /if let failure \{ self\.onFailure\(failure\) \}/);
});

test('repeated tap and stale completion are guarded; teardown cancels its watchdog and only its own activity', () => {
  assert.match(bridge, /let lease = state\.begin\(\)/);
  assert.match(bridge, /state\.beginFinish\(lease\)/);
  assert.match(bridge, /self\.state\.finish\(lease\)/);
  assert.match(bridge, /func invalidate\(\)/);
  const teardown = bridge.slice(bridge.indexOf('func invalidate()'));
  assert.match(teardown, /watchdog\?\.cancel\(\)/);
  assert.match(teardown, /activity\?\.dismiss\(animated: false\)/);
  assert.doesNotMatch(bridge, /NetworkManager|TicketService|Keychain|print\(|NSLog/);
});

test('every PDF failure has one Turkish and English message', () => {
  const keys = ['pdf.preview.close', 'pdf.share.action', 'pdf.share.accessibility', 'pdf.share.failure_title',
                'pdf.share.dismiss_error', 'pdf.share.file_unavailable', 'pdf.share.invalid_document',
                'pdf.share.presentation_unavailable', 'pdf.share.system_failure'];
  for (const locale of ['tr', 'en']) {
    const strings = read(app + locale + '.lproj/Localizable.strings');
    for (const key of keys) {
      const pattern = new RegExp('^"' + key.replaceAll('.', '\\.') + '"\\s*=\\s*"[^"\\n]+";', 'gm');
      assert.equal([...strings.matchAll(pattern)].length, 1, locale + ':' + key);
    }
  }
});

test('offline UI test sources cover nested PDF sharing, missing files and failure recovery', () => {
  const fixture = read(app + 'ScreenshotSupport/PDFShareFixture.swift');
  assert.ok(fixture.startsWith('#if DEBUG'));
  assert.match(fixture, /#if targetEnvironment\(simulator\)/);
  assert.match(fixture, /AppStoreScreenshotMode\.isEnabled/);
  assert.match(fixture, /PDFPreviewSheet\(item: item\)/);
  assert.match(fixture, /UIGraphicsPDFRenderer/);
  assert.doesNotMatch(fixture, /NetworkManager|TicketService|Keychain|configureSession/);
  assert.match(bridge, /#if DEBUG\s+if PDFShareFixture.forcePresentationFailure/);
  const ui = read('frontend-appstore/PusulaServiceScreenshots/PDFShareTests.swift');
  for (const name of ['testServiceFormShareCanCancelAndReopenWithoutClosingItsPDF',
    'testClosingAndReopeningPDFDoesNotLeaveTheShareButtonBusy',
    'testProposalAndFinancePDFsUseTheSameWorkingSharePresentation',
    'testMissingFileShowsAnErrorWithoutOpeningShareAndCanStillClose',
    'testUnavailablePresentationShowsAnErrorAndRetryIsNotPermanentlyDisabled']) {
    assert.ok(ui.includes('func ' + name + '()'), name);
  }
  assert.match(ui, /identifier != %@/);
  assert.match(ui, /"pdf.preview.close", "Close", "Cancel"/);
  assert.match(read('frontend-appstore/Package.swift'), /name: "PusulaSharingTests"/);
});
