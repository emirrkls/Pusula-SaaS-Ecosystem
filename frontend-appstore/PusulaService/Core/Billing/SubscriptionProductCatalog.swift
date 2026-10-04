/// Store identifiers only. Prices and purchase eligibility always come from StoreKit.
public enum SubscriptionProductCatalog {
    public static let ustaMonthly = "com.pusula.usta"
    public static let ustaYearly = "com.pusula.usta.yearly"
    public static let patronMonthly = "com.pusula.patron"
    public static let patronYearly = "com.pusula.patron.yearly"

    public static let identifiers: Set<String> = [
        ustaMonthly, ustaYearly, patronMonthly, patronYearly
    ]

    public static func availability(returnedIDs: Set<String>) -> SubscriptionCatalogAvailability {
        let available = identifiers.intersection(returnedIDs)
        guard !available.isEmpty else { return .empty }
        let missing = identifiers.subtracting(available).sorted()
        return missing.isEmpty ? .complete : .partial(missing: missing)
    }
}

public enum SubscriptionCatalogAvailability: Equatable, Sendable {
    case empty
    case partial(missing: [String])
    case complete
}
