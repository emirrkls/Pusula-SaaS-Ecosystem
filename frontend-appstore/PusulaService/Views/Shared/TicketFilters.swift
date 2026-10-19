import Foundation
import PDFKit
import SwiftUI

extension FieldTicketDTO: TicketListRecord {
    var ticketSearchValues: [String] {
        [String(id), customerName, customerPhone, customerAddress, description,
         notes, assignedTechnicianName, scheduledDate].compactMap { $0 }
    }
}

enum TicketFilters {
    static let adminFilters = TicketListQuery.adminFilters
    static let technicianFilters = TicketListQuery.technicianFilters

    static func defaultFilter(isAdmin: Bool) -> String {
        isAdmin ? "Atama Bekleyen" : "Bugünkü İşler"
    }

    static func parseBusinessDate(_ raw: String?) -> Date? {
        BusinessDateParser.parse(raw)
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
    @State private var isSharing = false
    @State private var shareFailure: PDFShareFailure?

    var body: some View {
        NavigationStack {
            PDFDocumentView(url: item.url)
                .background(Color(uiColor: .secondarySystemBackground))
                .navigationTitle(item.title)
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .cancellationAction) {
                        Button("pdf.preview.close") { dismiss() }
                            .disabled(isSharing)
                            .accessibilityIdentifier("pdf.preview.close")
                    }
                    ToolbarItem(placement: .primaryAction) {
                        PDFShareButton(url: item.url, isSharing: $isSharing) { shareFailure = $0 }
                            .fixedSize()
                            .frame(minWidth: 44, minHeight: 44)
                    }
                }
        }
        .interactiveDismissDisabled(isSharing)
        .alert("pdf.share.failure_title", isPresented: Binding(
            get: { shareFailure != nil }, set: { if !$0 { shareFailure = nil } }
        )) {
            Button("pdf.share.dismiss_error", role: .cancel) { shareFailure = nil }
        } message: {
            if let shareFailure { Text(String(localized: String.LocalizationValue(shareFailure.rawValue))) }
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
