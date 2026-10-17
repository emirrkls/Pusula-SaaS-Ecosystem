import XCTest

final class SupportFlowTests:XCTestCase {
    override func setUpWithError() throws {continueAfterFailure=false}
    func testLostResponseRetriesTheSameIntentWithoutSecondWrite() {
        let app=launch("lost-response")
        reveal("support.new",in:app).tap()
        let subject=app.textFields["support.subject"];XCTAssertTrue(subject.waitForExistence(timeout:10));subject.tap();subject.typeText("Synthetic QA")
        let body=app.descendants(matching:.any).matching(identifier:"support.body").firstMatch;body.tap();body.typeText("Synthetic support text")
        dismissKeyboard(app)
        reveal("support.send",in:app).tap()
        XCTAssertTrue(app.staticTexts["support.error"].waitForExistence(timeout:10))
        let retry=reveal("support.send",in:app);XCTAssertTrue(retry.isEnabled);XCTAssertEqual(retry.label,"Aynı gönderimi tekrar dene");retry.tap()
        XCTAssertTrue(app.staticTexts["support.success"].waitForExistence(timeout:10))
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
        XCTAssertTrue(app.staticTexts["support.error"].waitForExistence(timeout:10));XCTAssertTrue(app.buttons["support.ticket.1"].exists)
        app.buttons["support.ticket.1"].tap();XCTAssertTrue(app.staticTexts["Synthetic support conversation"].waitForExistence(timeout:10))
    }
    func testAnonymousSupportHasFallbackButCannotSubmit() {
        let app=launch("anonymous");XCTAssertTrue(app.staticTexts["support.login_help"].waitForExistence(timeout:10));XCTAssertFalse(app.buttons["support.new"].exists)
        XCTAssertEqual(app.staticTexts["support.fixture.counts"].value as? String,"0:0:false")
    }
    private func launch(_ scenario:String)->XCUIApplication {
        let app=XCUIApplication();app.launchArguments=["--app-store-screenshots","-AppleLanguages","(tr)","-AppleLocale","tr_TR"]
        app.launchEnvironment["PUSULA_SUPPORT_FIXTURE"]=scenario;app.launchEnvironment["PUSULA_SUPPORT_RESET_DRAFT"]="1";app.launch();return app
    }
    private func dismissKeyboard(_ app:XCUIApplication) {let done=app.buttons["keyboard.done"];if done.exists && done.isHittable {done.tap()}else if app.buttons["Bitti"].exists {app.buttons["Bitti"].tap()}}
    private func reveal(_ identifier:String,in app:XCUIApplication)->XCUIElement {
        let element=app.buttons[identifier];for _ in 0..<10 {if element.exists && element.isHittable {break};app.swipeUp()};XCTAssertTrue(element.waitForExistence(timeout:10),identifier);XCTAssertTrue(element.isHittable,identifier);return element
    }
}
