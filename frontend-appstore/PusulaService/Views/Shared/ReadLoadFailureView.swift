import SwiftUI

/// Inline feedback for a failed read, separate from mutation-error alerts.
struct ReadLoadFailureView: View {
    let message: String
    var identifier: String = "read.load"
    let retry: () -> Void

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("common.load_failed", systemImage: "exclamationmark.triangle")
                .font(.subheadline.weight(.medium))
                .foregroundStyle(.orange)
            Text(verbatim: message)
                .font(.caption)
                .foregroundStyle(.secondary)
            Button(action: retry) {
                Label("common.retry", systemImage: "arrow.clockwise")
            }
            .buttonStyle(.bordered)
            .accessibilityIdentifier("\(identifier).retry")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .accessibilityIdentifier("\(identifier).error")
    }
}
