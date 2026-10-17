import XCTest

final class SupportFlowTests:XCTestCase {
    override func setUpWithError() throws {continueAfterFailure=false}
    func testCompanyAdminOpensSupportFromActualAccountSettings() {
        let app=launch("normal",entry:"admin")
        openAdminAccount(app)
        reveal("settings.support.open",in:app).tap()
        assertSupportCanComposeWithoutAuthenticating(app)
    }
    func testCompanyAdminCanReachSupportWhenSettingsDataFails() {
        let app=launch("settings-load-failure",entry:"admin")
        XCTAssertTrue(app.descendants(matching:.any).matching(identifier:"settings.initial-load-failure").firstMatch.waitForExistence(timeout:10))
        openAdminAccount(app)
        reveal("settings.support.open",in:app).tap()
        assertSupportCanComposeWithoutAuthenticating(app)
    }
    func testTechnicianOpensSupportFromActualProfile() {
        let app=launch("normal",entry:"technician")
        reveal("account.support.open",in:app).tap()
        assertSupportCanComposeWithoutAuthenticating(app)
    }
    func testLostResponseRetriesTheSameIntentWithoutSecondWrite() {
        let app=launch("lost-response")
        reveal("support.new",in:app).tap()
        let subject=app.textFields["support.subject"];XCTAssertTrue(subject.waitForExistence(timeout:10));subject.tap();subject.typeText("Synthetic QA")
        let body=app.descendants(matching:.any).matching(identifier:"support.body").firstMatch;body.tap();body.typeText("Synthetic support text")
        dismissKeyboard(app)
        reveal("support.send",in:app).tap()
        XCTAssertTrue(app.descendants(matching:.any).matching(identifier:"support.error").firstMatch.waitForExistence(timeout:10))
        let retry=reveal("support.send",in:app);XCTAssertTrue(retry.isEnabled);XCTAssertEqual(retry.label,"Aynı gönderimi tekrar dene");retry.tap()
        XCTAssertTrue(app.descendants(matching:.any).matching(identifier:"support.success").firstMatch.waitForExistence(timeout:10))
        XCTAssertEqual(app.staticTexts["support.fixture.counts"].value as? String,"2:1:false")
    }
    func testSubjectOnlyDraftSurvivesApplicationRelaunch() {
        let app=launch("normal")
        reveal("support.new",in:app).tap();let field=app.textFields["support.subject"];field.tap();field.typeText("Preserved subject")
        dismissKeyboard(app);XCTAssertTrue(app.staticTexts["support.draft.saved"].waitForExistence(timeout:10))
        app.terminate();app.launchEnvironment["PUSULA_SUPPORT_RESET_DRAFT"]="0";app.launch()
        let restored=app.textFields["support.subject"];XCTAssertTrue(restored.waitForExistence(timeout:10));XCTAssertEqual(restored.value as? String,"Preserved subject")
    }
    func testRefreshFailurePreservesExistingTicket() {
        let app=launch("refresh-failure");XCTAssertTrue(app.buttons["support.ticket.1"].waitForExistence(timeout:10));reveal("support.refresh",in:app).tap()
        XCTAssertTrue(app.descendants(matching:.any).matching(identifier:"support.error").firstMatch.waitForExistence(timeout:10));XCTAssertTrue(app.buttons["support.ticket.1"].exists)
        app.buttons["support.ticket.1"].tap();XCTAssertTrue(app.staticTexts["Synthetic support conversation"].waitForExistence(timeout:10))
    }
    func testAnonymousSupportHasFallbackButCannotSubmit() {
        let app=launch("anonymous");XCTAssertTrue(app.descendants(matching:.any).matching(identifier:"support.login_help").firstMatch.waitForExistence(timeout:10));XCTAssertFalse(app.buttons["support.new"].exists)
        XCTAssertEqual(app.staticTexts["support.fixture.counts"].value as? String,"0:0:false")
    }
    private func openAdminAccount(_ app:XCUIApplication) {
        let sections=app.segmentedControls["settings.sections"]
        XCTAssertTrue(sections.waitForExistence(timeout:10))
        let account=sections.buttons["Hesap"]
        XCTAssertTrue(account.isHittable);account.tap()
    }
    private func assertSupportCanComposeWithoutAuthenticating(_ app:XCUIApplication) {
        let new=reveal("support.new",in:app)
        XCTAssertTrue(app.navigationBars["Yardım ve Destek"].exists)
        new.tap()
        XCTAssertTrue(app.textFields["support.subject"].waitForExistence(timeout:10))
        XCTAssertEqual(app.staticTexts["support.fixture.counts"].value as? String,"0:0:false")
    }
    func testSupportComposerKeepsSubmitVisibleAtAccessibilityTextSize() {
        let app=launch("normal",large:true)
        XCTAssertTrue(app.descendants(matching:.any).matching(identifier:"support.hero").firstMatch.waitForExistence(timeout:10))
        reveal("support.new",in:app).tap()
        let subject=app.textFields["support.subject"];XCTAssertTrue(subject.waitForExistence(timeout:10))
        subject.tap();subject.typeText("Synthetic QA")
        let body=app.descendants(matching:.any).matching(identifier:"support.body").firstMatch
        for _ in 0..<10 {if body.exists && body.isHittable{break};app.scrollViews.firstMatch.swipeUp()}
        XCTAssertTrue(body.isHittable);body.tap();body.typeText("Synthetic support layout")
        dismissKeyboard(app)
        let send=app.buttons["support.send"]
        let ready=XCTNSPredicateExpectation(predicate:NSPredicate{_,_ in send.exists && send.isHittable && app.keyboards.count==0},object:nil)
        XCTAssertEqual(XCTWaiter.wait(for:[ready],timeout:10),.completed)
        let screen=app.windows.firstMatch.frame
        XCTAssertGreaterThan(send.frame.height,0);XCTAssertGreaterThanOrEqual(send.frame.minY,screen.minY);XCTAssertLessThanOrEqual(send.frame.maxY,screen.maxY)
        XCTAssertEqual(app.staticTexts["support.fixture.counts"].value as? String,"0:0:false")
        let image=XCTAttachment(screenshot:app.screenshot());image.name="Support composer at accessibility XXXL";image.lifetime = .keepAlways;add(image)
    }
    private func launch(_ scenario:String,entry:String="direct",large:Bool=false)->XCUIApplication {
        let app=XCUIApplication();app.launchArguments=["--app-store-screenshots","-AppleLanguages","(tr)","-AppleLocale","tr_TR"]
        if large {app.launchArguments += ["-UIPreferredContentSizeCategoryName","UICTContentSizeCategoryAccessibilityXXXL"]}
        app.launchEnvironment["PUSULA_SUPPORT_FIXTURE"]=scenario;app.launchEnvironment["PUSULA_SUPPORT_RESET_DRAFT"]="1";app.launchEnvironment["PUSULA_SUPPORT_ENTRY"]=entry;app.launch();return app
    }
    private func dismissKeyboard(_ app:XCUIApplication) {let done=app.buttons["keyboard.done"];if done.exists && done.isHittable {done.tap()}else if app.buttons["Bitti"].exists {app.buttons["Bitti"].tap()}}
    private func reveal(_ identifier:String,in app:XCUIApplication)->XCUIElement {
        let element=app.buttons[identifier];for _ in 0..<10 {if element.exists && element.isHittable {break};app.swipeUp()};XCTAssertTrue(element.waitForExistence(timeout:10),identifier);XCTAssertTrue(element.isHittable,identifier);return element
    }
}
