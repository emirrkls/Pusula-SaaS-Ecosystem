import Foundation
import XCTest
@testable import PusulaBilling

final class PurchaseAccountPolicyTests: XCTestCase {
    func testOnlyMatchingAccountCanProcessBoundPurchase() {
        let owner = UUID()
        XCTAssertTrue(PurchaseAccountPolicy.canProcess(transactionAccount: owner, currentAccount: owner, explicitRestore: false))
        XCTAssertFalse(PurchaseAccountPolicy.canProcess(transactionAccount: owner, currentAccount: UUID(), explicitRestore: true))
    }

    func testLegacyTransactionsRequireExplicitRestoreAndBackendOwnershipCheck() {
        XCTAssertFalse(PurchaseAccountPolicy.canProcess(transactionAccount: nil, currentAccount: UUID(), explicitRestore: false))
        XCTAssertTrue(PurchaseAccountPolicy.canProcess(transactionAccount: nil, currentAccount: UUID(), explicitRestore: true))
    }

    func testMissingServerAccountBindingBlocksPurchasesAndRestore() {
        XCTAssertFalse(PurchaseAccountPolicy.canProcess(transactionAccount: UUID(), currentAccount: nil, explicitRestore: false))
        XCTAssertFalse(PurchaseAccountPolicy.canProcess(transactionAccount: nil, currentAccount: nil, explicitRestore: true))
    }
}
