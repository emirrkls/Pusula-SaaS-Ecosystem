import XCTest
@testable import PusulaBilling

final class SubscriptionProductCatalogTests: XCTestCase {
    func testIdentifiersMatchTheFourAppStoreConnectSubscriptions() {
        XCTAssertEqual(SubscriptionProductCatalog.identifiers, [
            "com.pusula.usta", "com.pusula.usta.yearly",
            "com.pusula.patron", "com.pusula.patron.yearly"
        ])
    }

    func testEmptyResponseIsUnavailableNotSuccessful() {
        XCTAssertEqual(SubscriptionProductCatalog.availability(returnedIDs: []), .empty)
    }

    func testUnknownProductsDoNotMakeTheCatalogAvailable() {
        XCTAssertEqual(SubscriptionProductCatalog.availability(returnedIDs: ["com.other.product"]), .empty)
    }

    func testMonthlyOnlyResponseReportsBothYearlyProducts() {
        XCTAssertEqual(
            SubscriptionProductCatalog.availability(returnedIDs: [
                SubscriptionProductCatalog.ustaMonthly, SubscriptionProductCatalog.patronMonthly
            ]),
            .partial(missing: ["com.pusula.patron.yearly", "com.pusula.usta.yearly"])
        )
    }

    func testSingleProductDoesNotHideMissingPlans() {
        XCTAssertEqual(
            SubscriptionProductCatalog.availability(returnedIDs: [SubscriptionProductCatalog.ustaMonthly]),
            .partial(missing: ["com.pusula.patron", "com.pusula.patron.yearly", "com.pusula.usta.yearly"])
        )
    }

    func testCompleteResponseIncludingUnknownExtrasIsComplete() {
        XCTAssertEqual(
            SubscriptionProductCatalog.availability(
                returnedIDs: SubscriptionProductCatalog.identifiers.union(["com.other.product"])
            ), .complete
        )
    }
}
