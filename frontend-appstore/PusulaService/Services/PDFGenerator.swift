import UIKit
import PDFKit

/// Generates a professional service form PDF matching the desktop app's format.
/// Includes: company logo, customer info, service details, used parts table,
/// payment breakdown, and customer signature.
class ServiceFormPDFGenerator {
    
    struct FormData {
        let companyName: String
        let customerName: String
        let customerPhone: String?
        let customerAddress: String?
        let ticketId: Int
        let description: String?
        let scheduledDate: String?
        let usedParts: [UsedPartDTO]
        let totalAmount: Double
        let collectedAmount: Double
        let paymentMethod: String
        let remainingDebt: Double
        let technicianName: String
        let signatureImage: UIImage?
    }
    
    static func generate(from data: FormData) -> Data {
        let pageWidth: CGFloat = 595.0  // A4
        let pageHeight: CGFloat = 842.0
        let margin: CGFloat = 40
        let contentWidth = pageWidth - (margin * 2)
        
        let renderer = UIGraphicsPDFRenderer(bounds: CGRect(x: 0, y: 0, width: pageWidth, height: pageHeight))
        
        return renderer.pdfData { context in
            context.beginPage()
            var y: CGFloat = margin
            
            // ── HEADER ──────────────────────────────────
            
            // Company name
            let titleAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 22, weight: .bold),
                .foregroundColor: UIColor(red: 0.1, green: 0.3, blue: 0.6, alpha: 1)
            ]
            let titleStr = data.companyName
            titleStr.draw(at: CGPoint(x: margin, y: y), withAttributes: titleAttrs)
            y += 32
            
            // "SERVİS FORMU" title
            let formTitleAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 16, weight: .semibold),
                .foregroundColor: UIColor.darkGray
            ]
            "SERVİS FORMU".draw(at: CGPoint(x: margin, y: y), withAttributes: formTitleAttrs)
            
            // Ticket number on right
            let ticketNoAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 14, weight: .bold),
                .foregroundColor: UIColor.black
            ]
            let ticketNo = "Fiş No: #\(data.ticketId)"
            let ticketNoSize = (ticketNo as NSString).size(withAttributes: ticketNoAttrs)
            ticketNo.draw(at: CGPoint(x: pageWidth - margin - ticketNoSize.width, y: y), withAttributes: ticketNoAttrs)
            y += 28
            
            // Date
            if let date = data.scheduledDate {
                let dateAttrs: [NSAttributedString.Key: Any] = [
                    .font: UIFont.systemFont(ofSize: 11),
                    .foregroundColor: UIColor.gray
                ]
                "Tarih: \(date)".draw(at: CGPoint(x: margin, y: y), withAttributes: dateAttrs)
                y += 20
            }
            
            // Separator line
            drawLine(at: y, width: contentWidth, margin: margin, context: context)
            y += 16
            
            // ── CUSTOMER INFO ──────────────────────────
            
            let sectionHeader: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 13, weight: .semibold),
                .foregroundColor: UIColor(red: 0.1, green: 0.3, blue: 0.6, alpha: 1)
            ]
            let bodyAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 11),
                .foregroundColor: UIColor.black
            ]
            
            "MÜŞTERİ BİLGİLERİ".draw(at: CGPoint(x: margin, y: y), withAttributes: sectionHeader)
            y += 20
            
            "Müşteri: \(data.customerName)".draw(at: CGPoint(x: margin, y: y), withAttributes: bodyAttrs)
            y += 16
            
            if let phone = data.customerPhone {
                "Telefon: \(phone)".draw(at: CGPoint(x: margin, y: y), withAttributes: bodyAttrs)
                y += 16
            }
            
            if let address = data.customerAddress {
                "Adres: \(address)".draw(at: CGPoint(x: margin, y: y), withAttributes: bodyAttrs)
                y += 16
            }
            
            y += 8
            drawLine(at: y, width: contentWidth, margin: margin, context: context)
            y += 16
            
            // ── SERVICE DESCRIPTION ────────────────────
            
            "SERVİS AÇIKLAMASI".draw(at: CGPoint(x: margin, y: y), withAttributes: sectionHeader)
            y += 20
            
            let description = data.description ?? "Belirtilmedi"
            let descRect = CGRect(x: margin, y: y, width: contentWidth, height: 60)
            (description as NSString).draw(in: descRect, withAttributes: bodyAttrs)
            y += 68
            
            drawLine(at: y, width: contentWidth, margin: margin, context: context)
            y += 16
            
            // ── USED PARTS TABLE ───────────────────────
            
            "KULLANILAN PARÇALAR".draw(at: CGPoint(x: margin, y: y), withAttributes: sectionHeader)
            y += 22
            
            // Table header
            let tableHeaderAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 10, weight: .semibold),
                .foregroundColor: UIColor.white
            ]
            
            let headerBg = UIColor(red: 0.1, green: 0.3, blue: 0.6, alpha: 1)
            let headerRect = CGRect(x: margin, y: y, width: contentWidth, height: 22)
            headerBg.setFill()
            UIBezierPath(roundedRect: headerRect, cornerRadius: 4).fill()
            
            "Parça Adı".draw(at: CGPoint(x: margin + 8, y: y + 4), withAttributes: tableHeaderAttrs)
            "Adet".draw(at: CGPoint(x: margin + 300, y: y + 4), withAttributes: tableHeaderAttrs)
            "Birim Fiyat".draw(at: CGPoint(x: margin + 370, y: y + 4), withAttributes: tableHeaderAttrs)
            "Toplam".draw(at: CGPoint(x: margin + 460, y: y + 4), withAttributes: tableHeaderAttrs)
            y += 26
            
            // Table rows
            let rowAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 10),
                .foregroundColor: UIColor.black
            ]
            let boldRowAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 10, weight: .semibold),
                .foregroundColor: UIColor.black
            ]
            
            for (index, part) in data.usedParts.enumerated() {
                if index % 2 == 0 {
                    UIColor(white: 0.95, alpha: 1).setFill()
                    let rowRect = CGRect(x: margin, y: y, width: contentWidth, height: 18)
                    UIBezierPath(rect: rowRect).fill()
                }
                
                part.partName.draw(at: CGPoint(x: margin + 8, y: y + 2), withAttributes: rowAttrs)
                "\(part.quantityUsed)".draw(at: CGPoint(x: margin + 308, y: y + 2), withAttributes: rowAttrs)
                "₺\(String(format: "%.2f", part.sellingPriceSnapshot))".draw(
                    at: CGPoint(x: margin + 370, y: y + 2), withAttributes: rowAttrs)
                "₺\(String(format: "%.2f", part.totalPrice))".draw(
                    at: CGPoint(x: margin + 460, y: y + 2), withAttributes: boldRowAttrs)
                y += 20
            }
            
            y += 8
            drawLine(at: y, width: contentWidth, margin: margin, context: context)
            y += 16
            
            // ── PAYMENT SUMMARY ────────────────────────
            
            "ÖDEME BİLGİLERİ".draw(at: CGPoint(x: margin, y: y), withAttributes: sectionHeader)
            y += 22
            
            let rightAlignX: CGFloat = margin + 360
            
            drawLabelValue("Toplam Tutar:", "₺\(String(format: "%.2f", data.totalAmount))", at: y, margin: margin, valueX: rightAlignX, attrs: bodyAttrs)
            y += 18
            drawLabelValue("Tahsil Edilen:", "₺\(String(format: "%.2f", data.collectedAmount))", at: y, margin: margin, valueX: rightAlignX, attrs: bodyAttrs)
            y += 18
            drawLabelValue("Ödeme Yöntemi:", data.paymentMethod, at: y, margin: margin, valueX: rightAlignX, attrs: bodyAttrs)
            y += 18
            
            if data.remainingDebt > 0 {
                let debtAttrs: [NSAttributedString.Key: Any] = [
                    .font: UIFont.systemFont(ofSize: 12, weight: .bold),
                    .foregroundColor: UIColor.red
                ]
                drawLabelValue("Kalan Borç (Cari):", "₺\(String(format: "%.2f", data.remainingDebt))", at: y, margin: margin, valueX: rightAlignX, attrs: debtAttrs)
                y += 20
            }
            
            y += 16
            
            // ── TECHNICIAN ─────────────────────────────
            
            "Teknisyen: \(data.technicianName)".draw(at: CGPoint(x: margin, y: y), withAttributes: bodyAttrs)
            y += 30
            
            // ── SIGNATURE ──────────────────────────────
            
            drawLine(at: y, width: contentWidth, margin: margin, context: context)
            y += 16
            
            "MÜŞTERİ İMZASI".draw(at: CGPoint(x: margin, y: y), withAttributes: sectionHeader)
            y += 24
            
            if let signature = data.signatureImage {
                let sigRect = CGRect(x: margin, y: y, width: 200, height: 80)
                signature.draw(in: sigRect)
                y += 90
            } else {
                // Empty signature box
                let sigRect = CGRect(x: margin, y: y, width: 200, height: 80)
                UIColor.lightGray.setStroke()
                let path = UIBezierPath(roundedRect: sigRect, cornerRadius: 8)
                path.lineWidth = 0.5
                path.stroke()
                y += 90
            }
            
            // ── FOOTER ─────────────────────────────────
            
            let footerAttrs: [NSAttributedString.Key: Any] = [
                .font: UIFont.systemFont(ofSize: 8),
                .foregroundColor: UIColor.lightGray
            ]
            let footerText = "Pusula Servis Yönetim Sistemi • Bu belge dijital olarak oluşturulmuştur."
            footerText.draw(at: CGPoint(x: margin, y: pageHeight - 30), withAttributes: footerAttrs)
        }
    }
    
    // MARK: - Drawing Helpers
    
    private static func drawLine(at y: CGFloat, width: CGFloat, margin: CGFloat, context: UIGraphicsPDFRendererContext) {
        let path = UIBezierPath()
        path.move(to: CGPoint(x: margin, y: y))
        path.addLine(to: CGPoint(x: margin + width, y: y))
        UIColor.lightGray.setStroke()
        path.lineWidth = 0.5
        path.stroke()
    }
    
    private static func drawLabelValue(_ label: String, _ value: String, at y: CGFloat, margin: CGFloat, valueX: CGFloat, attrs: [NSAttributedString.Key: Any]) {
        label.draw(at: CGPoint(x: margin, y: y), withAttributes: attrs)
        value.draw(at: CGPoint(x: valueX, y: y), withAttributes: attrs)
    }
}
