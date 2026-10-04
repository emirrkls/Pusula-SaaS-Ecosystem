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
        for appearance in ["light", "dark"] {
            let app = launchTurkishLogin(appearance: appearance)
            let register = app.buttons["Hesap Oluştur"]
            if !register.isHittable { app.scrollViews.firstMatch.swipeUp() }
            XCTAssertTrue(register.waitForExistence(timeout: 5))
            XCTAssertTrue(register.isHittable)
            register.tap()
            XCTAssertTrue(app.staticTexts["İşletmenizi oluşturun"].waitForExistence(timeout: 5))
            // The login remains mounted behind the registration sheet and uses
            // the same identifiers. Query only the presented registration form.
            let registration = app.scrollViews.containing(.staticText, identifier: "İşletmenizi oluşturun").firstMatch
            XCTAssertTrue(registration.exists)
            keepScreenshot(app, name: "Registration-\(appearance)")
            assertSocialButtonGeometry(registration)
            let separator = registration.staticTexts["veya e-posta ile"]
            XCTAssertTrue(separator.exists)
            XCTAssertGreaterThan(separator.frame.minY, registration.buttons["auth.apple"].firstMatch.frame.maxY)
            app.terminate()
        }
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
        // A clean installation presents the real intro over the mounted login.
        // Dismiss it through the normal UI before checking controls underneath.
        let skipIntro = app.buttons["Atla"]
        if skipIntro.waitForExistence(timeout: 3) { skipIntro.tap() }
        XCTAssertTrue(app.staticTexts["auth.login.ready"].waitForExistence(timeout: 20))
        XCTAssertTrue(app.staticTexts["auth.login.ready"].isHittable)
        keepScreenshot(app, name: "Login-\(appearance)")
        return app
    }

    private func assertSocialButtonGeometry(_ container: XCUIElement) {
        let google = container.buttons["auth.google"].firstMatch
        let apple = container.buttons["auth.apple"].firstMatch
        XCTAssertTrue(google.waitForExistence(timeout: 5))
        XCTAssertTrue(apple.waitForExistence(timeout: 5))
        XCTAssertTrue(google.isHittable)
        XCTAssertTrue(apple.isHittable)
        XCTAssertEqual(google.frame.width, apple.frame.width, accuracy: 1)
        XCTAssertEqual(google.frame.height, apple.frame.height, accuracy: 1)
        XCTAssertGreaterThanOrEqual(google.frame.height, 44)
        XCTAssertEqual(google.frame.minX, apple.frame.minX, accuracy: 1)
    }

    private func keepScreenshot(_ app: XCUIApplication, name: String) {
        let attachment = XCTAttachment(screenshot: app.screenshot())
        attachment.name = name
        attachment.lifetime = .keepAlways
        add(attachment)
    }
}
