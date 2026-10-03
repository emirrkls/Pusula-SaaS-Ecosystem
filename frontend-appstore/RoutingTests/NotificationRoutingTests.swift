import Foundation
import XCTest
@testable import PusulaRouting

final class NotificationRoutingTests: XCTestCase {
    func testAssignmentPayloadAcceptsIntegerTicketID() {
        XCTAssertEqual(PushNavigationDestination.parse(["type": "TICKET_ASSIGNED", "ticketId": 75]), .ticket(75))
    }

    func testTicketIDAcceptsStringAndNSNumber() {
        XCTAssertEqual(PushNavigationDestination.parse(["ticketId": "888"]), .ticket(888))
        XCTAssertEqual(PushNavigationDestination.parse(["ticketId": NSNumber(value: 888)]), .ticket(888))
    }

    func testInvalidIDsAreRejectedWithoutTruncation() {
        let invalid: [Any] = [0, -1, true, NSNumber(value: false), 75.5, "75.5", "not-a-number", "9223372036854775808", NSNull()]
        for value in invalid {
            XCTAssertNil(PushNavigationDestination.parse(["ticketId": value]), "Unexpected accepted ID: \(value)")
        }
    }

    func testOnlyTicketReferencesUseTicketFallback() {
        XCTAssertEqual(PushNavigationDestination.parse(["referenceType": "TICKET", "referenceId": "75"]), .ticket(75))
        XCTAssertNil(PushNavigationDestination.parse(["referenceType": "CUSTOMER", "referenceId": 75]))
        XCTAssertNil(PushNavigationDestination.parse(["referenceType": "FINANCE", "referenceId": 75]))
        XCTAssertNil(PushNavigationDestination.parse(["referenceId": 75]))
    }

    func testNetworkReferenceIsNotMistakenForATicket() {
        XCTAssertEqual(
            PushNavigationDestination.parse(["referenceType": "NETWORK_WORK_ORDER", "referenceId": 31, "ticketId": 75]),
            .network(referenceType: "NETWORK_WORK_ORDER", referenceId: 31)
        )
        XCTAssertNil(PushNavigationDestination.parse(["referenceType": "NETWORK_WORK_ORDER", "referenceId": -1, "ticketId": 75]))
    }

    func testColdLaunchRequestSurvivesUntilAcknowledged() throws {
        var queue = TicketNavigationQueue()
        queue.enqueue(ticketId: 75, companyId: nil)
        let request = try XCTUnwrap(queue.pending)
        XCTAssertTrue(queue.matches(request, companyId: 10))
        XCTAssertEqual(queue.pending?.ticketId, 75)
        queue.acknowledge(request)
        XCTAssertNil(queue.pending)
    }

    func testReadinessRequiresEverySafetyCondition() {
        for mask in 0..<32 {
            let readiness = TicketPresentationReadiness(
                isAuthenticated: mask & 1 != 0,
                isRestoringSession: mask & 2 != 0,
                isSceneActive: mask & 4 != 0,
                isScreenVisible: mask & 8 != 0,
                isPresentationBlocked: mask & 16 != 0
            )
            XCTAssertEqual(readiness.canPresent, mask == 13, "Incorrect readiness for mask \(mask)")
        }
    }

    func testLatestTapWinsAndOldFetchCannotConsumeIt() throws {
        var queue = TicketNavigationQueue()
        queue.enqueue(ticketId: 75, companyId: 10)
        let old = try XCTUnwrap(queue.pending)
        queue.enqueue(ticketId: 76, companyId: 10)
        let latest = try XCTUnwrap(queue.pending)
        XCTAssertFalse(queue.matches(old, companyId: 10))
        queue.acknowledge(old)
        XCTAssertEqual(queue.pending, latest)
    }

    func testRepeatedTapCoalescesWhilePending() throws {
        var queue = TicketNavigationQueue()
        queue.enqueue(ticketId: 75, companyId: 10)
        let first = try XCTUnwrap(queue.pending)
        queue.enqueue(ticketId: 75, companyId: 10)
        XCTAssertEqual(queue.pending, first)
    }

    func testSameTicketCanBeOpenedAgainAfterDismissal() throws {
        var queue = TicketNavigationQueue()
        queue.enqueue(ticketId: 75, companyId: 10)
        let first = try XCTUnwrap(queue.pending)
        queue.acknowledge(first)
        queue.enqueue(ticketId: 75, companyId: 10)
        XCTAssertNotEqual(queue.pending?.id, first.id)
    }

    func testTenantChangeInvalidatesAnInFlightRequest() throws {
        var queue = TicketNavigationQueue()
        queue.enqueue(ticketId: 75, companyId: 10)
        let request = try XCTUnwrap(queue.pending)
        XCTAssertFalse(queue.matches(request, companyId: 11))
        queue.enqueue(ticketId: 75, companyId: 11)
        XCTAssertFalse(queue.matches(request, companyId: 11))
    }

    func testInvalidEnqueueDoesNotOverwriteAValidTap() throws {
        var queue = TicketNavigationQueue()
        queue.enqueue(ticketId: 75, companyId: 10)
        let request = try XCTUnwrap(queue.pending)
        queue.enqueue(ticketId: -1, companyId: 10)
        XCTAssertEqual(queue.pending, request)
    }

    func testLogoutClearsQueuedRequest() {
        var queue = TicketNavigationQueue()
        queue.enqueue(ticketId: 75, companyId: 10)
        queue.reset()
        XCTAssertNil(queue.pending)
    }

    func testTaskTriggerChangesWhenSceneBecomesActive() {
        let request = TicketNavigationRequest(id: UUID(), ticketId: 75, companyId: 10)
        let inactive = TicketPresentationReadiness(isAuthenticated: true, isRestoringSession: false,
            isSceneActive: false, isScreenVisible: true, isPresentationBlocked: false)
        let active = TicketPresentationReadiness(isAuthenticated: true, isRestoringSession: false,
            isSceneActive: true, isScreenVisible: true, isPresentationBlocked: false)
        XCTAssertNotEqual(PendingTicketTrigger(request: request, readiness: inactive, selectedTicketId: nil),
                          PendingTicketTrigger(request: request, readiness: active, selectedTicketId: nil))
    }

    func testOldUnauthorizedResponseCannotCloseANewSession() {
        XCTAssertFalse(SessionResponsePolicy.belongsToCurrentSession(requestToken: "old-test-token", currentToken: "new-test-token"))
        XCTAssertTrue(SessionResponsePolicy.belongsToCurrentSession(requestToken: "same-test-token", currentToken: "same-test-token"))
    }

    func testMissingTokenCannotInvalidateALoggedInSession() {
        XCTAssertFalse(SessionResponsePolicy.belongsToCurrentSession(requestToken: nil, currentToken: "new-test-token"))
        XCTAssertFalse(SessionResponsePolicy.belongsToCurrentSession(requestToken: "", currentToken: ""))
        XCTAssertFalse(SessionResponsePolicy.belongsToCurrentSession(requestToken: "old-test-token", currentToken: nil))
    }
}
