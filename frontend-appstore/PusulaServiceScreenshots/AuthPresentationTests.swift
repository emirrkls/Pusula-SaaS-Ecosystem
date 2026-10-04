import XCTest

/// Run on a clean simulator alongside StartupSmokeTests. Never submits credentials.
final class AuthPresentationTests: XCTestCase {
    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testSocialButtonsUseEqualGeometryInBothAppearances() {
        for appearance in ["light", "dark"] {
            let app = launchTurkishLogin(appearance: appearance)
            assertSocialButtonGeometry(app)
            app.terminate()
        }
    }

    func testRegistrationUsesTheSameSocialControlsAndEmailSeparator() {
        let app = launchTurkishLogin(appearance: "dark")
        let register = app.buttons["Hesap Oluştur"]
        if !register.isHittable { app.scrollViews.firstMatch.swipeUp() }
        XCTAssertTrue(register.waitForExistence(timeout: 5))
        register.tap()
        XCTAssertTrue(app.staticTexts["İşletmenizi oluşturun"].waitForExistence(timeout: 5))
        assertSocialButtonGeometry(app)
        XCTAssertTrue(app.staticTexts["veya e-posta ile"].exists)
        app.terminate()
    }

    func testTurkishSocialTitlesAreLocalized() {
        let app = launchTurkishLogin(appearance: "dark")
        let google = app.buttons["auth.google"].firstMatch
        let apple = app.buttons["auth.apple"].firstMatch
        XCTAssertTrue(google.waitForExistence(timeout: 5))
        XCTAssertTrue(apple.waitForExistence(timeout: 5))
        XCTAssertEqual(google.label, "Google ile devam et")
        XCTAssertTrue(apple.label.contains("Apple"))
        XCTAssertFalse(apple.label.contains("Continue with Apple"))
        app.terminate()
    }

    private func launchTurkishLogin(appearance: String) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = [
            "-AppleLanguages", "(tr)", "-AppleLocale", "tr_TR",
            "-pusula.appearance", appearance
        ]
        app.launch()
        XCTAssertTrue(app.staticTexts["auth.login.ready"].waitForExistence(timeout: 20))
        return app
    }

    private func assertSocialButtonGeometry(_ app: XCUIApplication) {
        let google = app.buttons["auth.google"].firstMatch
        let apple = app.buttons["auth.apple"].firstMatch
        XCTAssertTrue(google.waitForExistence(timeout: 5))
        XCTAssertTrue(apple.waitForExistence(timeout: 5))
        XCTAssertEqual(google.frame.width, apple.frame.width, accuracy: 1)
        XCTAssertEqual(google.frame.height, apple.frame.height, accuracy: 1)
        XCTAssertGreaterThanOrEqual(google.frame.height, 44)
        XCTAssertEqual(google.frame.minX, apple.frame.minX, accuracy: 1)
    }
}
