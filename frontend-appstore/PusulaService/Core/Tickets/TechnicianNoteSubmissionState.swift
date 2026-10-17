import Foundation

/// In-memory intent for one note. Never mint a new key after an uncertain write.
/// No token or note content is written to diagnostics or persistent preferences.
struct TechnicianNoteSubmissionState {
    struct Intent: Equatable {
        let ticketID: Int
        let sessionRevision: UUID
        let content: String
        let important: Bool
    }

    private(set) var requestID = UUID()
    private(set) var intent: Intent?
    private(set) var isSubmitting = false
    private(set) var showsRetry = false
    var canEdit: Bool { intent == nil && !isSubmitting }

    mutating func begin(ticketID: Int, sessionRevision: UUID, content: String, important: Bool) -> Intent? {
        guard !isSubmitting else { return nil }
        if let intent {
            guard intent.ticketID == ticketID, intent.sessionRevision == sessionRevision else { return nil }
        } else {
            let normalized = content.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !normalized.isEmpty else { return nil }
            intent = Intent(ticketID: ticketID, sessionRevision: sessionRevision,
                            content: normalized, important: important)
        }
        isSubmitting = true
        showsRetry = false
        return intent
    }

    mutating func succeed() {
        guard isSubmitting else { return }
        self = Self() // A confirmed success permits a genuinely new note.
    }

    mutating func fail(allowsCorrection: Bool) {
        guard isSubmitting else { return }
        if allowsCorrection {
            self = Self()
        } else {
            isSubmitting = false
            showsRetry = true
        }
    }
}
