import Foundation

enum PurchaseAccountPolicy {
    static func canProcess(transactionAccount: UUID?, currentAccount: UUID?, explicitRestore: Bool) -> Bool {
        guard let currentAccount else { return false }
        if let transactionAccount { return transactionAccount == currentAccount }
        // Legacy ownership must be established by the server, never inferred on-device.
        return explicitRestore
    }
}
