import Foundation

/// Decimal arithmetic mirrors ServicePricing on the server. Discounts reduce sales, not cash.
enum CollectionPricing {
    enum DiscountType: String, CaseIterable { case amount = "AMOUNT", percent = "PERCENT" }
    struct Totals {
        let subtotal: Decimal
        let discount: Decimal
        let net: Decimal
        let customerShare: Decimal
        let customerDebt: Decimal
    }
    struct Invalid: LocalizedError {
        let message: String
        var errorDescription: String? { message }
    }
    static func round(_ value: Decimal) -> Decimal {
        var original = value, rounded = Decimal()
        NSDecimalRound(&rounded, &original, 2, .plain)
        return rounded
    }
    static func parse(_ text: String) throws -> Decimal {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty { return 0 }
        var normalized = trimmed
        if trimmed.contains(",") {
            guard trimmed.range(of: "^[0-9]+(\\.[0-9]{3})*(,[0-9]{0,2})?$", options: .regularExpression) != nil else {
                throw Invalid(message: "Tutarı en fazla iki ondalıkla girin. Örn. 1.500,00")
            }
            normalized = trimmed.replacingOccurrences(of: ".", with: "").replacingOccurrences(of: ",", with: ".")
        }
        guard normalized.range(of: "^[0-9]+(\\.[0-9]{0,2})?$", options: .regularExpression) != nil,
              let value = Decimal(string: normalized, locale: Locale(identifier: "en_US_POSIX")),
              value <= Decimal(string: "999999999999.99")! else {
            throw Invalid(message: "Geçerli, negatif olmayan bir tutar girin (en fazla iki ondalık).")
        }
        return value
    }
    static func calculate(parts: Decimal, labor: Decimal, discountType: DiscountType,
                          discountValue: Decimal, organization: Decimal, collected: Decimal) throws -> Totals {
        guard parts >= 0, labor >= 0, discountValue >= 0, organization >= 0, collected >= 0 else {
            throw Invalid(message: "Tutarlar negatif olamaz.")
        }
        let subtotal = round(round(parts) + round(labor))
        guard subtotal <= Decimal(string: "999999999999.99")! else { throw Invalid(message: "Fiş tutarı sınırı aşıldı.") }
        if discountType == .percent && discountValue > 100 { throw Invalid(message: "İskonto yüzdesi 100'ü aşamaz.") }
        let discount = discountType == .percent ? round(subtotal * discountValue / 100) : round(discountValue)
        guard discount <= subtotal else { throw Invalid(message: "İskonto ara toplamı aşamaz.") }
        let net = subtotal - discount
        guard organization <= net else { throw Invalid(message: "Kurum payı net fiş toplamını aşamaz.") }
        let customerShare = net - organization
        guard collected <= customerShare else { throw Invalid(message: "Tahsilat müşteriye düşen tutarı aşamaz.") }
        return Totals(subtotal: subtotal, discount: discount, net: net,
                      customerShare: customerShare, customerDebt: customerShare - collected)
    }
}
