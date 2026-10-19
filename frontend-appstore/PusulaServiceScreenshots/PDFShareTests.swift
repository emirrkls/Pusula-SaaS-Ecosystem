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
        // iOS replaces the activity's initial root view with its remote host
        // on later presentations. The captured Cloud hierarchy still exposes
        // that real system share window as ShareSheet.RemoteContainerView.
        app.descendants(matching: .any).matching(NSPredicate(format: "identifier IN %@",
            ["pdf.share.activity", "ShareSheet.RemoteContainerView"])).firstMatch
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
        // Xcode Cloud's iOS 27 activity uses a popover on both destinations.
        // Its captured accessibility hierarchy exposes _UIPopoverDimmingView
        // as Other, label "dismiss popup", not as a Close/Cancel button.
        let popoverDismiss = app.otherElements["dismiss popup"].firstMatch
        if popoverDismiss.exists {
            // The iPad AX dismiss region has no hittable point. Tap its actual
            // left edge, outside the trailing-button-anchored activity popover.
            // The disappearance / restored controls / retained PDF assertions
            // below still prove that only the real system activity was closed.
            let bounds = popoverDismiss.frame
            XCTAssertGreaterThan(bounds.width, 4, file: file, line: line)
            XCTAssertGreaterThan(bounds.height, 4, file: file, line: line)
            popoverDismiss.coordinate(withNormalizedOffset: CGVector(dx: 2 / bounds.width, dy: 0.5)).tap()
        } else {
            XCTAssertTrue(close.waitForExistence(timeout: 5), file: file, line: line)
            close.tap()
        }
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
