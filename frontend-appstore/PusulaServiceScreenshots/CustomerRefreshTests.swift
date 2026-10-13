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
        let list = app.descendants(matching: .any).matching(identifier: "customers.list").firstMatch
        XCTAssertTrue(list.exists)

        list.swipeDown()
        waitForReads(2, in: app)
        XCTAssertTrue(firstCustomer.exists)
        XCTAssertFalse(app.alerts.firstMatch.exists)
        XCTAssertFalse(app.descendants(matching: .any).matching(identifier: "customers.empty").firstMatch.exists)

        // The next refresh returns a genuine TLS error. It must remain visible
        // inline, rather than being suppressed along with cancellation errors.
        list.swipeDown()
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
        let list = app.descendants(matching: .any).matching(identifier: "customers.list").firstMatch
        list.swipeDown()
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
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 10), .completed,
                       file: file, line: line)
    }
}
