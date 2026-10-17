import XCTest

final class ServicePhotoFlowTests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }
    private func launch(large: Bool = false) -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--app-store-screenshots", "-AppleLanguages", "(tr)", "-AppleLocale", "tr_TR"]
        if large { app.launchArguments += ["-UIPreferredContentSizeCategoryName", "UICTContentSizeCategoryAccessibilityXXXL"] }
        app.launchEnvironment["PUSULA_PHOTO_BATCH_TEST"] = "1"
        app.launch(); return app
    }
    private func reveal(_ id: String, _ app: XCUIApplication) -> XCUIElement {
        let element = app.descendants(matching: .any).matching(identifier: id).firstMatch
        for _ in 0..<10 {
            if element.exists && element.isHittable { break }
            app.swipeUp()
        }
        XCTAssertTrue(element.waitForExistence(timeout: 10)); XCTAssertTrue(element.isHittable)
        return element
    }
    func testPartialBatchReplaysOnlyUnconfirmedPhotoWithoutDuplicateWrite() {
        let app = launch()
        reveal("photos.fixture.pick", app).tap()
        reveal("photos.batch.retry", app).tap()
        let done = XCTNSPredicateExpectation(predicate: NSPredicate { _, _ in
            app.staticTexts["photos.fixture.counts"].label == "4:3" &&
                app.descendants(matching: .any).matching(identifier: "photos.batch.summary").firstMatch.label == "3 / 3 görsel yüklendi"
        }, object: nil)
        XCTAssertEqual(XCTWaiter.wait(for: [done], timeout: 12), .completed)
        XCTAssertFalse(app.buttons["photos.batch.retry"].exists)
        XCTAssertEqual(app.descendants(matching: .any).matching(identifier: "photos.batch.summary").firstMatch.label, "3 / 3 görsel yüklendi")
    }
    func testLargeTextFailedBatchRequiresExplicitLeaveAndKeepsRetryReachable() {
        let app = launch(large: true)
        reveal("photos.fixture.pick", app).tap()
        let retry = reveal("photos.batch.retry", app)
        XCTAssertGreaterThan(retry.frame.height, 0)
        XCTAssertLessThanOrEqual(retry.frame.maxY, app.windows.firstMatch.frame.maxY)
        app.navigationBars.buttons["Tamam"].tap()
        XCTAssertTrue(app.buttons["Bekleyenleri Bırak ve Çık"].waitForExistence(timeout: 5))
        app.buttons["Burada Kal"].tap()
        XCTAssertTrue(app.buttons["photos.batch.retry"].exists)
        XCTAssertEqual(app.staticTexts["photos.fixture.counts"].label, "3:3")
    }
}
