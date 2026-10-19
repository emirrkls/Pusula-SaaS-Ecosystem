#if DEBUG
import SwiftUI
import UIKit

enum PDFShareFixture {
    static var isEnabled: Bool {
#if targetEnvironment(simulator)
        AppStoreScreenshotMode.isEnabled && ProcessInfo.processInfo.environment["PUSULA_PDF_SHARE_FIXTURE"] != nil
#else
        false
#endif
    }
    static var scenario: String { ProcessInfo.processInfo.environment["PUSULA_PDF_SHARE_FIXTURE"] ?? "service" }
    static var forcePresentationFailure: Bool { isEnabled && scenario == "presentation-failure" }

    @MainActor static func item() throws -> PDFPreviewItem {
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: 595, height: 842))
        let data = renderer.pdfData { context in
            context.beginPage()
            "Synthetic offline PDF — no customer data".draw(at: CGPoint(x: 40, y: 40),
                withAttributes: [.font: UIFont.systemFont(ofSize: 18)])
        }
        return try PDFPreviewItem(data: data, fileName: "synthetic-\(scenario).pdf", title: "Synthetic \(scenario) PDF")
    }
}

/// The PDF opens above another sheet, like a service form above ticket details.
/// No authentication, production request, customer file or actual recipient.
@MainActor
struct PDFShareHarness: View {
    @State private var showDetail = false
    @State private var preview: PDFPreviewItem?
    @State private var fixtureError: String?
    @State private var fileRemoved = false

    var body: some View {
        Button("Open synthetic detail") { showDetail = true }
            .accessibilityIdentifier("pdf.fixture.detail.open")
            .sheet(isPresented: $showDetail) {
                NavigationStack {
                    VStack(spacing: 20) {
                        Text("Synthetic detail").accessibilityIdentifier("pdf.fixture.detail")
                        Button("Open PDF") {
                            do { fileRemoved = false; preview = try PDFShareFixture.item() }
                            catch { fixtureError = "Synthetic PDF creation failed" }
                        }.accessibilityIdentifier("pdf.fixture.preview.open")
                        if let fixtureError { Text(fixtureError) }
                    }
                    .sheet(item: $preview) { item in
                        PDFPreviewSheet(item: item)
                            .overlay(alignment: .bottomLeading) {
                                if fileRemoved {
                                    Text("Synthetic file removed").font(.caption2)
                                        .accessibilityIdentifier("pdf.fixture.file_removed")
                                }
                            }
                            .onAppear {
                                if PDFShareFixture.scenario == "missing-file" {
                                    do { try FileManager.default.removeItem(at: item.url); fileRemoved = true }
                                    catch { fixtureError = "Synthetic file removal failed" }
                                }
                            }
                    }
                }
            }
    }
}
#endif
