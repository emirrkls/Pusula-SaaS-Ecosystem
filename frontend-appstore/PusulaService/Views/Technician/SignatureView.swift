import SwiftUI
import UIKit

/// Full-screen signature capture using a finger-first SwiftUI canvas.
/// The captured signature is converted to PNG base64 and uploaded to the backend.
struct SignatureView: View {
    let ticketId: Int
    
    @Environment(\.dismiss) private var dismiss
    @State private var strokes: [SignatureStroke] = []
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
                    SignatureDrawingCanvas(strokes: $strokes)
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
                    .disabled(isUploading || strokes.isEmpty)
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
        strokes.removeAll()
        errorMessage = nil
    }
    
    private func saveSignature() {
        isUploading = true
        errorMessage = nil
        
        guard !strokes.isEmpty else {
            errorMessage = "Lütfen önce imzanızı atın"
            isUploading = false
            return
        }

        let imageSize = CGSize(width: 1_200, height: 500)
        let format = UIGraphicsImageRendererFormat.default()
        format.scale = 1
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: imageSize, format: format)
        let image = renderer.image { context in
            UIColor.white.setFill()
            context.cgContext.fill(CGRect(origin: .zero, size: imageSize))
            renderSignature(strokes, in: context.cgContext, size: imageSize)
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

private struct SignatureStroke: Identifiable {
    let id = UUID()
    var points: [CGPoint]
}

private struct SignatureDrawingCanvas: View {
    @Binding var strokes: [SignatureStroke]
    @State private var isDrawing = false

    var body: some View {
        GeometryReader { proxy in
            Canvas { context, size in
                for stroke in strokes {
                    guard let first = stroke.points.first else { continue }
                    if stroke.points.count == 1 {
                        let point = denormalized(first, in: size)
                        context.fill(
                            Path(ellipseIn: CGRect(x: point.x - 1.5, y: point.y - 1.5, width: 3, height: 3)),
                            with: .color(.black)
                        )
                        continue
                    }

                    var path = Path()
                    path.move(to: denormalized(first, in: size))
                    for point in stroke.points.dropFirst() {
                        path.addLine(to: denormalized(point, in: size))
                    }
                    context.stroke(path, with: .color(.black), style: StrokeStyle(
                        lineWidth: 3,
                        lineCap: .round,
                        lineJoin: .round
                    ))
                }
            }
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .local)
                    .onChanged { value in
                        let point = normalized(value.location, in: proxy.size)
                        if !isDrawing {
                            strokes.append(SignatureStroke(points: [point]))
                            isDrawing = true
                        } else if !strokes.isEmpty {
                            strokes[strokes.count - 1].points.append(point)
                        }
                    }
                    .onEnded { _ in
                        isDrawing = false
                    }
            )
            .accessibilityLabel("İmza çizim alanı")
            .accessibilityHint("Parmağınızı veya Apple Pencil'ı sürükleyerek imza atın")
        }
    }

    private func normalized(_ point: CGPoint, in size: CGSize) -> CGPoint {
        CGPoint(
            x: min(max(point.x / max(size.width, 1), 0), 1),
            y: min(max(point.y / max(size.height, 1), 0), 1)
        )
    }

    private func denormalized(_ point: CGPoint, in size: CGSize) -> CGPoint {
        CGPoint(x: point.x * size.width, y: point.y * size.height)
    }
}

private func renderSignature(_ strokes: [SignatureStroke], in context: CGContext, size: CGSize) {
    context.setStrokeColor(UIColor.black.cgColor)
    context.setFillColor(UIColor.black.cgColor)
    context.setLineWidth(6)
    context.setLineCap(.round)
    context.setLineJoin(.round)

    for stroke in strokes {
        guard let first = stroke.points.first else { continue }
        let firstPoint = CGPoint(x: first.x * size.width, y: first.y * size.height)
        if stroke.points.count == 1 {
            context.fillEllipse(in: CGRect(x: firstPoint.x - 3, y: firstPoint.y - 3, width: 6, height: 6))
            continue
        }

        context.beginPath()
        context.move(to: firstPoint)
        for point in stroke.points.dropFirst() {
            context.addLine(to: CGPoint(x: point.x * size.width, y: point.y * size.height))
        }
        context.strokePath()
    }
}
