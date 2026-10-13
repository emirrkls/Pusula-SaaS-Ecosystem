import XCTest

/// Exercises real detail sections with offline failures, never a real account.
final class TicketDetailLoadTests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    override func tearDownWithError() throws {
        if testRun?.hasSucceeded == false {
            let app = XCUIApplication()
            let hierarchy = XCTAttachment(string: app.debugDescription)
            hierarchy.name = "Offline ticket detail accessibility hierarchy"
            hierarchy.lifetime = .keepAlways
            add(hierarchy)
            print("OFFLINE TICKET HIERARCHY:\n\(app.debugDescription)")
        }
    }

    func testTLSFailureIsNotEmptyAndRetryLoadsOnlyFailedSection() {
        let app = launch("parts-tls")
        let retry = reveal("ticket.parts.retry", in: app)
        XCTAssertFalse(app.staticTexts["ticket.parts.empty"].exists)
        XCTAssertEqual(app.alerts.count, 0)
        retry.tap()
        XCTAssertTrue(app.staticTexts["Sentetik filtre"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["ticket.parts.retry"].exists)
        XCTAssertTrue(revealText("Sentetik teknisyen notu", in: app).exists)
        XCTAssertTrue(revealText("Sentetik işlem geçmişi", in: app).exists)
    }

    func testEveryFailedSectionHasIndependentRetryAndNoFalseEmpty() {
        let app = launch("all-fail")
        for section in ["parts", "notes", "timeline"] {
            let prefix = "ticket.\(section)"
            let retry = reveal("\(prefix).retry", in: app)
            XCTAssertFalse(app.staticTexts["\(prefix).empty"].exists)
            XCTAssertEqual(app.alerts.count, 0)
            retry.tap()
            XCTAssertTrue(app.staticTexts["\(prefix).empty"].waitForExistence(timeout: 5))
            XCTAssertFalse(app.buttons["\(prefix).retry"].exists)
        }
    }

    func testFailedLegacyPartsReadDoesNotInventZeroFinancialTotals() {
        let app = launch("legacy-tls")
        _ = reveal("ticket.parts.retry", in: app)
        for id in ["ticket.financial.parts", "ticket.financial.invoice"] {
            XCTAssertEqual(revealText(id, in: app).label, "Bilgi alınamadı")
        }
        reveal("ticket.parts.retry", in: app, scrollUp: false).tap()
        XCTAssertTrue(app.staticTexts["Sentetik filtre"].waitForExistence(timeout: 5))
        for id in ["ticket.financial.parts", "ticket.financial.invoice"] {
            let amount = revealText(id, in: app)
            XCTAssertNotEqual(amount.label, "Bilgi alınamadı")
            XCTAssertTrue(amount.label.contains("10"))
        }
    }

    func testSuccessfulEmptyResponsesShowEmptyWithoutError() {
        let app = launch("empty")
        for section in ["parts", "notes", "timeline"] {
            XCTAssertTrue(revealText("ticket.\(section).empty", in: app).exists)
            XCTAssertFalse(app.buttons["ticket.\(section).retry"].exists)
        }
        XCTAssertEqual(app.alerts.count, 0)
    }

    func testCancellationDoesNotAlertOrPretendThereAreNoParts() {
        let app = launch("cancel")
        let retry = reveal("ticket.parts.retry", in: app)
        XCTAssertEqual(app.alerts.count, 0)
        XCTAssertFalse(app.staticTexts["ticket.parts.empty"].exists)
        retry.tap()
        XCTAssertTrue(app.staticTexts["Sentetik filtre"].waitForExistence(timeout: 5))
    }

    private func launch(_ scenario: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--app-store-screenshots", "-AppleLanguages", "(tr)", "-AppleLocale", "tr_TR"]
        app.launchEnvironment["PUSULA_TICKET_DETAIL_LOAD_FIXTURE"] = "1"
        app.launchEnvironment["PUSULA_TICKET_DETAIL_LOAD_SCENARIO"] = scenario
        app.launch()
        XCTAssertTrue(app.navigationBars["İş Emri #1"].waitForExistence(timeout: 5))
        return app
    }

    private func reveal(_ identifier: String, in app: XCUIApplication, scrollUp: Bool = true,
                        file: StaticString = #filePath, line: UInt = #line) -> XCUIElement {
        let element = app.buttons[identifier]
        // SwiftUI may not expose offscreen descendants in its accessibility
        // snapshot. Discover them by scrolling before asserting existence.
        for _ in 0..<8 {
            if element.exists && element.isHittable { break }
            if scrollUp { app.swipeUp() } else { app.swipeDown() }
        }
        XCTAssertTrue(element.waitForExistence(timeout: 5), identifier, file: file, line: line)
        XCTAssertTrue(element.isHittable, identifier, file: file, line: line)
        return element
    }

    private func revealText(_ identifierOrLabel: String, in app: XCUIApplication) -> XCUIElement {
        let element = app.staticTexts[identifierOrLabel]
        for _ in 0..<8 {
            if element.exists && element.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(element.waitForExistence(timeout: 5), identifierOrLabel)
        return element
    }
}
