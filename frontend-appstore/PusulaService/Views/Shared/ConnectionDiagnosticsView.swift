import SwiftUI

struct ConnectionDiagnosticsView: View {
    var store: ConnectionDiagnosticsStore = .shared
    @Environment(\.dismiss) private var dismiss
    @State private var report: ConnectionDiagnosticReport?
    @State private var exportURL: URL?
    @State private var reportText = ""
    @State private var isBusy = false
    @State private var exportFailed = false
    @State private var clearFailed = false
    @State private var showClearConfirmation = false

    private var failureCount: Int {
        report?.records.filter { [.transportError, .httpError, .localError].contains($0.outcome) }.count ?? 0
    }

    var body: some View {
        Form {
            Section {
                Text("diagnostics.explanation")
                Text("diagnostics.privacy")
                    .font(.footnote).foregroundStyle(.secondary)
            }
            Section("diagnostics.report_section") {
                if let report {
                    Text(String(format: String(localized: "diagnostics.summary"), report.records.count, failureCount))
                        .accessibilityIdentifier("diagnostics.summary")
                        .accessibilityValue("\(report.records.count):\(failureCount)")
                    NavigationLink {
                        ScrollView {
                            Text(verbatim: reportText)
                                .font(.system(.caption, design: .monospaced))
                                .textSelection(.enabled)
                                .frame(maxWidth: .infinity, alignment: .leading)
                                .padding()
                                .accessibilityIdentifier("diagnostics.report.body")
                        }
                        .navigationTitle("diagnostics.preview")
                    } label: { Label("diagnostics.preview", systemImage: "doc.text.magnifyingglass") }
                    .accessibilityIdentifier("diagnostics.preview.open")
                    if let exportURL {
                        ShareLink(item: exportURL) {
                            Label("diagnostics.share", systemImage: "square.and.arrow.up")
                        }
                        .disabled(report.records.isEmpty || isBusy)
                        .accessibilityIdentifier("diagnostics.share")
                    }
                }
                if isBusy { ProgressView("diagnostics.preparing") }
                if exportFailed {
                    Text("diagnostics.export_failed").foregroundStyle(.orange)
                }
                if clearFailed {
                    Text("diagnostics.clear_failed").foregroundStyle(.orange)
                }
                Button { Task { await reload() } } label: {
                    Label("diagnostics.refresh", systemImage: "arrow.clockwise")
                }
                .disabled(isBusy)
                .accessibilityIdentifier("diagnostics.refresh")
            }
            Section {
                Button("diagnostics.clear", role: .destructive) { showClearConfirmation = true }
                    .disabled(isBusy)
                    .accessibilityIdentifier("diagnostics.clear")
            } footer: { Text("diagnostics.retention") }
        }
        .navigationTitle("diagnostics.title")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar { ToolbarItem(placement: .topBarTrailing) { Button("diagnostics.close") { dismiss() } } }
        .task { await reload() }
        .confirmationDialog("diagnostics.clear_title", isPresented: $showClearConfirmation, titleVisibility: .visible) {
            Button("diagnostics.clear_confirm", role: .destructive) {
                Task {
                    guard !isBusy else { return }
                    exportURL = nil
                    reportText = ""
                    clearFailed = !(await store.clear())
                    await reload()
                }
            }
            Button("diagnostics.cancel", role: .cancel) {}
        } message: { Text("diagnostics.clear_message") }
    }

    @MainActor private func reload() async {
        guard !isBusy else { return }
        isBusy = true
        defer { isBusy = false }
        exportURL = nil
        exportFailed = false
        let metadata = ConnectionDiagnosticEnvironment.metadata()
        let snapshot = await store.report(metadata: metadata)
        report = snapshot
        reportText = (try? ConnectionDiagnosticsStore.encoder().encode(snapshot)).flatMap { String(data: $0, encoding: .utf8) } ?? ""
        do {
            let exported = try await store.export(metadata: metadata)
            report = exported.1
            reportText = (try? ConnectionDiagnosticsStore.encoder().encode(exported.1)).flatMap { String(data: $0, encoding: .utf8) } ?? ""
            exportURL = exported.0
        } catch { exportFailed = true }
    }
}
