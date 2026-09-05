import SwiftUI

struct AdminNotificationsView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var notifications: [AdminNotificationDTO] = []
    @State private var isLoading = true
    @State private var errorMessage: String?
    let onChanged: () async -> Void

    var body: some View {
        Group {
            if isLoading {
                ProgressView("Bildirimler yükleniyor...")
            } else if notifications.isEmpty {
                ContentUnavailableView("Bildirim yok", systemImage: "bell.slash",
                                       description: Text("Önemli operasyon hareketleri burada görünür."))
            } else {
                List {
                    ForEach(notifications) { item in
                        Button { open(item) } label: { notificationRow(item) }
                            .buttonStyle(.plain)
                            .listRowBackground(PusulaTheme.page)
                            .swipeActions(edge: .trailing, allowsFullSwipe: true) {
                                if !item.read {
                                    Button("Okundu") { Task { await markRead(item) } }
                                        .tint(PusulaTheme.accent)
                                }
                            }
                    }
                }
                .listStyle(.plain)
                .refreshable { await load() }
            }
        }
        .background(PusulaTheme.page)
        .navigationTitle("Bildirimler")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            ToolbarItem(placement: .topBarLeading) { Button("Kapat") { dismiss() } }
            ToolbarItem(placement: .topBarTrailing) {
                if notifications.contains(where: { !$0.read }) {
                    Button("Tümünü Okundu Yap") { Task { await markAllRead() } }
                        .font(.caption.weight(.semibold))
                }
            }
        }
        .task { await load() }
        .alert("Bildirimler yüklenemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "Bilinmeyen hata") }
    }

    private func notificationRow(_ item: AdminNotificationDTO) -> some View {
        HStack(alignment: .top, spacing: 12) {
            ZStack {
                Circle().fill(iconColor(item).opacity(0.12)).frame(width: 40, height: 40)
                Image(systemName: iconName(item)).foregroundStyle(iconColor(item))
            }
            VStack(alignment: .leading, spacing: 4) {
                HStack(alignment: .firstTextBaseline) {
                    Text(item.title).font(.subheadline.weight(item.read ? .medium : .semibold))
                    Spacer(minLength: 8)
                    Text(formattedDate(item.createdAt)).font(.caption2).foregroundStyle(.secondary)
                }
                Text(item.message).font(.caption).foregroundStyle(.secondary).multilineTextAlignment(.leading)
            }
            if !item.read { Circle().fill(PusulaTheme.accent).frame(width: 7, height: 7).padding(.top, 5) }
        }
        .padding(.vertical, 7)
        .contentShape(Rectangle())
    }

    private func open(_ item: AdminNotificationDTO) {
        Task {
            if !item.read { await markRead(item) }
            if item.referenceType == "TICKET", let id = item.referenceId {
                await MainActor.run {
                    AppNavigation.shared.openTicket(id: id)
                    dismiss()
                }
            }
        }
    }

    private func markRead(_ item: AdminNotificationDTO) async {
        do {
            let updated = try await AdminNotificationService.markRead(id: item.id)
            await MainActor.run { replace(updated) }
            await onChanged()
        } catch { await MainActor.run { errorMessage = error.localizedDescription } }
    }

    private func markAllRead() async {
        do {
            try await AdminNotificationService.markAllRead()
            await MainActor.run { notifications = notifications.map { item in
                AdminNotificationDTO(id: item.id, title: item.title, message: item.message, severity: item.severity,
                    category: item.category, referenceType: item.referenceType, referenceId: item.referenceId,
                    read: true, createdAt: item.createdAt)
            } }
            await onChanged()
        } catch { await MainActor.run { errorMessage = error.localizedDescription } }
    }

    private func load() async {
        do {
            let loaded = try await AdminNotificationService.list()
            await MainActor.run { notifications = loaded; isLoading = false }
        } catch { await MainActor.run { errorMessage = error.localizedDescription; isLoading = false } }
    }

    private func replace(_ updated: AdminNotificationDTO) {
        guard let index = notifications.firstIndex(where: { $0.id == updated.id }) else { return }
        notifications[index] = updated
    }

    private func iconName(_ item: AdminNotificationDTO) -> String {
        switch item.category {
        case "NEW_SERVICE": return "plus.circle"
        case "SERVICE_RESCHEDULED": return "calendar.badge.clock"
        case "SERVICE_COMPLETED": return "checkmark.circle"
        case "CRITICAL_STOCK": return "shippingbox"
        case "IMPORTANT_NOTE": return "note.text.badge.plus"
        default: return "bell"
        }
    }

    private func iconColor(_ item: AdminNotificationDTO) -> Color {
        item.severity == "CRITICAL" ? .red : (item.severity == "WARNING" ? .orange : PusulaTheme.accent)
    }

    private func formattedDate(_ raw: String) -> String {
        let input = ISO8601DateFormatter()
        let date = input.date(from: raw) ?? {
            let formatter = DateFormatter(); formatter.locale = Locale(identifier: "en_US_POSIX")
            formatter.dateFormat = "yyyy-MM-dd'T'HH:mm:ss.SSSSSS"; return formatter.date(from: raw)
        }()
        guard let date else { return "" }
        return date.formatted(.relative(presentation: .named))
    }
}
