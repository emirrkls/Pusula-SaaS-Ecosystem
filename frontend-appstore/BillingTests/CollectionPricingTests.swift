import XCTest
@testable import PusulaBilling

final class CollectionPricingTests: XCTestCase {
    private func d(_ value: String) -> Decimal { Decimal(string: value)! }
    func testDiscountPrecedesPayerAllocation() throws {
        let result = try CollectionPricing.calculate(parts: 3000, labor: 2000, discountType: .amount,
            discountValue: 500, organization: 2000, collected: 1500)
        XCTAssertEqual(result.net, 4500); XCTAssertEqual(result.customerDebt, 1000)
        XCTAssertEqual(result.net, 2000 + 1500 + result.customerDebt)
    }
    func testZeroAndFullDiscount() throws {
        let result = try CollectionPricing.calculate(parts: 500, labor: 0, discountType: .percent,
            discountValue: 100, organization: 0, collected: 0)
        XCTAssertEqual(result.net, 0); XCTAssertEqual(result.customerDebt, 0)
    }
    func testHalfCentAndPercentageRoundingMatchServer() throws {
        let result = try CollectionPricing.calculate(parts: d("0.05"), labor: 0, discountType: .percent,
            discountValue: 10, organization: 0, collected: 0)
        XCTAssertEqual(result.discount, d("0.01")); XCTAssertEqual(result.net, d("0.04"))
        let other = try CollectionPricing.calculate(parts: d("99.99"), labor: 0, discountType: .percent,
            discountValue: d("33.33"), organization: 0, collected: 0)
        XCTAssertEqual(other.discount, d("33.33")); XCTAssertEqual(other.net, d("66.66"))
    }
    func testLocalizedInput() throws {
        XCTAssertEqual(try CollectionPricing.parse("1.500,50"), d("1500.50"))
        XCTAssertEqual(try CollectionPricing.parse("1500.50"), d("1500.50"))
        XCTAssertEqual(try CollectionPricing.parse(""), 0)
    }
    func testMalformedAndNegativeInputIsRejected() {
        for value in ["-1", "NaN", "1,2,3", "1.234", "1.00,50", "1000000000000"] {
            XCTAssertThrowsError(try CollectionPricing.parse(value), value)
        }
    }
    func testAllocationAndDiscountBounds() {
        for (type, value, organization, collected) in [
            (CollectionPricing.DiscountType.amount, d("501"), d("0"), d("0")),
            (.percent, d("101"), d("0"), d("0")),
            (.amount, d("100"), d("401"), d("0")),
            (.amount, d("100"), d("300"), d("101"))] {
            XCTAssertThrowsError(try CollectionPricing.calculate(parts: 500, labor: 0, discountType: type,
                discountValue: value, organization: organization, collected: collected))
        }
    }
}
