import XCTest
@testable import PusulaGuide

final class GuideReadOnlyGateTests: XCTestCase {
    func testEveryMutationIsRejectedDuringTourAndRestoredAfterExit() throws {
        let gate = GuideReadOnlyGate()
        let lease = gate.begin()
        XCTAssertTrue(gate.isActive)
        for method in ["POST", "PUT", "PATCH", "DELETE", "post", "OPTIONS", "UNKNOWN"] {
            XCTAssertThrowsError(try gate.validate(method: method))
        }
        XCTAssertNoThrow(try gate.validate(method: "GET"))
        XCTAssertNoThrow(try gate.validate(method: "HEAD"))
        gate.end(lease)
        XCTAssertFalse(gate.isActive)
        XCTAssertNoThrow(try gate.validate(method: "POST"))
    }
    func testStaleAndDuplicateCleanupCannotUnlockAnotherTour() {
        let gate = GuideReadOnlyGate(), first = gate.begin(), second = gate.begin()
        gate.end(first); gate.end(first); gate.end(UUID())
        XCTAssertFalse(gate.allows(method: "DELETE"))
        gate.end(second)
        XCTAssertTrue(gate.allows(method: "DELETE"))
    }
    func testTicketAndCustomerContextsAreRoleScoped() {
        XCTAssertTrue(GuideScreen.collection.needsTicket(role: "COMPANY_ADMIN"))
        XCTAssertTrue(GuideScreen.customerContact.needsTicket(role: "TECHNICIAN"))
        XCTAssertFalse(GuideScreen.customerContact.needsCustomer(role: "TECHNICIAN"))
        XCTAssertTrue(GuideScreen.customerContact.needsCustomer(role: "COMPANY_ADMIN"))
        XCTAssertFalse(GuideScreen.ticketCreate.needsTicket(role: "COMPANY_ADMIN"))
    }
}
