import XCTest

final class ConnectionDiagnosticsPresentationTests: XCTestCase {
    override func setUpWithError() throws { continueAfterFailure = false }

    func testReportPreviewIsRedactedAndShareIsAvailable() throws {
        let app = launch()
        XCTAssertTrue(app.staticTexts["diagnostics.summary"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.staticTexts["diagnostics.summary"].value as? String, "1:1")
        XCTAssertTrue(reveal("diagnostics.share", in: app).isEnabled)
        reveal("diagnostics.preview.open", in: app, scrollUp: false).tap()
        let body = app.staticTexts["diagnostics.report.body"]
        XCTAssertTrue(body.waitForExistence(timeout: 5))
        // Selectable SwiftUI Text can expose a presentation label rather than
        // its full document. The explicit accessible value is the displayed text.
        let reportText = body.value as? String ?? body.label
        // Validate the JSON document, not slash escaping or pretty-print style.
        let document = try XCTUnwrap(try JSONSerialization.jsonObject(with: Data(reportText.utf8)) as? [String: Any])
        let records = try XCTUnwrap(document["records"] as? [[String: Any]])
        let record = try XCTUnwrap(records.first)
        let errors = try XCTUnwrap(record["errors"] as? [[String: Any]])
        XCTAssertEqual(records.count, 1)
        XCTAssertEqual(record["route"] as? String, "/api/tickets/:id/photos")
        XCTAssertEqual(record["target"] as? String, "api")
        XCTAssertEqual(errors.first?["domain"] as? String, "NSURLErrorDomain")
        XCTAssertEqual(errors.first?["code"] as? Int, -1200)
        XCTAssertFalse(reportText.contains("fixture-secret"))
        XCTAssertFalse(reportText.contains("987654"))
    }

    func testClearRemovesRecordsAndDisablesSharing() {
        let app = launch()
        XCTAssertTrue(app.staticTexts["diagnostics.summary"].waitForExistence(timeout: 10))
        reveal("diagnostics.clear", in: app).tap()
        let confirm = app.buttons["Kayıtları Sil"]
        XCTAssertTrue(confirm.waitForExistence(timeout: 5))
        confirm.tap()
        let predicate = NSPredicate(format: "exists == true AND value == %@", "0:0")
        let empty = XCTNSPredicateExpectation(predicate: predicate, object: app.staticTexts["diagnostics.summary"])
        XCTAssertEqual(XCTWaiter.wait(for: [empty], timeout: 10), .completed)
        XCTAssertFalse(reveal("diagnostics.share", in: app, scrollUp: false).isEnabled)
    }

    private func launch() -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--app-store-screenshots", "-AppleLanguages", "(tr)", "-AppleLocale", "tr_TR"]
        app.launchEnvironment["PUSULA_CONNECTION_DIAGNOSTICS_FIXTURE"] = "1"
        app.launch()
        return app
    }

    private func reveal(_ identifier: String, in app: XCUIApplication, scrollUp: Bool = true) -> XCUIElement {
        let element = app.buttons[identifier]
        for _ in 0..<8 {
            if element.exists && element.isHittable { break }
            if scrollUp { app.swipeUp() } else { app.swipeDown() }
        }
        XCTAssertTrue(element.waitForExistence(timeout: 5), identifier)
        XCTAssertTrue(element.isHittable, identifier)
        return element
    }
}
