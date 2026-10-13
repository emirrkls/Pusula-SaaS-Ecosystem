import XCTest

/// Simulator-only transport errors exercise the actual production views and
/// their refresh actions without authenticating or contacting a live server.
final class CustomerRefreshTests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    func testCancelledInitialReadOffersRetryWithoutErrorAlertOrFalseEmpty() {
        let app = launch(scenario: "initial-cancel")
        waitForReads(1, in: app)
        let retry = app.buttons["customers.load.retry"]
        XCTAssertTrue(retry.waitForExistence(timeout: 10))
        XCTAssertFalse(app.alerts.firstMatch.exists)
        XCTAssertFalse(app.descendants(matching: .any).matching(identifier: "customers.empty").firstMatch.exists)
        retry.tap()
        XCTAssertTrue(app.buttons["customers.card.901"].waitForExistence(timeout: 10))
        XCTAssertFalse(app.alerts.firstMatch.exists)
        XCTAssertFalse(retry.exists)
    }

    func testRefreshCancellationAndTLSFailurePreserveExistingCustomers() {
        let app = launch(scenario: "refresh-cancel")
        let firstCustomer = app.buttons["customers.card.901"]
        XCTAssertTrue(firstCustomer.waitForExistence(timeout: 10))
        pullToRefresh(in: app)
        waitForReads(2, in: app)
        XCTAssertTrue(firstCustomer.exists)
        XCTAssertFalse(app.alerts.firstMatch.exists)
        XCTAssertFalse(app.descendants(matching: .any).matching(identifier: "customers.empty").firstMatch.exists)

        // The next refresh returns a genuine TLS error. It must remain visible
        // inline, rather than being suppressed along with cancellation errors.
        pullToRefresh(in: app)
        let retry = app.buttons["customers.load.retry"]
        XCTAssertTrue(retry.waitForExistence(timeout: 10))
        XCTAssertTrue(firstCustomer.exists)
        XCTAssertFalse(app.alerts.firstMatch.exists)
        retry.tap()
        waitForReads(4, in: app)
        XCTAssertTrue(firstCustomer.waitForExistence(timeout: 10))
        XCTAssertFalse(retry.exists)
    }

    func testHistoryFailureIsNotDisplayedAsEmptyAndCanBeRetried() {
        let app = launch(scenario: "history-failure")
        let customer = app.buttons["customers.card.901"]
        XCTAssertTrue(customer.waitForExistence(timeout: 10))
        customer.tap()
        let retry = app.buttons["customers.history.retry"]
        XCTAssertTrue(retry.waitForExistence(timeout: 10))
        XCTAssertFalse(app.alerts.firstMatch.exists)
        XCTAssertFalse(app.descendants(matching: .any).matching(identifier: "customers.history.empty").firstMatch.exists)
        retry.tap()
        XCTAssertTrue(app.buttons["customers.history.ticket.9901"].waitForExistence(timeout: 10))
        XCTAssertFalse(retry.exists)
        XCTAssertFalse(app.alerts.firstMatch.exists)
    }

    func testCancelledRetryKeepsPreviousFailureInsteadOfRestoringStaleEmptyState() {
        let app = launch(scenario: "empty-failure-cancel")
        waitForReads(1, in: app)
        let empty = app.descendants(matching: .any).matching(identifier: "customers.empty").firstMatch
        XCTAssertTrue(empty.exists)
        pullToRefresh(in: app)
        let retry = app.buttons["customers.load.retry"]
        XCTAssertTrue(retry.waitForExistence(timeout: 10))
        XCTAssertFalse(empty.exists)

        retry.tap()
        waitForReads(3, in: app)
        XCTAssertTrue(retry.exists)
        XCTAssertTrue(retry.isEnabled)
        XCTAssertFalse(empty.exists)
        XCTAssertFalse(app.alerts.firstMatch.exists)

        retry.tap()
        waitForReads(4, in: app)
        XCTAssertTrue(app.buttons["customers.card.901"].waitForExistence(timeout: 10))
        XCTAssertFalse(retry.exists)
    }

    private func launch(scenario: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--app-store-screenshots", "-AppleLanguages", "(tr)", "-AppleLocale", "tr_TR"]
        app.launchEnvironment["PUSULA_CUSTOMER_REFRESH_FIXTURE"] = scenario
        app.launch()
        return app
    }

    private func waitForReads(_ expected: Int, in app: XCUIApplication,
                              file: StaticString = #filePath, line: UInt = #line) {
        let counter = app.staticTexts["customers.fixture.reads"]
        let predicate = NSPredicate(format: "exists == true AND value == %@", "\(expected):0")
        let ready = XCTNSPredicateExpectation(predicate: predicate, object: counter)
        let result = XCTWaiter.wait(for: [ready], timeout: 10)
        let observed = counter.exists
            ? "label=\(counter.label), value=\(String(describing: counter.value))"
            : "counter missing"
        if result != .completed { attachFailureState(in: app, reason: "Expected read \(expected); \(observed)") }
        XCTAssertEqual(result, .completed,
                       "Expected completed customer read \(expected); \(observed)",
                       file: file, line: line)
    }

    private func pullToRefresh(in app: XCUIApplication,
                               file: StaticString = #filePath, line: UInt = #line) {
        // List's accessibility wrapper is not necessarily its scrollable view,
        // and a short default swipe may never cross UIRefreshControl's threshold.
        // Drag the actual UIKit-backed list slowly, even when it has no cells.
        let collection = app.collectionViews.firstMatch
        let table = app.tables.firstMatch
        let scrollable = collection.exists ? collection : table
        guard scrollable.exists else {
            attachFailureState(in: app, reason: "Customer list scroll container missing")
            XCTFail("Customer list scroll container missing", file: file, line: line)
            return
        }
        print("Customer refresh container: \(scrollable.elementType), frame=\(scrollable.frame)")
        let start = scrollable.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.18))
        let end = scrollable.coordinate(withNormalizedOffset: CGVector(dx: 0.5, dy: 0.85))
        start.press(forDuration: 0.05, thenDragTo: end,
                    withVelocity: .slow, thenHoldForDuration: 0.2)
    }

    private func attachFailureState(in app: XCUIApplication, reason: String) {
        print("Offline customer refresh failure: \(reason)")
        let hierarchy = XCTAttachment(string: app.debugDescription)
        hierarchy.name = "Synthetic customer refresh accessibility hierarchy"
        hierarchy.lifetime = .keepAlways
        add(hierarchy)
        let screenshot = XCTAttachment(screenshot: app.screenshot())
        screenshot.name = "Synthetic customer refresh failure"
        screenshot.lifetime = .keepAlways
        add(screenshot)
    }
}
