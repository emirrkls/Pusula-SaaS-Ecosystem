import Foundation
import XCTest
@testable import PusulaTickets

final class TechnicianNoteSubmissionTests: XCTestCase {
    private let session = UUID()

    func testBlankNoteDoesNotStartAWrite() {
        var state = TechnicianNoteSubmissionState()
        let key = state.requestID
        XCTAssertNil(state.begin(ticketID: 999, sessionRevision: session, content: " \n ", important: false))
        XCTAssertTrue(state.canEdit)
        XCTAssertEqual(state.requestID, key)
    }

    func testLostResponseRetainsExactlyTheSameKeyTextTargetAndImportantFlag() throws {
        var state = TechnicianNoteSubmissionState()
        let key = state.requestID
        let first = try XCTUnwrap(state.begin(ticketID: 999, sessionRevision: session,
                                              content: "  Synthetic note  ", important: true))
        XCTAssertNil(state.begin(ticketID: 999, sessionRevision: session, content: "Double tap", important: false))
        state.fail(allowsCorrection: false)
        XCTAssertTrue(state.showsRetry)
        XCTAssertFalse(state.canEdit)
        let replay = try XCTUnwrap(state.begin(ticketID: 999, sessionRevision: session,
                                               content: "Changed text must not be replayed", important: false))
        XCTAssertEqual(replay, first)
        XCTAssertEqual(replay.content, "Synthetic note")
        XCTAssertTrue(replay.important)
        XCTAssertEqual(state.requestID, key)
    }

    func testAnotherAccountOrTicketCannotReplayAnUnconfirmedNote() {
        var state = TechnicianNoteSubmissionState()
        _ = state.begin(ticketID: 999, sessionRevision: session, content: "Synthetic", important: false)
        state.fail(allowsCorrection: false)
        XCTAssertNil(state.begin(ticketID: 999, sessionRevision: UUID(), content: "Synthetic", important: false))
        XCTAssertNil(state.begin(ticketID: 1000, sessionRevision: session, content: "Synthetic", important: false))
        XCTAssertTrue(state.showsRetry)
    }

    func testConfirmedSuccessAllowsANewIdenticalNoteWithADifferentKey() throws {
        var state = TechnicianNoteSubmissionState()
        let first = state.requestID
        _ = state.begin(ticketID: 999, sessionRevision: session, content: "Synthetic", important: false)
        state.succeed()
        XCTAssertTrue(state.canEdit)
        XCTAssertFalse(state.showsRetry)
        XCTAssertNotEqual(state.requestID, first)
        XCTAssertNotNil(state.begin(ticketID: 999, sessionRevision: session, content: "Synthetic", important: false))
    }

    func testDefiniteRejectionAllowsCorrectionButIdleCallbacksDoNotRotateKeys() {
        var state = TechnicianNoteSubmissionState()
        let first = state.requestID
        state.succeed(); state.fail(allowsCorrection: true)
        XCTAssertEqual(first, state.requestID)
        _ = state.begin(ticketID: 999, sessionRevision: session, content: "Synthetic", important: false)
        state.fail(allowsCorrection: true)
        XCTAssertTrue(state.canEdit)
        XCTAssertNotEqual(first, state.requestID)
        XCTAssertNotNil(state.begin(ticketID: 999, sessionRevision: session, content: "Corrected", important: true))
    }
}
