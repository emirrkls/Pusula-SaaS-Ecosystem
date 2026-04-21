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
                        .foregroundColor(.cyan)
                    Text("Lütfen aşağıdaki alana imzanızı atınız")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)
                }
                .padding()
                
                // Signature canvas
                ZStack {
                    SignatureCanvas(canvasView: $canvasView)
                        .background(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 16))
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
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
                    .clipShape(RoundedRectangle(cornerRadius: 12))
                    
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
                    .background(.cyan)
                    .foregroundColor(.white)
                    .clipShape(RoundedRectangle(cornerRadius: 12))
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
        
        // Render canvas to image
        let renderer = UIGraphicsImageRenderer(bounds: canvasView.bounds)
        let image = renderer.image { ctx in
            canvasView.drawHierarchy(in: canvasView.bounds, afterScreenUpdates: true)
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
        
        // Set pen tool — fine black ink
        let ink = PKInkingTool(.pen, color: .black, width: 3)
        canvasView.tool = ink
        
        // Disable ruler
        canvasView.isRulerActive = false
        
        return canvasView
    }
    
    func updateUIView(_ uiView: PKCanvasView, context: Context) {}
}
