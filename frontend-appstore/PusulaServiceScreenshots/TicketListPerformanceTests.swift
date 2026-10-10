import XCTest

/// Real list/actor with a DEBUG-only offline fixture, not a production login.
final class TicketListPerformanceTests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    func testLargeListSearchAndForegroundRemainResponsive() {
        let app = XCUIApplication()
        app.launchArguments = ["--app-store-screenshots", "-AppleLanguages", "(tr)", "-AppleLocale", "tr_TR"]
        app.launchEnvironment["PUSULA_TICKET_STRESS_COUNT"] = "10000"
        app.launch()
        XCTAssertTrue(app.staticTexts["10000 iş emri"].waitForExistence(timeout: 30))
        let search = app.textFields["ticket.search"]
        XCTAssertTrue(search.isHittable)
        search.tap()
        search.typeText("9999")
        XCTAssertTrue(app.staticTexts["1 iş emri"].waitForExistence(timeout: 10))
        XCTAssertTrue(app.staticTexts["Örnek müşteri 9999"].exists)

        for _ in 0..<3 {
            XCUIDevice.shared.press(.home)
            app.activate()
            XCTAssertTrue(app.staticTexts["1 iş emri"].waitForExistence(timeout: 10))
            XCTAssertTrue(search.isHittable)
            XCTAssertEqual(app.state, .runningForeground)
        }
        app.buttons["Aramayı temizle"].tap()
        XCTAssertTrue(app.staticTexts["10000 iş emri"].waitForExistence(timeout: 10))
    }

    func testBackgroundingDuringInitialProjectionRecovers() {
        let app = XCUIApplication()
        app.launchArguments = ["--app-store-screenshots"]
        app.launchEnvironment["PUSULA_TICKET_STRESS_COUNT"] = "10000"
        app.launch()
        XCUIDevice.shared.press(.home)
        app.activate()
        XCTAssertTrue(app.staticTexts["10000 iş emri"].waitForExistence(timeout: 30))
        XCTAssertTrue(app.textFields["ticket.search"].isHittable)
        XCTAssertEqual(app.state, .runningForeground)
    }
}
