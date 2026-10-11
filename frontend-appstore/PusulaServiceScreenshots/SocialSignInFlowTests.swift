import XCTest

/// Exercises the actual social-button completion path with a DEBUG simulator
/// response. Native Apple/Google and server verification still need a device.
final class SocialSignInFlowTests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    func testAppleLegacyPasswordFlagDoesNotBlockSignIn() {
        for placement in ["signIn", "registration"] {
            let app = launchFixture(placement: placement)
            app.buttons["auth.apple"].tap()
            assertSignedInWithoutPasswordPrompt(app)
            XCTAssertTrue(app.buttons["account.desktop-password.open"].exists)
            app.terminate()
        }
    }

    func testGoogleLegacyPasswordFlagDoesNotBlockSignIn() {
        for placement in ["signIn", "registration"] {
            let app = launchFixture(placement: placement)
            app.buttons["auth.google"].tap()
            assertSignedInWithoutPasswordPrompt(app)
            app.terminate()
        }
    }

    func testExistingPasswordAccountDoesNotOfferInitialPasswordSetup() {
        let app = launchFixture(localPasswordEnabled: true)
        app.buttons["auth.apple"].tap()
        assertSignedInWithoutPasswordPrompt(app)
        XCTAssertFalse(app.buttons["account.desktop-password.open"].exists)
    }

    func testCancellingOptionalDesktopSetupKeepsMobileSignIn() {
        let app = launchFixture()
        app.buttons["auth.apple"].tap()
        assertSignedInWithoutPasswordPrompt(app)
        app.buttons["account.desktop-password.open"].tap()
        let cancel = app.buttons["account.desktop-password.cancel"]
        XCTAssertTrue(cancel.waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["account.desktop-password.save"].isEnabled)
        cancel.tap()
        assertSignedInWithoutPasswordPrompt(app)
        XCTAssertTrue(app.buttons["account.desktop-password.open"].isHittable)
    }

    func testProviderCancellationDoesNotAuthenticateOrRequirePassword() {
        let app = launchFixture(result: "cancel")
        app.buttons["auth.apple"].tap()
        let attempted = NSPredicate(format: "label == %@", "Attempts: 1")
        expectation(for: attempted, evaluatedWith: app.staticTexts["auth.fixture.attempts"])
        let apple = app.buttons["auth.apple"]
        let enabled = NSPredicate(format: "enabled == true")
        expectation(for: enabled, evaluatedWith: apple)
        waitForExpectations(timeout: 5)
        XCTAssertFalse(app.staticTexts["auth.fixture.signed-in"].exists)
        XCTAssertFalse(app.staticTexts["auth.fixture.error"].exists)
        XCTAssertFalse(app.buttons["account.desktop-password.cancel"].exists)
    }

    func testUserCanExplicitlySaveOptionalDesktopPassword() {
        let app = launchFixture()
        app.buttons["auth.apple"].tap()
        assertSignedInWithoutPasswordPrompt(app)
        app.buttons["account.desktop-password.open"].tap()
        let password = app.secureTextFields["Şifre"]
        XCTAssertTrue(password.waitForExistence(timeout: 5))
        password.tap()
        password.typeText("Optional123")
        let confirmation = app.secureTextFields["Şifre tekrar"]
        confirmation.tap()
        confirmation.typeText("Optional123")
        let save = app.buttons["account.desktop-password.save"]
        if !save.isHittable { app.scrollViews.containing(.button, identifier: "account.desktop-password.save").firstMatch.swipeUp() }
        XCTAssertTrue(save.isEnabled)
        XCTAssertTrue(save.isHittable)
        save.tap()
        assertSignedInWithoutPasswordPrompt(app)
        XCTAssertFalse(app.buttons["account.desktop-password.open"].exists)
    }

    func testProviderFailureDoesNotAuthenticateAndRemainsRetryable() {
        let app = launchFixture(result: "error")
        app.buttons["auth.apple"].tap()
        XCTAssertTrue(app.staticTexts["auth.fixture.error"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.staticTexts["auth.fixture.signed-in"].exists)
        XCTAssertTrue(app.buttons["auth.apple"].isEnabled)
    }

    private func launchFixture(localPasswordEnabled: Bool = false, result: String = "success", placement: String = "signIn") -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--app-store-screenshots", "-AppleLanguages", "(tr)", "-AppleLocale", "tr_TR"]
        app.launchEnvironment["PUSULA_SOCIAL_AUTH_FIXTURE"] = "1"
        app.launchEnvironment["PUSULA_LOCAL_PASSWORD_ENABLED"] = localPasswordEnabled ? "1" : "0"
        app.launchEnvironment["PUSULA_SOCIAL_AUTH_RESULT"] = result
        app.launchEnvironment["PUSULA_SOCIAL_AUTH_PLACEMENT"] = placement
        app.launch()
        XCTAssertTrue(app.buttons["auth.apple"].waitForExistence(timeout: 10))
        return app
    }

    private func assertSignedInWithoutPasswordPrompt(_ app: XCUIApplication) {
        XCTAssertTrue(app.staticTexts["auth.fixture.signed-in"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.buttons["account.desktop-password.cancel"].exists)
        XCTAssertFalse(app.secureTextFields.firstMatch.exists)
    }
}
