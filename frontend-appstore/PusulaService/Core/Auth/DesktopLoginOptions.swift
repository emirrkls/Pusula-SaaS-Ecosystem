import Foundation

/// Legacy API metadata for an optional desktop credential, never a condition
/// for completing Apple/Google authentication or restoring a mobile session.
struct DesktopLoginOptions: Decodable, Equatable {
    let requiresPasswordSetup: Bool?
    let loginUsername: String?

    static let unavailable = DesktopLoginOptions(requiresPasswordSetup: nil, loginUsername: nil)

    // Fail closed when an older/partial response omits the capability.
    var canSetPassword: Bool { requiresPasswordSetup == true }

    var afterSettingPassword: DesktopLoginOptions {
        DesktopLoginOptions(requiresPasswordSetup: false, loginUsername: loginUsername)
    }
}
