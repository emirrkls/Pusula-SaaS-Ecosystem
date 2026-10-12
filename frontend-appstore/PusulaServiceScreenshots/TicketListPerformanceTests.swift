import XCTest

/// Real list/actor with a DEBUG-only offline fixture, not a production login.
final class TicketListPerformanceTests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    func testLargeListSearchAndForegroundRemainResponsive() {
        let app = XCUIApplication()
        app.launchArguments = ["--app-store-screenshots", "-AppleLanguages", "(tr)", "-AppleLocale", "tr_TR"]
        app.launchEnvironment["PUSULA_TICKET_STRESS_COUNT"] = "10000"
        app.launch()
        assertTicketCount(10_000, in: app, timeout: 30)
        let search = app.textFields["ticket.search"]
        XCTAssertTrue(search.isHittable)
        search.tap()
        search.typeText("9999")
        assertTicketCount(1, in: app, timeout: 10)
        XCTAssertTrue(app.staticTexts["Örnek müşteri 9999"].exists)

        for _ in 0..<3 {
            XCUIDevice.shared.press(.home)
            app.activate()
            assertTicketCount(1, in: app, timeout: 10)
            XCTAssertTrue(search.isHittable)
            XCTAssertEqual(app.state, .runningForeground)
        }
        app.buttons["Aramayı temizle"].tap()
        assertTicketCount(10_000, in: app, timeout: 10)
    }

    func testBackgroundingDuringInitialProjectionRecovers() {
        let app = XCUIApplication()
        app.launchArguments = ["--app-store-screenshots", "-AppleLanguages", "(en)", "-AppleLocale", "en_US"]
        app.launchEnvironment["PUSULA_TICKET_STRESS_COUNT"] = "10000"
        app.launch()
        XCUIDevice.shared.press(.home)
        app.activate()
        assertTicketCount(10_000, in: app, timeout: 30)
        XCTAssertTrue(app.textFields["ticket.search"].isHittable)
        XCTAssertEqual(app.state, .runningForeground)
    }

    private func assertTicketCount(_ expected: Int, in app: XCUIApplication,
                                   timeout: TimeInterval, file: StaticString = #filePath,
                                   line: UInt = #line) {
        // SwiftUI localizes interpolated numbers in Text. Assert the actual
        // projected count, not punctuation/grouping in its display label.
        let summary = app.staticTexts["ticket.summary.count"]
        let countMatches = NSPredicate(format: "exists == true AND value == %@", String(expected))
        let ready = XCTNSPredicateExpectation(predicate: countMatches, object: summary)
        let result = XCTWaiter.wait(for: [ready], timeout: timeout)
        let observed = summary.exists
            ? "label=\(summary.label), value=\(String(describing: summary.value))"
            : "summary is missing"
        print("Ticket projection: expected=\(expected), \(observed)")
        if result != .completed {
            let hierarchy = XCTAttachment(string: app.debugDescription)
            hierarchy.name = "Synthetic ticket list accessibility hierarchy"
            hierarchy.lifetime = .keepAlways
            add(hierarchy)
            let screenshot = XCTAttachment(screenshot: app.screenshot())
            screenshot.name = "Synthetic ticket list failure"
            screenshot.lifetime = .keepAlways
            add(screenshot)
        }
        XCTAssertEqual(result, .completed,
                       "Expected \(expected) projected tickets within \(timeout)s; \(observed)",
                       file: file, line: line)
    }
}
