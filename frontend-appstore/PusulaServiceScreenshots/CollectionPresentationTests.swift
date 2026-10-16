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
    func testSlowSubmitShowsProcessingNotRetryAndSavesOnlyOnce() {
        let app = launch("slow-submit")
        payFullAmount(in: app)
        let save = app.buttons["collection.submit"]
        save.tap()
        wait(NSPredicate(format: "label CONTAINS %@ AND enabled == false", "Tahsilat kaydediliyor"), on: save, timeout: 3)
        XCTAssertFalse(app.staticTexts["collection.unconfirmed"].exists)
        XCTAssertFalse(app.buttons["Aynı İşlemi Tekrar Dene"].exists)
        XCTAssertTrue(app.staticTexts["collection.fixture.completed"].waitForExistence(timeout: 10))
        XCTAssertEqual(app.staticTexts["collection.fixture.attempts"].label, "1")
        XCTAssertFalse(app.navigationBars["Tahsilat"].exists)
    }

    func testSlowFailedListRefreshDoesNotHoldCollectionOrDetailOpen() {
        let app = launch("detail-slow-refresh")
        openCollectionFromDetail(in: app)
        payFullAmount(in: app)
        app.buttons["collection.submit"].tap()
        let refresh = app.staticTexts["collection.fixture.refresh"]
        wait(NSPredicate(format: "exists == true AND label == %@", "Liste arka planda yenileniyor"), on: refresh, timeout: 3)
        XCTAssertFalse(app.navigationBars["Tahsilat"].exists)
        XCTAssertFalse(app.navigationBars["İş Emri #901"].exists)
        XCTAssertTrue(app.buttons["Listeyi Kullan"].isHittable)
        app.buttons["Listeyi Kullan"].tap()
        XCTAssertEqual(app.staticTexts["collection.fixture.interactions"].label, "1")
        wait(NSPredicate(format: "label == %@", "Liste yenilenemedi; tahsilat tamamlandı"), on: refresh, timeout: 10)
        XCTAssertTrue(app.staticTexts["collection.fixture.completed"].label.contains("COMPLETED"))
        XCTAssertEqual(app.staticTexts["collection.fixture.attempts"].label, "1")
        XCTAssertFalse(app.buttons["Aynı İşlemi Tekrar Dene"].exists)
        XCTAssertEqual(app.alerts.count, 0)
    }

    func testCancellingCollectionKeepsTicketDetailOpen() {
        let app = launch("detail-slow-refresh")
        openCollectionFromDetail(in: app)
        app.buttons["İptal"].tap()
        XCTAssertTrue(app.navigationBars["İş Emri #901"].waitForExistence(timeout: 5))
        XCTAssertFalse(app.navigationBars["Tahsilat"].exists)
        XCTAssertFalse(app.staticTexts["collection.fixture.completed"].exists)
    }

    func testDefinitiveRejectionAllowsEditingAndDoesNotOfferUnconfirmedReplay() {
        let app = launch("rejected")
        payFullAmount(in: app)
        let save = app.buttons["collection.submit"]
        save.tap()
        let error = app.staticTexts["collection.error"]
        for _ in 0..<3 { if error.isHittable { break }; app.scrollViews.firstMatch.swipeUp() }
        XCTAssertTrue(error.waitForExistence(timeout: 5))
        XCTAssertEqual(error.label, "Sentetik doğrulama hatası")
        XCTAssertFalse(app.staticTexts["collection.unconfirmed"].exists)
        XCTAssertFalse(app.buttons["Aynı İşlemi Tekrar Dene"].exists)
        XCTAssertTrue(save.isEnabled)
        save.tap()
        XCTAssertTrue(app.staticTexts["collection.fixture.completed"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.staticTexts["collection.fixture.attempts"].label, "2")
    }

    func testCollectionDecimalKeyboardCanBeDismissedWithoutLosingAmount() {
        let app = launch()
        let labor = app.textFields["collection.input.Servis / işçilik bedeli"]
        XCTAssertTrue(labor.waitForExistence(timeout: 5))
        labor.tap(); labor.typeText("2000")
        dismissKeyboard(in: app)
        XCTAssertEqual(labor.value as? String, "2000")
        XCTAssertTrue(app.buttons["collection.submit"].isHittable)
        // Starting a save dismisses the keyboard before presenting debt confirmation.
        labor.tap()
        app.buttons["collection.submit"].tap()
        XCTAssertTrue(app.alerts["Cari aktarımı onayla"].waitForExistence(timeout: 5))
        XCTAssertEqual(app.keyboards.count, 0)
        app.alerts.buttons["Vazgeç"].tap()
    }

    func testCustomerPhoneKeyboardDonePreservesPhoneAndCancelIsReachable() {
        let app = launch("keyboard-customer")
        let phone = app.textFields["Telefon"]
        XCTAssertTrue(phone.waitForExistence(timeout: 5))
        phone.tap(); phone.typeText("05400250925")
        dismissKeyboard(in: app)
        XCTAssertEqual(phone.value as? String, "05400250925")
        app.buttons["İptal"].tap()
        XCTAssertFalse(app.navigationBars["Yeni Müşteri"].exists)
    }

    func testMultilineClosingNoteDonePreservesTextAndDoesNotSubmit() {
        let app = launch()
        let disclosure = app.buttons["Kapanış notu ve garanti"]
        for _ in 0..<6 { if disclosure.isHittable { break }; app.scrollViews.firstMatch.swipeUp() }
        XCTAssertTrue(disclosure.isHittable)
        disclosure.tap()
        let note = app.descendants(matching: .any).matching(identifier: "collection.note").firstMatch
        for _ in 0..<3 { if note.isHittable { break }; app.scrollViews.firstMatch.swipeUp() }
        XCTAssertTrue(note.isHittable)
        note.tap(); note.typeText("Sentetik kapanış notu")
        dismissKeyboard(in: app)
        XCTAssertEqual(note.value as? String, "Sentetik kapanış notu")
        XCTAssertTrue(app.buttons["collection.submit"].isEnabled)
        XCTAssertFalse(app.staticTexts["collection.fixture.completed"].exists)
    }

    func testExpenseDecimalKeyboardDonePreservesAmountAndTextStillAcceptsInput() {
        let app = launch("keyboard-expense")
        let amount = app.textFields["Tutar"]
        XCTAssertTrue(amount.waitForExistence(timeout: 5))
        amount.tap(); amount.typeText("1")
        dismissKeyboard(in: app)
        XCTAssertEqual(amount.value as? String, "1")
        let description = app.textFields["Açıklama"]
        description.tap(); description.typeText("Sentetik gider")
        dismissKeyboard(in: app)
        XCTAssertEqual(description.value as? String, "Sentetik gider")
        app.buttons["İptal"].tap() // Never persist a fixture expense.
    }

    private func openCollectionFromDetail(in app: XCUIApplication) {
        let button = app.buttons["Servisi Tamamla & Tahsilat"]
        for _ in 0..<8 { if button.isHittable { break }; app.swipeUp() }
        XCTAssertTrue(button.isHittable)
        button.tap()
        XCTAssertTrue(app.navigationBars["Tahsilat"].waitForExistence(timeout: 5))
    }

    private func payFullAmount(in app: XCUIApplication) {
        let full = app.buttons["Müşteri payının tamamı"]
        for _ in 0..<6 { if full.isHittable { break }; app.scrollViews.firstMatch.swipeUp() }
        XCTAssertTrue(full.isHittable)
        full.tap()
    }

    private func dismissKeyboard(in app: XCUIApplication) {
        XCTAssertTrue(app.keyboards.firstMatch.waitForExistence(timeout: 5))
        let done = app.buttons["keyboard.done"]
        XCTAssertTrue(done.waitForExistence(timeout: 5))
        XCTAssertEqual(app.buttons.matching(identifier: "keyboard.done").count, 1, "Do not duplicate keyboard toolbars in nested sheets")
        XCTAssertTrue(done.isHittable)
        done.tap()
        wait(NSPredicate(format: "exists == false"), on: app.keyboards.firstMatch, timeout: 5)
    }

    private func wait(_ predicate: NSPredicate, on element: XCUIElement, timeout: TimeInterval,
                      file: StaticString = #filePath, line: UInt = #line) {
        let expectation = XCTNSPredicateExpectation(predicate: predicate, object: element)
        XCTAssertEqual(XCTWaiter.wait(for: [expectation], timeout: timeout), .completed, file: file, line: line)
    }

    private func launch(_ scenario: String = "lost-response") -> XCUIApplication {
        let app = XCUIApplication()
        app.launchArguments = ["--app-store-screenshots", "-AppleLanguages", "(tr)", "-AppleLocale", "tr_TR"]
        app.launchEnvironment["PUSULA_COLLECTION_FIXTURE"] = "1"
        app.launchEnvironment["PUSULA_COLLECTION_SCENARIO"] = scenario
        app.launch(); return app
    }
}
