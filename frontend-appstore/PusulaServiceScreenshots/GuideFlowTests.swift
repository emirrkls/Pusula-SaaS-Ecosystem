import XCTest

final class GuideFlowTests: XCTestCase {
    private func launch(_ scenario: String = "hub", role: String = "COMPANY_ADMIN", reset: Bool = true, account: String = "fixture-user-a", large: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--app-store-screenshots"]
        if large { app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"] }
        app.launchEnvironment["PUSULA_GUIDE_TEST"] = "1"
        app.launchEnvironment["PUSULA_GUIDE_SCENARIO"] = scenario
        app.launchEnvironment["PUSULA_GUIDE_ROLE"] = role
        app.launchEnvironment["PUSULA_GUIDE_RESET"] = reset ? "1" : "0"
        app.launchEnvironment["PUSULA_GUIDE_ACCOUNT"] = account
        app.launch()
        return app
    }
    private func tap(_ id: String, _ app: XCUIApplication) {
        let button = app.buttons[id]
        XCTAssertTrue(button.waitForExistence(timeout: 12), id)
        for _ in 0..<8 where !button.isHittable { app.swipeUp() }
        XCTAssertTrue(button.isHittable, id)
        button.tap()
    }
    func testAdminActualAccountEntry() {
        let app = launch("admin-entry")
        let picker = app.segmentedControls["settings.sections"]
        XCTAssertTrue(picker.waitForExistence(timeout: 12))
        picker.buttons["Hesap"].tap()
        tap("settings.guide.open", app)
        XCTAssertTrue(app.buttons["guide.quick-start"].waitForExistence(timeout: 12))
        XCTAssertTrue(app.images["pusula.brand"].exists)
    }
    func testTechnicianActualProfileEntryAndRoleFilter() {
        let app = launch("technician-entry", role: "TECHNICIAN")
        tap("account.guide.open", app)
        XCTAssertTrue(app.buttons["guide.quick-start"].waitForExistence(timeout: 12))
        let search = app.searchFields.firstMatch
        search.tap(); search.typeText("firma ayar")
        XCTAssertFalse(app.buttons["guide.topic.company-settings"].exists)
    }
    func testLessonNextBackResumeAndCompletion() {
        let app = launch()
        tap("guide.quick-start", app)
        XCTAssertEqual(app.staticTexts["guide.step.progress"].label, "1 / 6")
        tap("guide.next", app)
        XCTAssertEqual(app.staticTexts["guide.step.progress"].label, "2 / 6")
        tap("guide.back", app)
        XCTAssertEqual(app.staticTexts["guide.step.progress"].label, "1 / 6")
        tap("guide.next", app); tap("guide.close", app)
        app.terminate()
        let resumed = launch(reset: false)
        tap("guide.quick-start", resumed)
        XCTAssertEqual(resumed.staticTexts["guide.step.progress"].label, "2 / 6")
        for _ in 0..<5 { tap("guide.next", resumed) }
        XCTAssertTrue(resumed.buttons["guide.quick-start"].waitForExistence(timeout: 10))
        XCTAssertTrue(resumed.staticTexts["guide.progress"].label.hasPrefix("1 /"))
        resumed.terminate()
        let other = launch(reset: false, account: "fixture-user-b")
        XCTAssertTrue(other.staticTexts["guide.progress"].waitForExistence(timeout: 10))
        XCTAssertTrue(other.staticTexts["guide.progress"].label.hasPrefix("0 /"))
    }
    func testSearchAndLargeTextFooter() {
        let app = launch(large: true)
        let search = app.searchFields.firstMatch
        XCTAssertTrue(search.waitForExistence(timeout: 12))
        search.tap(); search.typeText("iskonto")
        tap("guide.topic.discount", app)
        tap("guide.next", app)
        XCTAssertTrue(app.staticTexts["guide.step.progress"].label.hasPrefix("2 /"))
    }
    func testIntroContinueAndFinishAtLargeText() {
        let app = launch("intro", large: true)
        XCTAssertTrue(app.images["pusula.brand"].waitForExistence(timeout: 10))
        for index in 1...4 {
            XCTAssertEqual(app.staticTexts["intro.progress"].label, "\(index) / 4")
            tap("intro.next", app)
        }
        XCTAssertTrue(app.staticTexts["intro.finished"].waitForExistence(timeout: 10))
    }
    func testIntroSkipAndCoachNextBackFinish() {
        var app = launch("intro")
        tap("intro.skip", app)
        XCTAssertTrue(app.staticTexts["intro.finished"].waitForExistence(timeout: 10))
        app.terminate()
        app = launch("coach", role: "TECHNICIAN", large: true)
        tap("tour.next", app)
        XCTAssertEqual(app.staticTexts["tour.progress"].label, "2 / 5")
        tap("tour.back", app)
        XCTAssertEqual(app.staticTexts["tour.progress"].label, "1 / 5")
        for _ in 0..<5 { tap("tour.next", app) }
        XCTAssertTrue(app.staticTexts["tour.finished"].waitForExistence(timeout: 10))
    }
}
