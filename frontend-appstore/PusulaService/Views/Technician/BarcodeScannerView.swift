import SwiftUI
import AVFoundation

/// Barcode scanner view using AVFoundation camera.
/// When a barcode is detected, looks it up via API and returns the InventoryItemDTO.
struct BarcodeScannerView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var scannedCode: String?
    @State private var foundItem: InventoryItemDTO?
    @State private var isSearching = false
    @State private var errorMessage: String?
    @State private var quantity = 1
    @State private var unitPriceText = ""
    @State private var showPriceChangeConfirmation = false
    
    let onItemSelected: (InventoryItemDTO, Int, Double) -> Void
    
    var body: some View {
        NavigationStack {
            ZStack {
                // Camera preview
                CameraPreview(onBarcodeDetected: handleBarcode)
                    .ignoresSafeArea()
                
                // Overlay
                VStack {
                    Spacer()
                    
                    // Scan guide frame
                    RoundedRectangle(cornerRadius: PusulaTheme.radius)
                        .stroke(PusulaTheme.accent, lineWidth: 2)
                        .frame(width: 280, height: 160)
                        .background(.black.opacity(0.01)) // Invisible tap target
                    
                    Spacer()
                    
                    // Result card
                    if let item = foundItem {
                        foundItemCard(item)
                            .transition(.move(edge: .bottom).combined(with: .opacity))
                    } else if isSearching {
                        ProgressView("Ürün aranıyor...")
                            .padding()
                            .background(.ultraThinMaterial)
                            .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
                    } else if let error = errorMessage {
                        HStack {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(.red)
                            Text(error)
                                .font(.caption)
                        }
                        .padding()
                        .background(.ultraThinMaterial)
                        .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
                    } else {
                        Text("Barkodu kameraya gösterin")
                            .font(.subheadline)
                            .padding()
                            .background(.ultraThinMaterial)
                            .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
                    }
                }
                .padding()
            }
            .navigationTitle("Barkod Okuyucu")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("İptal") { dismiss() }
                }
            }
            .animation(.spring(duration: 0.3), value: foundItem != nil)
            .alert("Satış fiyatı değiştirilsin mi?", isPresented: $showPriceChangeConfirmation) {
                Button("Vazgeç", role: .cancel) {}
                Button("Onayla ve Ekle") {
                    if let item = foundItem { submit(item) }
                }
            } message: {
                if let item = foundItem {
                    Text("Envanter fiyatı: \(formatCurrency(item.sellPrice ?? 0))\nYeni fiyat: \(formatCurrency(parsedUnitPrice ?? 0))")
                }
            }
        }
    }
    
    private func foundItemCard(_ item: InventoryItemDTO) -> some View {
        VStack(spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 4) {
                    Text(item.partName)
                        .font(.headline)
                    HStack(spacing: 8) {
                        if let brand = item.brand {
                            Text(brand)
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Text("Stok: \(item.quantity)")
                            .font(.caption)
                            .foregroundColor(item.quantity > 0 ? .green : .red)
                    }
                }
                Spacer()
                Text(formatCurrency(item.sellPrice ?? 0))
                    .font(.title3.weight(.bold))
                    .foregroundColor(PusulaTheme.accent)
            }
            
            // Quantity stepper
            HStack {
                Text("Adet:")
                    .font(.subheadline)
                Stepper("\(quantity)", value: $quantity, in: 1...max(item.quantity, 1))
                    .font(.subheadline.weight(.semibold))
            }

            VStack(alignment: .leading, spacing: 6) {
                Text("Bu servisteki birim satış fiyatı")
                    .font(.caption.weight(.semibold))
                TextField("Birim satış fiyatı", text: $unitPriceText)
                    .keyboardType(.decimalPad)
                    .textFieldStyle(.roundedBorder)
                if let price = parsedUnitPrice {
                    Text("Toplam: \(formatCurrency(price * Double(quantity)))")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            
            Button(action: {
                if isPriceChanged(from: item.sellPrice ?? 0) {
                    showPriceChangeConfirmation = true
                } else {
                    submit(item)
                }
            }) {
                HStack {
                    Image(systemName: "cart.badge.plus")
                    Text("Sepete Ekle")
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .font(.headline)
            }
            .background(PusulaTheme.accent)
            .foregroundColor(.white)
            .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
            .disabled(item.quantity < 1 || quantity < 1 || quantity > item.quantity || parsedUnitPrice == nil)
        }
        .padding()
        .background(.ultraThickMaterial)
        .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
    }
    
    private func handleBarcode(_ code: String) {
        guard !isSearching, scannedCode != code else { return }
        scannedCode = code
        isSearching = true
        errorMessage = nil
        foundItem = nil
        
        Task {
            do {
                let item = try await TicketService.lookupBarcode(code)
                await MainActor.run {
                    quantity = 1
                    unitPriceText = (item.sellPrice ?? 0).formatted(
                        .number.precision(.fractionLength(2)).locale(Locale(identifier: "tr_TR"))
                    )
                    // Publish the selected item only after its dependent form state is ready.
                    // This keeps the initial quantity of 1 immediately valid.
                    foundItem = item
                    isSearching = false
                }
            } catch {
                await MainActor.run {
                    errorMessage = "Barkod bulunamadı: \(code)"
                    isSearching = false
                    // Reset after 2 seconds for re-scan
                    DispatchQueue.main.asyncAfter(deadline: .now() + 2) {
                        scannedCode = nil
                        errorMessage = nil
                    }
                }
            }
        }
    }

    private var parsedUnitPrice: Double? {
        let normalized = unitPriceText
            .trimmingCharacters(in: .whitespacesAndNewlines)
            .replacingOccurrences(of: ",", with: ".")
        guard let value = Double(normalized), value >= 0, value.isFinite else { return nil }
        return value
    }

    private func isPriceChanged(from inventoryPrice: Double) -> Bool {
        guard let price = parsedUnitPrice else { return false }
        return abs(price - inventoryPrice) >= 0.005
    }

    private func submit(_ item: InventoryItemDTO) {
        guard let price = parsedUnitPrice else { return }
        onItemSelected(item, quantity, price)
        dismiss()
    }

    private func formatCurrency(_ value: Double) -> String {
        value.formatted(.currency(code: "TRY").locale(Locale(identifier: "tr_TR")))
    }
}

// MARK: - Camera Preview (AVFoundation)

struct CameraPreview: UIViewControllerRepresentable {
    let onBarcodeDetected: (String) -> Void
    
    func makeUIViewController(context: Context) -> CameraScannerController {
        let controller = CameraScannerController()
        controller.onBarcodeDetected = onBarcodeDetected
        return controller
    }
    
    func updateUIViewController(_ uiViewController: CameraScannerController, context: Context) {}
}

class CameraScannerController: UIViewController, AVCaptureMetadataOutputObjectsDelegate {
    var captureSession: AVCaptureSession?
    var previewLayer: AVCaptureVideoPreviewLayer?
    var onBarcodeDetected: ((String) -> Void)?
    
    override func viewDidLoad() {
        super.viewDidLoad()
        
        let session = AVCaptureSession()
        
        guard let device = AVCaptureDevice.default(for: .video),
              let input = try? AVCaptureDeviceInput(device: device) else {
            return
        }
        
        if session.canAddInput(input) {
            session.addInput(input)
        }
        
        let output = AVCaptureMetadataOutput()
        if session.canAddOutput(output) {
            session.addOutput(output)
            output.setMetadataObjectsDelegate(self, queue: DispatchQueue.main)
            output.metadataObjectTypes = [.ean8, .ean13, .code128, .qr, .upce, .code39]
        }
        
        let preview = AVCaptureVideoPreviewLayer(session: session)
        preview.videoGravity = .resizeAspectFill
        preview.frame = view.layer.bounds
        view.layer.addSublayer(preview)
        
        captureSession = session
        previewLayer = preview
        
        DispatchQueue.global(qos: .userInitiated).async {
            session.startRunning()
        }
    }
    
    override func viewDidLayoutSubviews() {
        super.viewDidLayoutSubviews()
        previewLayer?.frame = view.layer.bounds
    }
    
    func metadataOutput(_ output: AVCaptureMetadataOutput,
                        didOutput metadataObjects: [AVMetadataObject],
                        from connection: AVCaptureConnection) {
        guard let object = metadataObjects.first as? AVMetadataMachineReadableCodeObject,
              let code = object.stringValue else { return }
        
        // Haptic feedback
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.impactOccurred()
        
        onBarcodeDetected?(code)
    }
    
    override func viewWillDisappear(_ animated: Bool) {
        super.viewWillDisappear(animated)
        captureSession?.stopRunning()
    }
}
