import Foundation
import XCTest
@testable import PusulaTickets

final class TicketDetailSectionStateTests: XCTestCase {
    func testInitialAndLoadingAreNotEmptyResults() throws {
        var state = TicketDetailSectionState<Int>()
        XCTAssertFalse(state.showsEmpty)
        XCTAssertFalse(state.isLoaded)
        _ = try XCTUnwrap(state.beginLoading())
        XCTAssertTrue(state.isLoading)
        XCTAssertFalse(state.showsEmpty)
        XCTAssertNil(state.failure)
    }

    func testOnlySuccessfulEmptyReadShowsEmpty() throws {
        var state = TicketDetailSectionState<Int>()
        let request = try XCTUnwrap(state.beginLoading())
        state.succeed([], request: request)
        XCTAssertTrue(state.showsEmpty)
        XCTAssertTrue(state.isLoaded)
        XCTAssertFalse(state.isLoading)
    }

    func testFailedInitialReadIsNotEmpty() throws {
        var state = TicketDetailSectionState<Int>()
        let request = try XCTUnwrap(state.beginLoading())
        state.fail(.secureConnection, request: request)
        XCTAssertEqual(state.failure, .secureConnection)
        XCTAssertFalse(state.showsEmpty)
        XCTAssertFalse(state.isLoaded)
        XCTAssertFalse(state.isLoading)
    }

    func testFailedRefreshKeepsRecordsWithoutClaimingTheyAreCurrent() throws {
        var state = TicketDetailSectionState<Int>()
        state.replaceVerifiedItems([1, 2])
        let request = try XCTUnwrap(state.beginLoading())
        XCTAssertEqual(state.items, [1, 2])
        state.fail(.connection, request: request)
        XCTAssertEqual(state.items, [1, 2])
        XCTAssertEqual(state.failure, .connection)
        XCTAssertFalse(state.isLoaded)
        XCTAssertFalse(state.showsEmpty)
    }

    func testFailedRefreshOfPreviouslyEmptySectionDoesNotShowEmpty() throws {
        var state = TicketDetailSectionState<Int>()
        state.replaceVerifiedItems([])
        let request = try XCTUnwrap(state.beginLoading())
        state.fail(.unavailable, request: request)
        XCTAssertFalse(state.showsEmpty)
    }

    func testRetryClearsFailureAndReplacesRecordsOnlyOnSuccess() throws {
        var state = TicketDetailSectionState<Int>()
        state.replaceVerifiedItems([1])
        let first = try XCTUnwrap(state.beginLoading())
        state.fail(.connection, request: first)
        let retry = try XCTUnwrap(state.beginLoading())
        XCTAssertNil(state.failure)
        XCTAssertEqual(state.items, [1])
        state.succeed([2, 3], request: retry)
        XCTAssertTrue(state.isLoaded)
        XCTAssertEqual(state.items, [2, 3])
    }

    func testDuplicateReadDoesNotReplaceActiveRequest() throws {
        var state = TicketDetailSectionState<Int>()
        let first = try XCTUnwrap(state.beginLoading())
        XCTAssertNil(state.beginLoading())
        state.succeed([1], request: first)
        XCTAssertEqual(state.items, [1])
    }

    func testLateCompletionCannotOverwriteRetryOrVerifiedMutation() throws {
        var state = TicketDetailSectionState<Int>()
        let old = try XCTUnwrap(state.beginLoading())
        state.cancel(request: old)
        let current = try XCTUnwrap(state.beginLoading())
        state.succeed([99], request: old)
        state.fail(.connection, request: old)
        XCTAssertTrue(state.isLoading)
        XCTAssertTrue(state.items.isEmpty)
        state.succeed([1], request: current)
        state.replaceVerifiedItems([2])
        state.succeed([99], request: current)
        XCTAssertEqual(state.items, [2])
    }

    func testCancellationKeepsPreviousRecordsAndDoesNotBecomeFailure() throws {
        var state = TicketDetailSectionState<Int>()
        state.replaceVerifiedItems([1])
        let request = try XCTUnwrap(state.beginLoading())
        state.cancel(request: request)
        XCTAssertEqual(state.items, [1])
        XCTAssertTrue(state.isLoaded)
        XCTAssertNil(state.failure)
    }

    func testInitialCancellationAllowsRetryWithoutEmptyOrError() throws {
        var state = TicketDetailSectionState<Int>()
        let request = try XCTUnwrap(state.beginLoading())
        state.cancel(request: request)
        XCTAssertEqual(state.phase, .cancelled)
        XCTAssertFalse(state.isLoading)
        XCTAssertFalse(state.showsEmpty)
        XCTAssertNil(state.failure)
        XCTAssertNotNil(state.beginLoading())
    }

    func testPartialMutationDoesNotHideFailedFullListRead() throws {
        var state = TicketDetailSectionState<Int>()
        let request = try XCTUnwrap(state.beginLoading())
        state.fail(.connection, request: request)
        state.updateVisibleItems([7])
        XCTAssertEqual(state.items, [7])
        XCTAssertEqual(state.failure, .connection)
        XCTAssertFalse(state.isLoaded)
    }

    func testCancellationClassifierDoesNotHideTLSOrOtherErrors() {
        XCTAssertTrue(TicketDetailLoadFailure.isCancellation(CancellationError()))
        XCTAssertTrue(TicketDetailLoadFailure.isCancellation(URLError(.cancelled)))
        XCTAssertTrue(TicketDetailLoadFailure.isCancellation(NSError(domain: NSURLErrorDomain, code: -999)))
        XCTAssertFalse(TicketDetailLoadFailure.isCancellation(URLError(.secureConnectionFailed)))
        XCTAssertFalse(TicketDetailLoadFailure.isCancellation(URLError(.timedOut)))
        XCTAssertFalse(TicketDetailLoadFailure.isCancellation(NSError(domain: "Other", code: -999)))
    }

    func testSecureConnectionFailuresRemainVisibleAndDistinctFromConnectivity() {
        for code: URLError.Code in [.secureConnectionFailed, .serverCertificateUntrusted,
                                    .serverCertificateHasBadDate, .serverCertificateHasUnknownRoot,
                                    .serverCertificateNotYetValid, .clientCertificateRejected,
                                    .clientCertificateRequired] {
            XCTAssertEqual(TicketDetailLoadFailure(error: URLError(code)), .secureConnection)
        }
        for code: URLError.Code in [.notConnectedToInternet, .networkConnectionLost, .timedOut,
                                    .cannotConnectToHost, .cannotFindHost, .dnsLookupFailed] {
            XCTAssertEqual(TicketDetailLoadFailure(error: URLError(code)), .connection)
        }
        XCTAssertEqual(TicketDetailLoadFailure(error: NSError(domain: "Decode", code: 1)), .unavailable)
    }
}
