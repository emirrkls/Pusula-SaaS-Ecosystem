import Foundation
import XCTest
@testable import PusulaRouting

final class RefreshRequestStateTests: XCTestCase {
    private func snapshot(companyId: Int = 10, token: String = "test-token",
                          revision: UUID = UUID()) -> AuthenticatedSessionSnapshot {
        AuthenticatedSessionSnapshot(token: token, companyId: companyId, revision: revision)
    }

    func testInitialReadAndRefreshCannotStartDuplicateRequests() {
        var state = RefreshRequestState()
        let session = snapshot()
        let request = state.begin(for: session)
        XCTAssertNotNil(request)
        XCTAssertTrue(state.isLoading)
        XCTAssertNil(state.begin(for: session))
        state.finish(request!)
        XCTAssertFalse(state.isLoading)
        XCTAssertNotNil(state.begin(for: session))
    }

    func testDismissedScreenCannotAcceptLateSuccessOrFailure() {
        var state = RefreshRequestState()
        let session = snapshot()
        let request = state.begin(for: session)!
        state.invalidate()
        XCTAssertFalse(state.accepts(request, session: session, isCancelled: false))
        XCTAssertFalse(state.isLoading)
    }

    func testOldCompletionCannotClearTheReopenedScreensLoadingIndicator() {
        var state = RefreshRequestState()
        let session = snapshot()
        let oldRequest = state.begin(for: session)!
        state.invalidate()
        let newRequest = state.begin(for: session)!
        state.finish(oldRequest)
        XCTAssertTrue(state.isLoading)
        XCTAssertFalse(state.accepts(oldRequest, session: session, isCancelled: false))
        XCTAssertTrue(state.accepts(newRequest, session: session, isCancelled: false))
    }

    func testSwitchingCompanyRejectsEarlierResult() {
        var state = RefreshRequestState()
        let firstSession = snapshot()
        let oldRequest = state.begin(for: firstSession)!
        let nextSession = snapshot(companyId: 20)
        let newRequest = state.begin(for: nextSession)!
        XCTAssertFalse(state.accepts(oldRequest, session: nextSession, isCancelled: false))
        XCTAssertTrue(state.accepts(newRequest, session: nextSession, isCancelled: false))
        state.finish(oldRequest)
        XCTAssertTrue(state.isLoading)
    }

    func testNewLoginWithSameCompanyAndTokenStillRejectsOldRequest() {
        var state = RefreshRequestState()
        let previous = snapshot()
        let request = state.begin(for: previous)!
        let renewed = snapshot(companyId: previous.companyId, token: previous.token)
        XCTAssertFalse(state.accepts(request, session: renewed, isCancelled: false))
        XCTAssertFalse(state.accepts(request, session: nil, isCancelled: false))
        XCTAssertFalse(state.accepts(request, session: previous, isCancelled: true))
    }

    func testCancellationIncludesSwiftAndRawURLSessionErrors() {
        XCTAssertTrue(LoadCancellationPolicy.isCancellation(CancellationError()))
        XCTAssertTrue(LoadCancellationPolicy.isCancellation(URLError(.cancelled)))
        XCTAssertTrue(LoadCancellationPolicy.isCancellation(
            NSError(domain: NSURLErrorDomain, code: -999)))
        XCTAssertTrue(LoadCancellationPolicy.isCancellation(
            URLError(.timedOut), taskIsCancelled: true))
    }

    func testRealFailuresRemainVisibleRatherThanBeingSilentlyDiscarded() {
        for code in [URLError.Code.secureConnectionFailed, .timedOut,
                     .networkConnectionLost, .notConnectedToInternet] {
            XCTAssertFalse(LoadCancellationPolicy.isCancellation(URLError(code)))
        }
        XCTAssertFalse(LoadCancellationPolicy.isCancellation(
            NSError(domain: "OtherDomain", code: -999)))
    }
}
