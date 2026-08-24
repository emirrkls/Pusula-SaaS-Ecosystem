import SwiftUI
import UIKit

struct BusinessAssetsView: View {
    @State private var assets: [BusinessAssetDTO] = []
    @State private var search = ""
    @State private var editing: BusinessAssetDTO?
    @State private var showEditor = false
    @State private var pdfPreview: PDFPreviewItem?
    @State private var isLoading = true
    @State private var errorMessage: String?

    private var filtered: [BusinessAssetDTO] {
        guard !search.trimmingCharacters(in: .whitespaces).isEmpty else { return assets }
        let term = search.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "tr_TR"))
        return assets.filter { [$0.assetName, $0.category, $0.serialNumber, $0.location, $0.assignedTo].compactMap { $0 }.contains { $0.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "tr_TR")).contains(term) } }
    }
    private var totalValue: Double { assets.reduce(0) { $0 + $1.totalValue } }

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                VStack(alignment: .leading) { Text("Envanter değeri").font(.caption).foregroundStyle(.secondary); Text(formatCurrency(totalValue)).font(.title2.bold()).foregroundStyle(PusulaTheme.accent) }
                Spacer()
                Button { createPDF() } label: { Label("PDF", systemImage: "arrow.down.doc") }
            }.padding()
            List(filtered) { asset in
                Button { editing = asset; showEditor = true } label: {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack { Text(asset.assetName).font(.headline); Spacer(); Text(formatCurrency(asset.totalValue)).font(.headline) }
                        HStack { Text("\(asset.quantity ?? 1) adet"); if let category = asset.category, !category.isEmpty { Text("• \(category)") }; Spacer(); Text(assetConditionLabel(asset.condition)) }
                            .font(.caption).foregroundStyle(.secondary)
                        if let assigned = asset.assignedTo, !assigned.isEmpty { Label(assigned, systemImage: "person").font(.caption).foregroundStyle(.secondary) }
                    }.padding(.vertical, 4)
                }.buttonStyle(.plain)
                .swipeActions { Button("Sil", role: .destructive) { Task { await delete(asset) } } }
            }
            .listStyle(.plain)
            .searchable(text: $search, prompt: "Ad, kategori, seri no veya konum")
            .overlay { if isLoading { ProgressView("Demirbaşlar yükleniyor...") } else if filtered.isEmpty { ContentUnavailableView.search(text: search) } }
        }
        .toolbar { ToolbarItem(placement: .primaryAction) { Button { editing = nil; showEditor = true } label: { Image(systemName: "plus") }.readOnlyProtected() } }
        .task { await load() }.refreshable { await load() }
        .sheet(isPresented: $showEditor) { BusinessAssetEditorSheet(asset: editing) { await load() } }
        .sheet(item: $pdfPreview) { PDFPreviewSheet(item: $0) }
        .alert("İşlem Başarısız", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) { Button("Tamam", role: .cancel) {} } message: { Text(errorMessage ?? "") }
    }
    private func load() async { isLoading = true; defer { isLoading = false }; do { assets = try await FinanceService.getBusinessAssets() } catch { errorMessage = error.localizedDescription } }
    private func delete(_ asset: BusinessAssetDTO) async { guard let id = asset.id else { return }; do { try await FinanceService.deleteBusinessAsset(id: id); await load() } catch { errorMessage = error.localizedDescription } }
    private func createPDF() {
        do { pdfPreview = try PDFPreviewItem(data: BusinessAssetPDF.make(assets: assets), fileName: "isletme-mulkiyeti.pdf", title: "İşletme Mülkiyeti") } catch { errorMessage = error.localizedDescription }
    }
}

private struct BusinessAssetEditorSheet: View {
    let asset: BusinessAssetDTO?; let onSaved: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""; @State private var category = ""; @State private var quantity = 1; @State private var condition = "ACTIVE"
    @State private var serial = ""; @State private var location = ""; @State private var assignedTo = ""; @State private var hasPurchaseDate = false
    @State private var purchaseDate = Date(); @State private var price = ""; @State private var notes = ""; @State private var errorMessage: String?
    var body: some View {
        NavigationStack {
            Form {
                Section("Temel bilgiler") { TextField("Demirbaş adı", text: $name); TextField("Kategori", text: $category); Stepper("Adet: \(quantity)", value: $quantity, in: 1...100_000); Picker("Durum", selection: $condition) { Text("Aktif").tag("ACTIVE"); Text("Bakımda").tag("MAINTENANCE"); Text("Arızalı").tag("BROKEN"); Text("Kullanım dışı").tag("RETIRED") } }
                Section("Takip") { TextField("Seri numarası", text: $serial); TextField("Konum", text: $location); TextField("Zimmetli", text: $assignedTo) }
                Section("Değer") { TextField("Birim alış fiyatı", text: $price).keyboardType(.decimalPad); Toggle("Alış tarihi", isOn: $hasPurchaseDate); if hasPurchaseDate { DatePicker("Tarih", selection: $purchaseDate, displayedComponents: .date) } }
                Section("Not") { TextField("Notlar", text: $notes, axis: .vertical) }
            }
            .navigationTitle(asset == nil ? "Demirbaş Ekle" : "Demirbaşı Düzenle")
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("İptal") { dismiss() } }; ToolbarItem(placement: .confirmationAction) { Button("Kaydet") { Task { await save() } }.disabled(name.trimmingCharacters(in: .whitespaces).isEmpty) } }
            .onAppear { populate() }
            .alert("Demirbaş Kaydedilemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) { Button("Tamam", role: .cancel) {} } message: { Text(errorMessage ?? "") }
        }
    }
    private var parsedPrice: Double { Double(price.replacingOccurrences(of: ",", with: ".")) ?? 0 }
    private func populate() { guard let asset else { return }; name = asset.assetName; category = asset.category ?? ""; quantity = asset.quantity ?? 1; condition = asset.condition ?? "ACTIVE"; serial = asset.serialNumber ?? ""; location = asset.location ?? ""; assignedTo = asset.assignedTo ?? ""; price = String(asset.purchasePrice ?? 0); notes = asset.notes ?? ""; if let date = parseFinanceDate(asset.purchaseDate) { purchaseDate = date; hasPurchaseDate = true } }
    private func save() async {
        let value = BusinessAssetDTO(id: asset?.id, assetName: name.trimmingCharacters(in: .whitespacesAndNewlines), category: category.isEmpty ? nil : category, quantity: quantity, condition: condition, serialNumber: serial.isEmpty ? nil : serial, location: location.isEmpty ? nil : location, assignedTo: assignedTo.isEmpty ? nil : assignedTo, purchaseDate: hasPurchaseDate ? financeDate(purchaseDate) : nil, purchasePrice: parsedPrice, notes: notes.isEmpty ? nil : notes)
        do { if asset == nil { _ = try await FinanceService.createBusinessAsset(value) } else { _ = try await FinanceService.updateBusinessAsset(value) }; await onSaved(); dismiss() } catch { errorMessage = error.localizedDescription }
    }
}

private func assetConditionLabel(_ value: String?) -> String { switch value { case "MAINTENANCE": return "Bakımda"; case "BROKEN": return "Arızalı"; case "RETIRED": return "Kullanım dışı"; default: return "Aktif" } }

private enum BusinessAssetPDF {
    static func make(assets: [BusinessAssetDTO]) -> Data {
        let page = CGRect(x: 0, y: 0, width: 842, height: 595)
        let renderer = UIGraphicsPDFRenderer(bounds: page)
        return renderer.pdfData { context in
            var y: CGFloat = 36
            func newPage() { context.beginPage(); y = 36; drawText("İŞLETME MÜLKİYETİ / TAKIMLAR VE DEMİRBAŞLAR", rect: CGRect(x: 36, y: y, width: 770, height: 26), font: .boldSystemFont(ofSize: 17)); y += 38 }
            newPage()
            let total = assets.reduce(0) { $0 + $1.totalValue }
            drawText("Envanter Değeri: \(formatCurrency(total))", rect: CGRect(x: 36, y: y, width: 770, height: 22), font: .boldSystemFont(ofSize: 12)); y += 30
            for asset in assets {
                if y > 535 { newPage() }
                drawText(asset.assetName, rect: CGRect(x: 36, y: y, width: 260, height: 18), font: .boldSystemFont(ofSize: 9))
                drawText(asset.category ?? "-", rect: CGRect(x: 300, y: y, width: 110, height: 18), font: .systemFont(ofSize: 9))
                drawText("\(asset.quantity ?? 1) adet", rect: CGRect(x: 415, y: y, width: 70, height: 18), font: .systemFont(ofSize: 9))
                drawText(asset.assignedTo ?? asset.location ?? "-", rect: CGRect(x: 490, y: y, width: 160, height: 18), font: .systemFont(ofSize: 9))
                drawText(formatCurrency(asset.totalValue), rect: CGRect(x: 655, y: y, width: 150, height: 18), font: .boldSystemFont(ofSize: 9), alignment: .right)
                y += 22
            }
        }
    }
    private static func drawText(_ text: String, rect: CGRect, font: UIFont, alignment: NSTextAlignment = .left) {
        let paragraph = NSMutableParagraphStyle(); paragraph.alignment = alignment
        (text as NSString).draw(in: rect, withAttributes: [.font: font, .foregroundColor: UIColor(red: 0.03, green: 0.10, blue: 0.32, alpha: 1), .paragraphStyle: paragraph])
    }
}
