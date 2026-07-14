import SwiftUI
import PencilKit

/// Full-screen signature capture using PencilKit.
/// The captured signature is converted to PNG base64 and uploaded to the backend.
struct SignatureView: View {
    let ticketId: Int
    
    @Environment(\.dismiss) private var dismiss
    @State private var canvasView = PKCanvasView()
    @State private var isUploading = false
    @State private var showSuccess = false
    @State private var errorMessage: String?
    
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                // Instructions
                HStack {
                    Image(systemName: "pencil.tip.crop.circle")
                        .foregroundColor(PusulaTheme.accent)
                    Text("Lütfen aşağıdaki alana imzanızı atınız")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .padding()
                
                // Signature canvas
                ZStack {
                    SignatureCanvas(canvasView: $canvasView)
                        .background(.white)
                        .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
                        .overlay(
                            RoundedRectangle(cornerRadius: PusulaTheme.radius)
                                .stroke(Color(.systemGray3), lineWidth: 1)
                        )
                    
                    // Signature line
                    VStack {
                        Spacer()
                        Rectangle()
                            .fill(Color(.systemGray4))
                            .frame(height: 1)
                            .padding(.horizontal, 40)
                            .padding(.bottom, 60)
                    }
                    .allowsHitTesting(false)
                    
                    // "X" mark for signature start
                    VStack {
                        Spacer()
                        HStack {
                            Text("✕")
                                .font(.title2)
                                .foregroundColor(.gray)
                                .padding(.leading, 44)
                                .padding(.bottom, 64)
                            Spacer()
                        }
                    }
                    .allowsHitTesting(false)
                }
                .frame(height: 300)
                .padding()
                
                // Error / Success
                if let error = errorMessage {
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.red)
                        .padding()
                }
                
                if showSuccess {
                    HStack {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundColor(.green)
                        Text("İmza başarıyla kaydedildi!")
                            .font(.subheadline.weight(.semibold))
                            .foregroundColor(.green)
                    }
                    .padding()
                    .transition(.opacity)
                }
                
                Spacer()
                
                // Action buttons
                HStack(spacing: 16) {
                    // Clear button
                    Button(action: clearSignature) {
                        HStack {
                            Image(systemName: "arrow.counterclockwise")
                            Text("Temizle")
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                    }
                    .background(Color(.systemGray5))
                    .foregroundColor(.primary)
                    .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
                    
                    // Save button
                    Button(action: saveSignature) {
                        HStack {
                            if isUploading {
                                ProgressView().tint(.white)
                            } else {
                                Image(systemName: "checkmark")
                                Text("Kaydet")
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                    }
                    .background(PusulaTheme.accent)
                    .foregroundColor(.white)
                    .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
                    .disabled(isUploading)
                }
                .padding()
            }
            .navigationTitle("Müşteri İmzası")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button("İptal") { dismiss() }
                }
            }
        }
    }
    
    private func clearSignature() {
        canvasView.drawing = PKDrawing()
    }
    
    private func saveSignature() {
        isUploading = true
        errorMessage = nil
        
        let drawing = canvasView.drawing
        guard !drawing.strokes.isEmpty else {
            errorMessage = "Lütfen önce imzanızı atın"
            isUploading = false
            return
        }

        // Render the PencilKit strokes themselves so the saved image contains
        // the complete signature instead of a cropped canvas viewport.
        let signatureBounds = drawing.bounds.insetBy(dx: -16, dy: -16)
        let scale = canvasView.window?.screen.scale ?? 2
        let strokeImage = drawing.image(from: signatureBounds, scale: scale)
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: signatureBounds.size, format: format)
        let image = renderer.image { context in
            UIColor.white.setFill()
            context.cgContext.fill(CGRect(origin: .zero, size: signatureBounds.size))
            strokeImage.draw(in: CGRect(origin: .zero, size: signatureBounds.size))
        }
        
        guard let pngData = image.pngData() else {
            errorMessage = "İmza görüntüsü oluşturulamadı"
            isUploading = false
            return
        }
        
        let base64 = pngData.base64EncodedString()
        
        Task {
            do {
                _ = try await TicketService.uploadSignature(ticketId: ticketId, signatureBase64: base64)
                await MainActor.run {
                    showSuccess = true
                    isUploading = false
                    DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
                        dismiss()
                    }
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isUploading = false
                }
            }
        }
    }
}

// MARK: - PencilKit Canvas Wrapper

struct SignatureCanvas: UIViewRepresentable {
    @Binding var canvasView: PKCanvasView
    
    func makeUIView(context: Context) -> PKCanvasView {
        canvasView.drawingPolicy = .anyInput
        canvasView.backgroundColor = .clear
        canvasView.isScrollEnabled = false
        canvasView.bounces = false
        
        // Set pen tool — fine black ink
        let ink = PKInkingTool(.pen, color: .black, width: 3)
        canvasView.tool = ink
        
        // Disable ruler
        canvasView.isRulerActive = false
        
        return canvasView
    }
    
    func updateUIView(_ uiView: PKCanvasView, context: Context) {}
}
