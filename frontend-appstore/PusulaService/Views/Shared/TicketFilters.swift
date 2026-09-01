import Foundation
import PDFKit
import SwiftUI

enum TicketFilters {
    static let adminFilters = ["Atama Bekleyen", "Bugün Açılan", "Atanan", "Devam Eden", "Kapanan", "Tümü"]
    static let technicianFilters = ["Bugünün Çağrıları", "İleri Tarihli", "Kapanan", "İptal Edilen"]
    
    static func defaultFilter(isAdmin: Bool) -> String {
        isAdmin ? "Atama Bekleyen" : "Bugünün Çağrıları"
    }
    
    static func matches(_ ticket: FieldTicketDTO, filter: String, isAdmin: Bool) -> Bool {
        if filter == "Tümü" { return true }
        let status = ticket.status?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() ?? ""
        
        switch filter {
        case "Atama Bekleyen":
            return ticket.assignedTechnicianId == nil && status == "PENDING"
        case "Bugün Açılan":
            return isTodayInBusinessZone(ticket.createdAt)
        case "Atanan":
            if isAdmin {
                return status == "ASSIGNED"
            }
            return status == "ASSIGNED" || status == "IN_PROGRESS"
        case "Bugünün Çağrıları":
            guard status == "ASSIGNED" || status == "IN_PROGRESS" else { return false }
            guard let scheduled = parseBusinessDate(ticket.scheduledDate) else { return true }
            return businessCalendar().startOfDay(for: scheduled) <= businessDayStart()
        case "İleri Tarihli":
            guard status == "ASSIGNED" || status == "IN_PROGRESS",
                  let scheduled = parseBusinessDate(ticket.scheduledDate) else { return false }
            return businessCalendar().startOfDay(for: scheduled) > businessDayStart()
        case "Devam Eden":
            return status == "IN_PROGRESS"
        case "Kapanan":
            return status == "COMPLETED"
        case "İptal Edilen":
            return status == "CANCELLED"
        default:
            return status == filter.uppercased()
        }
    }
    
    static func pendingUnassigned(_ tickets: [FieldTicketDTO]) -> [FieldTicketDTO] {
        tickets.filter {
            let status = $0.status?.uppercased() ?? ""
            return $0.assignedTechnicianId == nil && status == "PENDING"
        }
    }
    
    private static func isTodayInBusinessZone(_ dateRaw: String?) -> Bool {
        guard let dateRaw, !dateRaw.isEmpty else { return false }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Europe/Istanbul")
        formatter.dateFormat = "yyyy-MM-dd"
        let today = formatter.string(from: Date())
        return dateRaw.hasPrefix(today) || dateRaw.contains(today)
    }

    static func matchesSearch(_ ticket: FieldTicketDTO, query: String) -> Bool {
        let term = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !term.isEmpty else { return true }

        let values: [String?] = [
            String(ticket.id),
            ticket.customerName,
            ticket.customerPhone,
            ticket.customerAddress,
            ticket.description,
            ticket.notes,
            ticket.assignedTechnicianName,
            ticket.scheduledDate
        ]
        return values.compactMap { $0 }.contains {
            $0.localizedCaseInsensitiveContains(term)
        }
    }

    static func sorted(_ tickets: [FieldTicketDTO], filter: String) -> [FieldTicketDTO] {
        let ascending = filter == "Bugünün Çağrıları" || filter == "İleri Tarihli"
        return tickets.sorted { left, right in
            let leftDate = sortDate(left)
            let rightDate = sortDate(right)
            switch (leftDate, rightDate) {
            case let (lhs?, rhs?) where lhs != rhs:
                return ascending ? lhs < rhs : lhs > rhs
            case (_?, nil):
                return true
            case (nil, _?):
                return false
            default:
                return left.id > right.id
            }
        }
    }

    private static func sortDate(_ ticket: FieldTicketDTO) -> Date? {
        parseBusinessDate(ticket.scheduledDate) ?? parseBusinessDate(ticket.createdAt)
    }

    static func parseBusinessDate(_ raw: String?) -> Date? {
        guard let raw, !raw.isEmpty else { return nil }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Europe/Istanbul")
        for format in ["yyyy-MM-dd'T'HH:mm:ss.SSSSSS", "yyyy-MM-dd'T'HH:mm:ss.SSS", "yyyy-MM-dd'T'HH:mm:ss"] {
            formatter.dateFormat = format
            if let date = formatter.date(from: raw) { return date }
        }
        return ISO8601DateFormatter().date(from: raw)
    }

    private static func businessDayStart() -> Date {
        businessCalendar().startOfDay(for: Date())
    }

    private static func businessCalendar() -> Calendar {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(identifier: "Europe/Istanbul") ?? .current
        return calendar
    }
}

func formatCurrency(_ value: Double?) -> String {
    guard let value else { return "₺0,00" }
    return String(format: "₺%.2f", value)
}

func formatShortAmount(_ value: Double?) -> String {
    guard let val = value else { return "0" }
    if abs(val) >= 1000 {
        return String(format: "%.1fK", val / 1000)
    }
    return String(format: "%.0f", val)
}

struct PDFPreviewItem: Identifiable {
    let id = UUID()
    let url: URL
    let title: String

    init(data: Data, fileName: String, title: String) throws {
        guard data.starts(with: Data("%PDF-".utf8)), PDFDocument(data: data) != nil else {
            throw PDFPreviewError.invalidDocument
        }

        let directory = FileManager.default.temporaryDirectory
            .appendingPathComponent("PusulaPDFPreviews", isDirectory: true)
            .appendingPathComponent(id.uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
        url = directory.appendingPathComponent(fileName)
        self.title = title
        try data.write(to: url, options: .atomic)
    }
}

struct PDFPreviewSheet: View {
    let item: PDFPreviewItem
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationStack {
            PDFDocumentView(url: item.url)
                .background(Color(uiColor: .secondarySystemBackground))
                .navigationTitle(item.title)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("Kapat") { dismiss() }
                    }
                    ToolbarItem(placement: .primaryAction) {
                        ShareLink(item: item.url) {
                            Image(systemName: "square.and.arrow.up")
                        }
                        .accessibilityLabel("PDF'yi paylaş")
                    }
                }
        }
    }
}

private struct PDFDocumentView: UIViewRepresentable {
    let url: URL

    func makeUIView(context: Context) -> PDFView {
        let view = PDFView()
        view.autoScales = true
        view.displayMode = .singlePageContinuous
        view.displayDirection = .vertical
        return view
    }

    func updateUIView(_ view: PDFView, context: Context) {
        if view.document?.documentURL != url {
            view.document = PDFDocument(url: url)
        }
    }
}

private enum PDFPreviewError: LocalizedError {
    case invalidDocument

    var errorDescription: String? {
        "Sunucu geçerli bir PDF döndürmedi. Lütfen daha sonra tekrar deneyin."
    }
}
