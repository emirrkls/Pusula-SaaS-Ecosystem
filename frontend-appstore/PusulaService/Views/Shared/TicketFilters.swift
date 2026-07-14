import Foundation
import PDFKit
import SwiftUI

enum TicketFilters {
    static let adminFilters = ["Atama Bekleyen", "Bugün Açılan", "Atanan", "Devam Eden", "Kapanan", "Tümü"]
    static let technicianFilters = ["Atanan", "Kapanan", "Tümü"]
    
    static func defaultFilter(isAdmin: Bool) -> String {
        isAdmin ? "Atama Bekleyen" : "Atanan"
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
        case "Devam Eden":
            return status == "IN_PROGRESS"
        case "Kapanan":
            return status == "COMPLETED" || status == "CANCELLED"
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
