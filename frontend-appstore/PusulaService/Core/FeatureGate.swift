import SwiftUI

/// FeatureGate view modifier — hides/disables views based on feature flags.
/// Optionally shows an upgrade prompt for locked features.
struct FeatureGateModifier: ViewModifier {
    let featureKey: String
    let showUpgradeHint: Bool
    let session = SessionManager.shared
    
    var isEnabled: Bool {
        session.isFeatureEnabled(featureKey)
    }
    
    func body(content: Content) -> some View {
        if isEnabled {
            content
        } else if showUpgradeHint {
            content
                .overlay {
                    ZStack {
                        Color.black.opacity(0.5)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                        
                        VStack(spacing: 6) {
                            Image(systemName: "lock.fill")
                                .font(.title3)
                            Text("Paketinizi Yükseltin")
                                .font(.caption.weight(.semibold))
                        }
                        .foregroundColor(.white)
                    }
                }
                .allowsHitTesting(false)
        } else {
            // Completely hide
            EmptyView()
        }
    }
}

extension View {
    /// Gate a view behind a feature flag. Hidden if feature is disabled.
    func featureGated(_ featureKey: String, showUpgradeHint: Bool = false) -> some View {
        modifier(FeatureGateModifier(featureKey: featureKey, showUpgradeHint: showUpgradeHint))
    }
}

/// ReadOnly modifier — disables write actions when subscription is suspended.
struct ReadOnlyModifier: ViewModifier {
    let session = SessionManager.shared
    
    func body(content: Content) -> some View {
        content
            .disabled(session.isReadOnly)
            .overlay {
                if session.isReadOnly {
                    Color.clear // Transparent overlay to block interaction
                }
            }
    }
}

extension View {
    /// Disable the view when the tenant is in read-only mode (subscription expired).
    func readOnlyProtected() -> some View {
        modifier(ReadOnlyModifier())
    }
}
