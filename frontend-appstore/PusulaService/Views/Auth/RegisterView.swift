import SwiftUI

struct RegisterView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var fullName = ""
    @State private var email = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var isLoading = false
    @State private var errorMessage: String?

    private let session = SessionManager.shared

    private var passwordsMatch: Bool {
        password == confirmPassword && !password.isEmpty
    }

    private var isFormValid: Bool {
        !fullName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && email.contains("@")
            && password.count >= 6
            && passwordsMatch
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 26) {
                    header
                    form
                    trialNote
                    legalNote
                }
                .frame(maxWidth: 520, alignment: .leading)
                .padding(.horizontal, PusulaTheme.pagePadding)
                .padding(.top, 16)
                .padding(.bottom, 32)
                .frame(maxWidth: .infinity)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(PusulaTheme.page.ignoresSafeArea())
            .navigationTitle("Hesap Oluştur")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark")
                    }
                    .accessibilityLabel("Kapat")
                }
            }
        }
    }

    private var header: some View {
        HStack(alignment: .center, spacing: 14) {
            PusulaBrandMark(size: 52)
            VStack(alignment: .leading, spacing: 4) {
                Text("İşletmenizi oluşturun")
                    .font(.title2.weight(.bold))
                Text("Çırak planı ile başlayın")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var form: some View {
        VStack(spacing: 18) {
            PusulaTextField(
                title: "Ad soyad",
                icon: "person",
                text: $fullName,
                contentType: .name
            )

            PusulaTextField(
                title: "E-posta",
                icon: "envelope",
                text: $email,
                contentType: .emailAddress,
                keyboardType: .emailAddress,
                textInputAutocapitalization: .never
            )

            PusulaTextField(
                title: "Şifre",
                icon: "lock",
                text: $password,
                contentType: .newPassword,
                textInputAutocapitalization: .never,
                isSecure: true
            )

            PusulaTextField(
                title: "Şifre tekrar",
                icon: "lock.rotation",
                text: $confirmPassword,
                contentType: .newPassword,
                textInputAutocapitalization: .never,
                isSecure: true,
                isInvalid: !confirmPassword.isEmpty && !passwordsMatch,
                submitLabel: .done,
                onSubmit: { if isFormValid { handleRegister() } }
            )

            if !confirmPassword.isEmpty && !passwordsMatch {
                PusulaInlineMessage(text: "Şifreler eşleşmiyor.")
            }

            if let errorMessage {
                PusulaInlineMessage(text: errorMessage)
            }

            PusulaPrimaryButton(
                title: "Hesap Oluştur",
                icon: "arrow.right",
                isLoading: isLoading,
                isDisabled: !isFormValid,
                action: handleRegister
            )
        }
    }

    private var trialNote: some View {
        HStack(spacing: 12) {
            Image(systemName: "calendar.badge.clock")
                .foregroundStyle(PusulaTheme.amber)
            VStack(alignment: .leading, spacing: 2) {
                Text("14 gün ücretsiz")
                    .font(.subheadline.weight(.semibold))
                Text("Çırak planı")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
            Spacer()
        }
        .pusulaCard(padding: 14)
    }

    private var legalNote: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Hesap oluşturarak Kullanım Koşulları'nı ve Gizlilik Politikası'nı kabul etmiş olursunuz.")
                .font(.caption)
                .foregroundStyle(.secondary)
            HStack(spacing: 18) {
                Link("Gizlilik", destination: AppLinks.privacyPolicy)
                Link("Kullanım Koşulları", destination: AppLinks.termsOfUse)
            }
            .font(.caption.weight(.semibold))
            .foregroundStyle(PusulaTheme.accent)
        }
    }

    private func handleRegister() {
        guard isFormValid else { return }
        isLoading = true
        errorMessage = nil

        Task {
            do {
                let response = try await AuthService.registerIndividual(
                    email: email.trimmingCharacters(in: .whitespacesAndNewlines),
                    password: password,
                    fullName: fullName.trimmingCharacters(in: .whitespacesAndNewlines)
                )
                await MainActor.run {
                    session.configure(from: response)
                    isLoading = false
                    dismiss()
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isLoading = false
                }
            }
        }
    }
}

struct RegisterView_Previews: PreviewProvider {
    static var previews: some View {
        RegisterView()
    }
}
