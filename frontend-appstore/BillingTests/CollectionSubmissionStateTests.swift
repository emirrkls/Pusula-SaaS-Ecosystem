import XCTest
@testable import PusulaBilling

final class CollectionSubmissionStateTests: XCTestCase {
    func testSlowFirstRequestIsProcessingNotRetryAndBlocksDuplicateSubmit() {
        var state = CollectionSubmissionState<String>()
        XCTAssertTrue(state.canEdit)
        XCTAssertEqual(state.begin { "original payment" }, "original payment")
        XCTAssertTrue(state.isProcessing)
        XCTAssertFalse(state.showsRetry)
        XCTAssertFalse(state.canEdit)
        XCTAssertFalse(state.canSubmit)
        XCTAssertNil(state.begin { XCTFail("Must not build another payload"); return "changed" })
    }

    func testUnconfirmedFailureReplaysFrozenPayloadAndKey() {
        var state = CollectionSubmissionState<String>()
        let key = state.requestID
        _ = state.begin { "original" }
        state.fail(allowsCorrection: false)
        XCTAssertTrue(state.showsRetry)
        XCTAssertFalse(state.canEdit)
        XCTAssertTrue(state.canSubmit)
        XCTAssertEqual(state.begin { XCTFail("Retry must not reread fields"); return "changed" }, "original")
        XCTAssertEqual(state.requestID, key)
        XCTAssertFalse(state.showsRetry, "In-flight retries also show processing, not another replay action")
    }

    func testConfirmedSuccessIsTerminalEvenIfRefreshFailsOrIsSlow() {
        var state = CollectionSubmissionState<String>()
        _ = state.begin { "payment" }
        state.succeed()
        XCTAssertEqual(state.phase, .succeeded)
        XCTAssertFalse(state.isProcessing)
        XCTAssertFalse(state.showsRetry)
        XCTAssertFalse(state.canSubmit)
        XCTAssertFalse(state.canEdit)
        // A later list failure cannot make the committed mutation replayable.
        state.fail(allowsCorrection: false)
        state.fail(allowsCorrection: true)
        XCTAssertEqual(state.phase, .succeeded)
        XCTAssertNil(state.begin { "duplicate" })
    }

    func testDefinitiveRejectionAllowsCorrectionWithNewKey() {
        var state = CollectionSubmissionState<String>()
        let oldKey = state.requestID
        _ = state.begin { "invalid" }
        state.fail(allowsCorrection: true)
        XCTAssertTrue(state.canEdit)
        XCTAssertFalse(state.showsRetry)
        XCTAssertNil(state.payload)
        XCTAssertNotEqual(state.requestID, oldKey)
        XCTAssertEqual(state.begin { "corrected" }, "corrected")
    }

    func testRepeatedUnconfirmedFailuresKeepSameKeyUntilSuccess() {
        var state = CollectionSubmissionState<Decimal>()
        let key = state.requestID
        for _ in 0..<3 {
            XCTAssertEqual(state.begin { 10 }, 10)
            state.fail(allowsCorrection: false)
            XCTAssertEqual(state.requestID, key)
        }
        _ = state.begin { 999 }
        state.succeed()
        XCTAssertEqual(state.payload, 10)
        XCTAssertFalse(state.canSubmit)
    }
}
