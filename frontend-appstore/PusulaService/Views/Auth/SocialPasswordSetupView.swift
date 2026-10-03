import SwiftUI

/// Social sign-up must not leave a random, unknown password for financial confirmations.
struct SocialPasswordSetupView: View {
    @State private var password = ""
    @State private var confirmation = ""
    @State private var isSaving = false
    @State private var errorMessage: String?
    let username: String?
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
                    Text("İşlem şifrenizi belirleyin").font(.title2.weight(.semibold))
                    Text("Google veya Apple ile giriş yapmaya devam edebilirsiniz. Bu şifreyi finansal işlemleri onaylarken ve masaüstü uygulamasında giriş yaparken kullanacaksınız.")
                        .foregroundStyle(.secondary)
                    if let username {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Masaüstü kullanıcı adınız").font(.caption).foregroundStyle(.secondary)
                            Text(username).textSelection(.enabled)
                        }
                        .pusulaCard()
                    }
                    PusulaTextField(title: "Şifre", icon: "lock", text: $password,
                                    contentType: .newPassword, textInputAutocapitalization: .never, isSecure: true)
                    PusulaTextField(title: "Şifre tekrar", icon: "lock.rotation", text: $confirmation,
                                    contentType: .newPassword, textInputAutocapitalization: .never, isSecure: true)
                    Text("En az 8 karakter, harf ve rakam içermelidir.").font(.caption).foregroundStyle(.secondary)
                    if let errorMessage { PusulaInlineMessage(text: errorMessage) }
                    PusulaPrimaryButton(title: "Kaydet ve Devam Et", icon: "checkmark", isLoading: isSaving,
                                        isDisabled: !isValid || isSaving, action: save)
                }
                .padding(PusulaTheme.pagePadding)
                .frame(maxWidth: 520)
                .frame(maxWidth: .infinity)
            }
            .background(PusulaTheme.page.ignoresSafeArea())
            .navigationTitle("Hesap Güvenliği")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Vazgeç", action: onCancel).disabled(isSaving) } }
        }
    }
    private func save() {
        guard isValid, !isSaving else { return }
        isSaving = true
        Task { @MainActor in
            defer { isSaving = false }
            do { try await AuthService.setInitialSocialPassword(password); onComplete() }
            catch { errorMessage = error.localizedDescription }
        }
    }
}
