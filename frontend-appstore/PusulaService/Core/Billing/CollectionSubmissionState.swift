import Foundation

/// A frozen payload is a replay safeguard, not evidence that a request failed.
/// Only an unconfirmed failure offers replay; a confirmed success is terminal.
struct CollectionSubmissionState<Payload> {
    enum Phase: Equatable { case idle, submitting, unconfirmed, succeeded }

    private(set) var phase: Phase = .idle
    private(set) var requestID = UUID()
    private(set) var payload: Payload?

    var isProcessing: Bool { phase == .submitting }
    var canEdit: Bool { phase == .idle }
    var canSubmit: Bool { phase == .idle || phase == .unconfirmed }
    var showsRetry: Bool { phase == .unconfirmed }

    mutating func begin(makePayload: () -> Payload) -> Payload? {
        guard canSubmit else { return nil }
        if payload == nil { payload = makePayload() }
        phase = .submitting
        return payload
    }

    mutating func succeed() {
        guard phase == .submitting else { return }
        phase = .succeeded
    }

    mutating func fail(allowsCorrection: Bool) {
        guard phase == .submitting else { return }
        if allowsCorrection {
            payload = nil
            requestID = UUID()
            phase = .idle
        } else {
            // Timeout, cancellation and lost responses can follow a committed
            // write. Keep exactly the same payload and idempotency key.
            phase = .unconfirmed
        }
    }
}
