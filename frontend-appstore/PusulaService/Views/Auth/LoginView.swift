import SwiftUI

struct LoginView: View {
    @State private var selectedTab = 0
    @State private var username = ""
    @State private var password = ""
    @State private var orgCode = ""
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var showRegister = false

    private let session = SessionManager.shared

    private var isCorporate: Bool { selectedTab == 1 }
    private var isFormValid: Bool {
        !username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
            && !password.isEmpty
            && (!isCorporate || !orgCode.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 28) {
                    brandHeader
                    loginMode
                    loginForm
                    accountActions
                }
                .frame(maxWidth: 520, alignment: .leading)
                .padding(.horizontal, PusulaTheme.pagePadding)
                .padding(.top, 24)
                .padding(.bottom, 32)
                .frame(maxWidth: .infinity)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(PusulaTheme.page.ignoresSafeArea())
            .animation(.easeInOut(duration: 0.2), value: selectedTab)
            .sheet(isPresented: $showRegister) {
                RegisterView()
            }
            .onAppear {
                guard errorMessage == nil, let message = session.sessionMessage else { return }
                errorMessage = message
                session.sessionMessage = nil
            }
        }
    }

    private var brandHeader: some View {
        VStack(alignment: .leading, spacing: 22) {
            HStack(spacing: 12) {
                PusulaBrandMark(size: 44)
                VStack(alignment: .leading, spacing: 1) {
                    Text("PUSULA")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(PusulaTheme.accent)
                    Text("Servis")
                        .font(.title3.weight(.semibold))
                }
            }

            VStack(alignment: .leading, spacing: 7) {
                Text("Tekrar hoş geldiniz")
                    .font(.largeTitle.weight(.bold))
                Text(isCorporate ? "Şirket hesabınızla devam edin." : "Hesabınızla devam edin.")
                    .font(.subheadline)
                    .foregroundStyle(.secondary)
            }
        }
    }

    private var loginMode: some View {
        Picker("Giriş türü", selection: $selectedTab) {
            Text("Bireysel").tag(0)
            Text("Kurumsal").tag(1)
        }
        .pickerStyle(.segmented)
        .onChange(of: selectedTab) { _, _ in
            errorMessage = nil
        }
    }

    private var loginForm: some View {
        VStack(spacing: 18) {
            if isCorporate {
                PusulaTextField(
                    title: "Kurum kodu",
                    icon: "building.2",
                    text: $orgCode,
                    contentType: .organizationName,
                    textInputAutocapitalization: .characters
                )
            }

            PusulaTextField(
                title: isCorporate ? "Kullanıcı adı" : "E-posta veya kullanıcı adı",
                icon: "person",
                text: $username,
                contentType: .username,
                keyboardType: isCorporate ? .default : .emailAddress,
                textInputAutocapitalization: .never
            )

            PusulaTextField(
                title: "Şifre",
                icon: "lock",
                text: $password,
                contentType: .password,
                textInputAutocapitalization: .never,
                isSecure: true,
                submitLabel: .go,
                onSubmit: { if isFormValid { handleLogin() } }
            )

            if let errorMessage {
                PusulaInlineMessage(text: errorMessage)
                    .transition(.opacity.combined(with: .move(edge: .top)))
            }

            PusulaPrimaryButton(
                title: "Giriş Yap",
                icon: "arrow.right",
                isLoading: isLoading,
                isDisabled: !isFormValid,
                action: handleLogin
            )
        }
    }

    @ViewBuilder
    private var accountActions: some View {
        if !isCorporate {
            HStack(spacing: 10) {
                Text("Yeni misiniz?")
                    .foregroundStyle(.secondary)
                Button("Hesap Oluştur") { showRegister = true }
                    .fontWeight(.semibold)
                    .foregroundStyle(PusulaTheme.accent)
            }
            .font(.subheadline)
            .frame(maxWidth: .infinity)
        }
    }

    private func handleLogin() {
        guard isFormValid else { return }
        isLoading = true
        errorMessage = nil

        Task {
            do {
                let response: AuthResponse
                if isCorporate {
                    response = try await AuthService.loginCorporate(
                        orgCode: orgCode.trimmingCharacters(in: .whitespacesAndNewlines),
                        username: username.trimmingCharacters(in: .whitespacesAndNewlines),
                        password: password
                    )
                } else {
                    response = try await AuthService.login(
                        username: username.trimmingCharacters(in: .whitespacesAndNewlines),
                        password: password
                    )
                }

                await MainActor.run {
                    session.configure(from: response)
                    isLoading = false
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

struct LoginView_Previews: PreviewProvider {
    static var previews: some View {
        LoginView()
    }
}
