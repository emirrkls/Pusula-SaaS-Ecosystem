import Foundation
import XCTest
@testable import PusulaRouting

final class SessionRecoveryTests: XCTestCase {
    func testConnectivityAndServerFailuresPreserveCredentials() {
        for status in [nil, 408, 429, 500, 502, 503] as [Int?] {
            XCTAssertFalse(SessionRestorePolicy.shouldDiscardCredentials(httpStatus: status))
        }
    }

    func testRejectedAuthenticationOrUnsupportedRoleRequiresLogin() {
        XCTAssertTrue(SessionRestorePolicy.shouldDiscardCredentials(httpStatus: 401))
        XCTAssertTrue(SessionRestorePolicy.shouldDiscardCredentials(httpStatus: 403))
        XCTAssertTrue(SessionRestorePolicy.shouldDiscardCredentials(httpStatus: nil, unsupportedRole: true))
    }

    func testSessionRevisionInvalidatesEvenAReusedToken() {
        let first = AuthenticatedSessionSnapshot(token: "test-token", companyId: 10, revision: UUID())
        XCTAssertNotEqual(first, AuthenticatedSessionSnapshot(token: first.token, companyId: 10, revision: UUID()))
        XCTAssertNotEqual(first, AuthenticatedSessionSnapshot(token: first.token, companyId: 20, revision: first.revision))
        XCTAssertEqual(first, first)
    }
}
