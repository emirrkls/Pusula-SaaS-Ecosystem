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
    
    let onItemSelected: (InventoryItemDTO) -> Void
    
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
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(.cyan, lineWidth: 2)
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
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    } else if let error = errorMessage {
                        HStack {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(.red)
                            Text(error)
                                .font(.caption)
                        }
                        .padding()
                        .background(.ultraThinMaterial)
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    } else {
                        Text("Barkodu kameraya gösterin")
                            .font(.subheadline)
                            .padding()
                            .background(.ultraThinMaterial)
                            .clipShape(RoundedRectangle(cornerRadius: 12))
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
                Text("₺\(String(format: "%.2f", item.sellPrice ?? 0))")
                    .font(.title3.weight(.bold))
                    .foregroundColor(.cyan)
            }
            
            // Quantity stepper
            HStack {
                Text("Adet:")
                    .font(.subheadline)
                Stepper("\(quantity)", value: $quantity, in: 1...max(item.quantity, 1))
                    .font(.subheadline.weight(.semibold))
            }
            
            Button(action: {
                onItemSelected(item)
                dismiss()
            }) {
                HStack {
                    Image(systemName: "cart.badge.plus")
                    Text("Sepete Ekle")
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 12)
                .font(.headline)
            }
            .background(.cyan)
            .foregroundColor(.white)
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .disabled(item.quantity < 1)
        }
        .padding()
        .background(.ultraThickMaterial)
        .clipShape(RoundedRectangle(cornerRadius: 20))
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
