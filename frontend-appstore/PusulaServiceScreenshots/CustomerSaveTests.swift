import XCTest

final class CustomerSaveTests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    func testSlowCreateShowsSavingAndBlocksDuplicateActionsUntilSuccess() {
        let app = launch("slow-create")
        fillName(in: app)
        app.buttons["customers.save.submit"].tap()
        assertProcessing(in: app, title: "Kaydediliyor…")
        assertCompleted(in: app, id: "902", attempts: "1")
        XCTAssertEqual(app.staticTexts["customers.save.fixture.name"].label, "IOS-QA New Customer")
    }

    func testSlowUpdateShowsLocalizedSavingInDarkModeAndPreservesCustomerID() {
        let app = launch("slow-update", language: "en", dark: true)
        XCTAssertTrue(app.textFields["customers.save.name"].waitForExistence(timeout: 5))
        app.buttons["customers.save.submit"].tap()
        assertProcessing(in: app, title: "Saving…")
        assertCompleted(in: app, id: "901", attempts: "1")
    }

    func testFailureRemovesSavingRestoresInputAndRetainsIntentForManualRetry() {
        let app = launch("failure-then-success")
        fillName(in: app)
        app.buttons["customers.save.submit"].tap()
        assertProcessing(in: app, title: "Kaydediliyor…")
        let alert = app.alerts["Müşteri Kaydedilemedi"]
        XCTAssertTrue(alert.waitForExistence(timeout: 12))
        alert.buttons["Tamam"].tap()
        XCTAssertFalse(progress(in: app).exists)
        XCTAssertEqual(app.textFields["customers.save.name"].value as? String, "IOS-QA New Customer")
        XCTAssertTrue(app.textFields["customers.save.name"].isEnabled)
        XCTAssertTrue(app.buttons["customers.save.cancel"].isEnabled)
        XCTAssertTrue(app.buttons["customers.save.submit"].isEnabled)
        app.buttons["customers.save.submit"].tap()
        assertProcessing(in: app, title: "Kaydediliyor…")
        assertCompleted(in: app, id: "902", attempts: "2")
    }

    private func fillName(in app: XCUIApplication) {
        let name = app.textFields["customers.save.name"]
        XCTAssertTrue(name.waitForExistence(timeout: 5))
        name.tap()
        name.typeText("IOS-QA New Customer")
        let done = app.buttons["keyboard.done"]
        XCTAssertTrue(done.waitForExistence(timeout: 5))
        done.tap()
    }

    private func progress(in app: XCUIApplication) -> XCUIElement {
        app.descendants(matching: .any).matching(identifier: "customers.save.progress").firstMatch
    }

    private func assertProcessing(in app: XCUIApplication, title: String,
                                  file: StaticString = #filePath, line: UInt = #line) {
        let status = progress(in: app)
        XCTAssertTrue(status.waitForExistence(timeout: 3), file: file, line: line)
        XCTAssertTrue(status.isHittable, "Saving feedback must remain visible", file: file, line: line)
        XCTAssertTrue(status.label.contains(title), file: file, line: line)
        XCTAssertEqual(app.buttons["customers.save.submit"].label, title, file: file, line: line)
        XCTAssertFalse(app.buttons["customers.save.submit"].isEnabled, file: file, line: line)
        XCTAssertFalse(app.buttons["customers.save.cancel"].isEnabled, file: file, line: line)
        XCTAssertFalse(app.textFields["customers.save.name"].isEnabled, file: file, line: line)
    }

    private func assertCompleted(in app: XCUIApplication, id: String, attempts: String,
                                 file: StaticString = #filePath, line: UInt = #line) {
        let completed = app.staticTexts["customers.save.fixture.completed"]
        XCTAssertTrue(completed.waitForExistence(timeout: 12), file: file, line: line)
        XCTAssertEqual(completed.label, id, file: file, line: line)
        XCTAssertEqual(app.staticTexts["customers.save.fixture.attempts"].label, attempts, file: file, line: line)
        XCTAssertFalse(progress(in: app).exists, file: file, line: line)
        XCTAssertFalse(app.alerts.firstMatch.exists, file: file, line: line)
    }

    private func launch(_ scenario: String, language: String = "tr", dark: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--app-store-screenshots", "-AppleLanguages", "(\(language))",
                               "-AppleLocale", language == "tr" ? "tr_TR" : "en_US",
                               "-AppleInterfaceStyle", dark ? "Dark" : "Light"]
        app.launchEnvironment["PUSULA_CUSTOMER_SAVE_FIXTURE"] = scenario
        app.launch()
        return app
    }
}
