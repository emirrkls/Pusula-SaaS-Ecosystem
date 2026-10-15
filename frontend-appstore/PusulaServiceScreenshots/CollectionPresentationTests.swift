import XCTest

final class CollectionPresentationTests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }
    func testFeeInputsPrecedeAllocationAndCollectionAndSaveRemainsReachable() {
        let app = launch()
        let labor = app.textFields["collection.input.Servis / işçilik bedeli"]
        XCTAssertTrue(labor.waitForExistence(timeout: 10))
        XCTAssertTrue(labor.isHittable)
        XCTAssertFalse(app.staticTexts["Servis Tutarı"].exists)
        let save = app.buttons["Tahsilatı Kaydet ve İşi Tamamla"]
        XCTAssertTrue(save.isHittable)
        labor.tap(); labor.typeText("2000")
        XCTAssertTrue(save.isHittable, "The action must remain available above the keyboard")
    }
    func testLostResponseFreezesInputAndRetriesTheSamePayload() {
        let app = launch()
        let labor = app.textFields["collection.input.Servis / işçilik bedeli"]
        XCTAssertTrue(labor.waitForExistence(timeout: 10))
        let full = app.buttons["Müşteri payının tamamı"]
        for _ in 0..<4 { if full.isHittable { break }; app.scrollViews.firstMatch.swipeUp() }
        XCTAssertTrue(full.isHittable); full.tap()
        app.buttons["Tahsilatı Kaydet ve İşi Tamamla"].tap()
        let retry = app.buttons["Aynı İşlemi Tekrar Dene"]
        XCTAssertTrue(retry.waitForExistence(timeout: 10))
        XCTAssertFalse(labor.isEnabled)
        retry.tap()
        XCTAssertTrue(app.staticTexts["collection.fixture.completed"].waitForExistence(timeout: 10))
    }
    private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--app-store-screenshots", "-AppleLanguages", "(tr)", "-AppleLocale", "tr_TR"]
        app.launchEnvironment["PUSULA_COLLECTION_FIXTURE"] = "1"
        app.launch(); return app
    }
}
