import XCTest

/// Uses the real root/login flow on a fresh simulator, not screenshot mode.
/// No credentials, customer data, or production writes are involved.
final class StartupSmokeTests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testColdLaunchAndForegroundStayOnLogin() {
        let app = XCUIApplication()
        for attempt in 1...3 {
            app.launch()
            let skipIntro = app.buttons["Atla"]
            if skipIntro.waitForExistence(timeout: 3) { skipIntro.tap() }
            XCTAssertTrue(
                app.staticTexts["auth.login.ready"].waitForExistence(timeout: 20),
                "Login did not become ready after cold launch \(attempt)"
            )
            XCTAssertTrue(app.staticTexts["auth.login.ready"].isHittable)
            XCTAssertEqual(app.state, .runningForeground)

            XCUIDevice.shared.press(.home)
            app.activate()
            XCTAssertTrue(
                app.staticTexts["auth.login.ready"].waitForExistence(timeout: 20),
                "Login did not remain usable after foreground activation \(attempt)"
            )
            XCTAssertTrue(app.staticTexts["auth.login.ready"].isHittable)
            XCTAssertEqual(app.state, .runningForeground)
            app.terminate()
        }
    }
}
