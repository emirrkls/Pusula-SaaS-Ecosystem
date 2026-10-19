import XCTest

final class PDFShareTests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    func testServiceFormShareCanCancelAndReopenWithoutClosingItsPDF() {
        let app = launch("service")
        openShare(in: app)
        closeShare(in: app)
        openShare(in: app)
        closeShare(in: app)
        XCTAssertTrue(app.buttons["pdf.preview.close"].isHittable)
    }

    func testClosingAndReopeningPDFDoesNotLeaveTheShareButtonBusy() {
        let app = launch("service")
        openShare(in: app)
        closeShare(in: app)
        app.buttons["pdf.preview.close"].tap()
        let reopen = app.buttons["pdf.fixture.preview.open"]
        XCTAssertTrue(reopen.waitForExistence(timeout: 5))
        reopen.tap()
        openShare(in: app)
        closeShare(in: app)
    }

    func testProposalAndFinancePDFsUseTheSameWorkingSharePresentation() {
        for scenario in ["proposal", "finance"] {
            let app = launch(scenario)
            openShare(in: app)
            closeShare(in: app)
            app.terminate()
        }
    }

    func testMissingFileShowsAnErrorWithoutOpeningShareAndCanStillClose() {
        let app = launch("missing-file")
        XCTAssertTrue(app.staticTexts["pdf.fixture.file_removed"].waitForExistence(timeout: 5))
        app.buttons["pdf.share.open"].tap()
        dismissError(in: app)
        XCTAssertFalse(activity(in: app).exists)
        XCTAssertTrue(app.buttons["pdf.share.open"].isEnabled)
        XCTAssertTrue(app.buttons["pdf.preview.close"].isEnabled)
        app.buttons["pdf.preview.close"].tap()
        XCTAssertTrue(app.buttons["pdf.fixture.preview.open"].waitForExistence(timeout: 5))
    }

    func testUnavailablePresentationShowsAnErrorAndRetryIsNotPermanentlyDisabled() {
        let app = launch("presentation-failure")
        for _ in 0..<2 {
            app.buttons["pdf.share.open"].tap()
            dismissError(in: app)
            XCTAssertFalse(activity(in: app).exists)
            XCTAssertTrue(app.buttons["pdf.share.open"].isEnabled)
        }
        XCTAssertTrue(app.buttons["pdf.preview.close"].isEnabled)
    }

    private func activity(in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: "pdf.share.activity").firstMatch
    }

    private func openShare(in app: XCUIApplication, file: StaticString = #filePath, line: UInt = #line) {
        let share = app.buttons["pdf.share.open"]
        XCTAssertTrue(share.waitForExistence(timeout: 5), file: file, line: line)
        XCTAssertTrue(share.isHittable && share.isEnabled, file: file, line: line)
        share.tap()
        XCTAssertTrue(activity(in: app).waitForExistence(timeout: 8), file: file, line: line)
        XCTAssertFalse(app.alerts.firstMatch.exists, file: file, line: line)
    }

    private func closeShare(in app: XCUIApplication, file: StaticString = #filePath, line: UInt = #line) {
        // Close only the system activity, never the PDF's own Close button.
        // These tests intentionally do not select any recipient or send a file.
        let close = app.buttons.matching(NSPredicate(format:
            "identifier != %@ AND (label == %@ OR label == %@)", "pdf.preview.close", "Close", "Cancel")).firstMatch
        XCTAssertTrue(close.waitForExistence(timeout: 5), file: file, line: line)
        close.tap()
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == false"), object: activity(in: app))
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 8), .completed, file: file, line: line)
        let share = app.buttons["pdf.share.open"]
        let enabled = XCTNSPredicateExpectation(predicate: NSPredicate(format: "exists == true AND enabled == true"), object: share)
        XCTAssertEqual(XCTWaiter.wait(for: [enabled], timeout: 5), .completed, file: file, line: line)
        XCTAssertTrue(app.buttons["pdf.preview.close"].isHittable, file: file, line: line)
    }

    private func dismissError(in app: XCUIApplication) {
        let alert = app.alerts["PDF Could Not Be Shared"]
        XCTAssertTrue(alert.waitForExistence(timeout: 6))
        alert.buttons["OK"].tap()
    }

    private func launch(_ scenario: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--app-store-screenshots", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launchEnvironment["PUSULA_PDF_SHARE_FIXTURE"] = scenario
        app.launch()
        app.buttons["pdf.fixture.detail.open"].tap()
        let open = app.buttons["pdf.fixture.preview.open"]
        XCTAssertTrue(open.waitForExistence(timeout: 5))
        open.tap()
        XCTAssertTrue(app.buttons["pdf.share.open"].waitForExistence(timeout: 5))
        return app
    }
}
