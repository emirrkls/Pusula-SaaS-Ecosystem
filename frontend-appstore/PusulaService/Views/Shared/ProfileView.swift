import SwiftUI

struct ProfileView: View {
    @ObservedObject private var session = SessionManager.shared
    @State private var showDeleteAlert = false
    @State private var showPlanUpgrade = false
    @State private var isDeletingAccount = false
    @State private var deleteError: String?
    @State private var showDesktopPasswordSetup = false
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                heroCard
                    .onboardingTarget(.profileContent)
                
                sectionCard("Hesap Bilgileri") {
                    infoRow("Ad Soyad", value: session.fullName)
                    infoRow("Rol", value: roleLabel(session.role))
                    infoRow("Paket", value: session.planType)
                }

                if session.canSetDesktopPassword {
                    Button(action: { showDesktopPasswordSetup = true }) {
                        Label("auth.desktop_password.action", systemImage: "desktopcomputer")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                    }
                    .buttonStyle(.bordered)
                    .accessibilityIdentifier("account.desktop-password.open")
                }
                
                sectionCard("Şirket") {
                    infoRow("Firma", value: session.companyName ?? "-")
                    if session.planType != "CIRAK", let days = session.trialDaysRemaining {
                        infoRow("Deneme Süresi", value: "\(days) gün kaldı")
                    }
                }

                PusulaAppearancePicker()
                    .pusulaCard()

                NavigationLink { ConnectionDiagnosticsView() } label: {
                    Label("diagnostics.title", systemImage: "network")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.bordered)
                .accessibilityIdentifier("account.diagnostics.open")

                Button {
                    OnboardingManager.shared.restartRoleTour(
                        role: session.role,
                        accountIdentifier: session.onboardingAccountIdentifier
                    )
                } label: {
                    Label("Uygulama Turunu Tekrar Başlat", systemImage: "questionmark.circle")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.bordered)
                
                if session.isAdmin {
                    Button(action: { showPlanUpgrade = true }) {
                        Label("Paket Yükselt", systemImage: "arrow.up.circle.fill")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.orange)
                }

                // Apple subscriptions may outlive a plan change or a role change.
                Group {
                    Button(action: { StoreKitManager.shared.manageSubscriptions() }) {
                        Label("account.manage_apple_subscription", systemImage: "creditcard")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                    }
                    .buttonStyle(.bordered)
                }

                HStack(spacing: 20) {
                    Link("Gizlilik Politikası", destination: AppLinks.privacyPolicy)
                    Link("Kullanım Koşulları", destination: AppLinks.termsOfUse)
                }
                .font(.caption.weight(.medium))
                
                Button(role: .destructive, action: { session.logout() }) {
                    Label("Çıkış Yap", systemImage: "rectangle.portrait.and.arrow.right")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.bordered)
                
                Button(role: .destructive, action: { showDeleteAlert = true }) {
                    HStack {
                        if isDeletingAccount { ProgressView() }
                        Label(isDeletingAccount ? "Hesap Siliniyor…" : "Hesabımı Sil", systemImage: "trash")
                    }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.bordered)
                .disabled(isDeletingAccount)
            }
            .padding()
        }
        .background(PusulaTheme.page)
        .navigationTitle("Hesap")
        .sheet(isPresented: $showPlanUpgrade) {
            NavigationStack { PlanUpgradeView() }
        }
        .sheet(isPresented: $showDesktopPasswordSetup) {
            DesktopPasswordSetupView(
                username: session.desktopLoginUsername,
                savePassword: { password in try await session.setDesktopPassword(password) },
                onComplete: { showDesktopPasswordSetup = false },
                onCancel: { showDesktopPasswordSetup = false }
            )
        }
        .alert("Hesabı Sil", isPresented: $showDeleteAlert) {
            Button("İptal", role: .cancel) { }
            Button("Sil", role: .destructive) {
                deleteAccount()
            }
        } message: {
            Text(deleteConfirmationMessage)
        }
        .alert("Hesap Silinemedi", isPresented: Binding(
            get: { deleteError != nil },
            set: { if !$0 { deleteError = nil } }
        )) {
            Button("Tamam", role: .cancel) { deleteError = nil }
        } message: {
            Text(deleteError ?? "Bilinmeyen bir hata oluştu.")
        }
    }

    private var deleteConfirmationMessage: String {
        String(localized: "account.delete_explanation")
    }
    
    private var heroCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Hesap")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(session.fullName.isEmpty ? "Kullanıcı" : session.fullName)
                .font(.title2.weight(.bold))
            Text("\(roleLabel(session.role)) · \(session.companyName ?? "Pusula Servis")")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text("\(session.planType) Plan")
                .font(.caption.weight(.semibold))
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .foregroundStyle(PusulaTheme.accentStrong)
                .background(PusulaTheme.accent.opacity(0.10))
                .clipShape(Capsule())
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .pusulaCard()
    }
    
    private func infoRow(_ title: String, value: String) -> some View {
        HStack {
            Text(title).foregroundStyle(.secondary)
            Spacer()
            Text(value).font(.subheadline.weight(.medium))
        }
    }
    
    private func sectionCard<Content: View>(_ title: String, @ViewBuilder content: () -> Content) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            Text(title).font(.subheadline.weight(.semibold))
            VStack(spacing: 10) { content() }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .pusulaCard()
    }
    
    private func roleLabel(_ role: String) -> String {
        switch role {
        case "TECHNICIAN": return "Teknisyen"
        case "COMPANY_ADMIN": return "Yönetici"
        case "SUPER_ADMIN": return "Süper Admin"
        default: return role
        }
    }

    private func deleteAccount() {
        guard !isDeletingAccount else { return }
        isDeletingAccount = true
        deleteError = nil

        Task {
            do {
                try await session.deleteAccount()
                await MainActor.run { isDeletingAccount = false }
            } catch {
                await MainActor.run {
                    deleteError = error.localizedDescription
                    isDeletingAccount = false
                }
            }
        }
    }
}
