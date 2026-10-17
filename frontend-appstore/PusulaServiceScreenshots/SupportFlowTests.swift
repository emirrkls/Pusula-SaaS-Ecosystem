import XCTest

final class SupportFlowTests:XCTestCase {
    override func setUpWithError() throws {continueAfterFailure=false}
    override func tearDownWithError() throws {
        if (testRun?.failureCount ?? 0)>0 {
            let app=XCUIApplication()
            let screen=XCTAttachment(screenshot:app.screenshot());screen.name="Support failure screen";screen.lifetime = .keepAlways;add(screen)
            let description=app.debugDescription
            print("SUPPORT_OFFLINE_FAILURE_HIERARCHY\n\(description)")
            let tree=XCTAttachment(string:description);tree.name="Support failure accessibility hierarchy";tree.lifetime = .keepAlways;add(tree)
        }
        try super.tearDownWithError()
    }
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
        dismissKeyboard(app)
        XCTAssertEqual(subject.value as? String,"Synthetic QA")
        let body=app.descendants(matching:.any).matching(identifier:"support.body").firstMatch
        let editor=app.scrollViews["support.composer.scroll"]
        XCTAssertTrue(editor.waitForExistence(timeout:10))
        revealComposerField(body,in:app)
        XCTAssertTrue(body.isHittable);body.tap();body.typeText("Synthetic support layout")
        dismissKeyboard(app)
        XCTAssertEqual(body.value as? String,"Synthetic support layout")
        let send=app.buttons["support.send"]
        let ready=XCTNSPredicateExpectation(predicate:NSPredicate{_,_ in send.exists && send.isHittable && send.isEnabled && app.keyboards.count==0},object:nil)
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
    private func dismissKeyboard(_ app:XCUIApplication) {
        let done=app.buttons["keyboard.done"]
        if done.exists && done.isHittable {done.tap()}else if app.buttons["Bitti"].exists {app.buttons["Bitti"].tap()}
        let closed=XCTNSPredicateExpectation(predicate:NSPredicate{_,_ in app.keyboards.count==0},object:nil)
        XCTAssertEqual(XCTWaiter.wait(for:[closed],timeout:5),.completed)
    }
    private func revealComposerField(_ field:XCUIElement,in app:XCUIApplication) {
        let window=app.windows.firstMatch
        let origin=window.coordinate(withNormalizedOffset:CGVector(dx:0,dy:0))
        for _ in 0..<10 {
            let screen=window.frame
            let top=max(screen.minY,app.navigationBars.firstMatch.frame.maxY)+12
            let saved=app.staticTexts["support.draft.saved"]
            let footerTop=saved.exists ? min(saved.frame.minY,app.buttons["support.send"].frame.minY) : app.buttons["support.send"].frame.minY
            let bottom=min(screen.maxY,footerTop)-12
            XCTAssertGreaterThan(bottom-top,field.frame.height,"The editable field must fit above the fixed action area")
            if field.isHittable && field.frame.minY>=top && field.frame.maxY<=bottom {return}
            // isHittable alone can be true beneath a SwiftUI safeAreaInset.
            // Drag the outer gutter, not the text field's own inner scroller.
            let center=(top+bottom)/2
            let limit=(bottom-top)*0.55
            let delta=max(-limit,min(limit,field.frame.midY-center))
            let start=origin.withOffset(CGVector(dx:10,dy:center+delta/2-screen.minY))
            let end=origin.withOffset(CGVector(dx:10,dy:center-delta/2-screen.minY))
            start.press(forDuration:0.05,thenDragTo:end)
        }
        XCTFail("The text field is still obscured by navigation or the action area")
    }
    private func reveal(_ identifier:String,in app:XCUIApplication)->XCUIElement {
        let element=app.buttons[identifier]
        XCTAssertTrue(element.waitForExistence(timeout:12),identifier)
        for _ in 0..<10 {
            if element.isHittable {break}
            // Eager native cards can be above OR below the viewport. Do not
            // swipe past the CTA while its async contact/draft read is loading.
            if element.frame.midY<app.windows.firstMatch.frame.midY {app.swipeDown()} else {app.swipeUp()}
        }
        let ready=XCTNSPredicateExpectation(predicate:NSPredicate { _,_ in
            element.exists&&element.isHittable&&element.isEnabled
        },object:nil)
        XCTAssertEqual(XCTWaiter.wait(for:[ready],timeout:12),.completed,identifier)
        return element
    }
}
