import SwiftUI

/// User-initiated alternative for desktop sign-in; never part of social login.
struct DesktopPasswordSetupView: View {
    @State private var password = ""
    @State private var confirmation = ""
    @State private var isSaving = false
    @State private var errorMessage: String?
    let username: String?
    let savePassword: (String) async throws -> Void
    let onComplete: () -> Void
    let onCancel: () -> Void

    private var isValid: Bool {
        password.count >= 8 && password.utf8.count <= 72 && password.contains(where: \.isLetter)
            && password.contains(where: \.isNumber) && password == confirmation
    }
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    Text("auth.desktop_password.heading").font(.title2.weight(.semibold))
                    Text("auth.desktop_password.explanation")
                        .foregroundStyle(.secondary)
                    if let username {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("auth.desktop_password.username").font(.caption).foregroundStyle(.secondary)
                            Text(username).textSelection(.enabled)
                        }
                        .pusulaCard()
                    }
                    PusulaTextField(title: String(localized: "auth.desktop_password.password"), icon: "lock", text: $password,
                                    contentType: .newPassword, textInputAutocapitalization: .never, isSecure: true)
                    PusulaTextField(title: String(localized: "auth.desktop_password.confirmation"), icon: "lock.rotation", text: $confirmation,
                                    contentType: .newPassword, textInputAutocapitalization: .never, isSecure: true)
                    Text("auth.desktop_password.requirements").font(.caption).foregroundStyle(.secondary)
                    if let errorMessage { PusulaInlineMessage(text: errorMessage) }
                    PusulaPrimaryButton(title: String(localized: "auth.desktop_password.save"), icon: "checkmark", isLoading: isSaving,
                                        isDisabled: !isValid || isSaving, action: save)
                        .accessibilityIdentifier("account.desktop-password.save")
                }
                .padding(PusulaTheme.pagePadding)
                .frame(maxWidth: 520)
                .frame(maxWidth: .infinity)
            }
            .background(PusulaTheme.page.ignoresSafeArea())
            .pusulaKeyboardDismissal()
            .navigationTitle("auth.desktop_password.title")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("auth.desktop_password.cancel", action: onCancel)
                        .disabled(isSaving)
                        .accessibilityIdentifier("account.desktop-password.cancel")
                }
            }
        }
        .interactiveDismissDisabled(isSaving)
    }
    private func save() {
        PusulaKeyboard.dismiss()
        guard isValid, !isSaving else { return }
        isSaving = true
        Task { @MainActor in
            defer { isSaving = false }
            do { try await savePassword(password); onComplete() }
            catch { errorMessage = error.localizedDescription }
        }
    }
}
