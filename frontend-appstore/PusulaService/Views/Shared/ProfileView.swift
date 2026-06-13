import SwiftUI

struct ProfileView: View {
    let session = SessionManager.shared
    @State private var showDeleteAlert = false
    @State private var showPlanUpgrade = false
    
    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                heroCard
                
                sectionCard("Hesap Bilgileri") {
                    infoRow("Ad Soyad", value: session.fullName)
                    infoRow("Rol", value: roleLabel(session.role))
                    infoRow("Paket", value: session.planType)
                }
                
                sectionCard("Şirket") {
                    infoRow("Firma", value: session.companyName ?? "-")
                    if let days = session.trialDaysRemaining {
                        infoRow("Deneme Süresi", value: "\(days) gün kaldı")
                    }
                }
                
                if session.isAdmin || session.isTechnician {
                    Button(action: { showPlanUpgrade = true }) {
                        Label("Paket Yükselt", systemImage: "arrow.up.circle.fill")
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 14)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(.orange)
                }
                
                Button(role: .destructive, action: { session.logout() }) {
                    Label("Çıkış Yap", systemImage: "rectangle.portrait.and.arrow.right")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.bordered)
                
                Button(role: .destructive, action: { showDeleteAlert = true }) {
                    Label("Hesabımı Sil", systemImage: "trash")
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                }
                .buttonStyle(.bordered)
            }
            .padding()
        }
        .navigationTitle("Hesap")
        .sheet(isPresented: $showPlanUpgrade) {
            NavigationStack { PlanUpgradeView() }
        }
        .alert("Hesabı Sil", isPresented: $showDeleteAlert) {
            Button("İptal", role: .cancel) { }
            Button("Sil", role: .destructive) {
                Task { try? await session.deleteAccount() }
            }
        } message: {
            Text("Hesabınızı ve tüm verilerinizi kalıcı olarak silmek istediğinizden emin misiniz?")
        }
    }
    
    private var heroCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Hesap")
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
            Text(session.fullName.isEmpty ? "Kullanıcı" : session.fullName)
                .font(.title2.weight(.bold))
            Text("\(roleLabel(session.role)) • \(session.companyName ?? "Pusula Servis")")
                .font(.subheadline)
                .foregroundStyle(.secondary)
            Text("\(session.planType) Plan")
                .font(.caption.weight(.semibold))
                .padding(.horizontal, 10)
                .padding(.vertical, 4)
                .background(.cyan.opacity(0.15))
                .clipShape(Capsule())
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding()
        .background(
            LinearGradient(colors: [.cyan.opacity(0.2), .blue.opacity(0.12)], startPoint: .topLeading, endPoint: .bottomTrailing)
        )
        .clipShape(RoundedRectangle(cornerRadius: 16))
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
        .padding()
        .background(.regularMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
    
    private func roleLabel(_ role: String) -> String {
        switch role {
        case "TECHNICIAN": return "Teknisyen"
        case "COMPANY_ADMIN": return "Yönetici"
        case "SUPER_ADMIN": return "Süper Admin"
        default: return role
        }
    }
}
