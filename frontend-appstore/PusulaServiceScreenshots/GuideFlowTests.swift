import XCTest

final class GuideFlowTests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }
    override func tearDownWithError() throws {
        if (testRun?.failureCount ?? 0) > 0 {
            // These tests only launch the offline simulator fixture.
            let app = XCUIApplication()
            let screenshot = XCTAttachment(screenshot: app.screenshot())
            screenshot.name = "Guide failure screen"
            screenshot.lifetime = .keepAlways
            add(screenshot)
            let hierarchy = XCTAttachment(string: app.debugDescription)
            hierarchy.name = "Guide failure accessibility hierarchy"
            hierarchy.lifetime = .keepAlways
            add(hierarchy)
        }
        try super.tearDownWithError()
    }
    private func launch(_ scenario: String = "hub", role: String = "COMPANY_ADMIN", reset: Bool = true, account: String = "fixture-user-a", large: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--app-store-screenshots", "-AppleLanguages", "(tr)", "-AppleLocale", "tr_TR"]
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
        let needsReveal = id.hasPrefix("guide.topic.") || id == "settings.guide.open" || id == "account.guide.open"
        if needsReveal {
            // Lazy catalog/menu rows can legitimately need scrolling. Never
            // swipe a destination while waiting for its fixed footer to appear.
            _ = button.waitForExistence(timeout: 2)
            for _ in 0..<12 {
                if button.exists && button.isHittable { break }
                app.swipeUp()
            }
        }
        let ready = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            button.exists && button.isHittable
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [ready], timeout: 12), .completed,
                       "\(id): exists=\(button.exists), hittable=\(button.isHittable), frame=\(button.frame)")
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
        tap("guide.quick-start", app)
        tap("guide.next", app)
        tap("guide.close", app)
        XCTAssertTrue(app.buttons["guide.quick-start"].waitForExistence(timeout: 12))
    }
    func testTechnicianActualProfileEntryAndRoleFilter() {
        let app = launch("technician-entry", role: "TECHNICIAN")
        tap("account.guide.open", app)
        XCTAssertTrue(app.buttons["guide.quick-start"].waitForExistence(timeout: 12))
        tap("guide.quick-start", app)
        tap("guide.next", app)
        tap("guide.close", app)
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
        // Independent cold launches catch presentation/focus races rather than
        // accepting a single lucky transition on the Cloud simulator.
        for iteration in 0..<3 {
            let app = launch(large: true)
            let search = app.searchFields.firstMatch
            XCTAssertTrue(search.waitForExistence(timeout: 12))
            search.tap(); search.typeText(iteration == 1 ? "iskonto" : "iskonto\n")
            tap("guide.topic.discount", app)
            XCTAssertTrue(app.staticTexts["guide.step.title"].waitForExistence(timeout: 12))
            let noKeyboard = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                app.keyboards.count == 0
            }, object: nil)
            XCTAssertEqual(XCTWaiter.wait(for: [noKeyboard], timeout: 5), .completed)
            let next = app.buttons["guide.next"]
            let footerReady = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
                next.exists && next.isHittable && next.frame.height > 0
            }, object: nil)
            XCTAssertEqual(XCTWaiter.wait(for: [footerReady], timeout: 12), .completed)
            let screen = app.windows.firstMatch.frame
            XCTAssertGreaterThanOrEqual(next.frame.minY, screen.minY)
            XCTAssertLessThanOrEqual(next.frame.maxY, screen.maxY)
            XCTAssertGreaterThan(next.frame.height, 0)
            if iteration == 0 {
                let screenshot = XCTAttachment(screenshot: app.screenshot())
                screenshot.name = "Guide lesson at accessibility XXXL"
                screenshot.lifetime = .keepAlways
                add(screenshot)
            }
            tap("guide.next", app)
            XCTAssertTrue(app.staticTexts["guide.step.progress"].label.hasPrefix("2 /"))
            app.terminate()
        }
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
