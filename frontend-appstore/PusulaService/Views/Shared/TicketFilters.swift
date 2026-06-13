import Foundation

enum TicketFilters {
    static let adminFilters = ["Atama Bekleyen", "Bugün Açılan", "Atanan", "Devam Eden", "Kapanan", "Tümü"]
    static let technicianFilters = ["Atanan", "Kapanan", "Tümü"]
    
    static func defaultFilter(isAdmin: Bool) -> String {
        isAdmin ? "Atama Bekleyen" : "Atanan"
    }
    
    static func matches(_ ticket: FieldTicketDTO, filter: String, isAdmin: Bool) -> Bool {
        if filter == "Tümü" { return true }
        let status = ticket.status?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() ?? ""
        
        switch filter {
        case "Atama Bekleyen":
            return ticket.assignedTechnicianId == nil && status == "PENDING"
        case "Bugün Açılan":
            return isTodayInBusinessZone(ticket.createdAt)
        case "Atanan":
            if isAdmin {
                return status == "ASSIGNED"
            }
            return status == "ASSIGNED" || status == "IN_PROGRESS"
        case "Devam Eden":
            return status == "IN_PROGRESS"
        case "Kapanan":
            return status == "COMPLETED" || status == "CANCELLED"
        default:
            return status == filter.uppercased()
        }
    }
    
    static func pendingUnassigned(_ tickets: [FieldTicketDTO]) -> [FieldTicketDTO] {
        tickets.filter {
            let status = $0.status?.uppercased() ?? ""
            return $0.assignedTechnicianId == nil && status == "PENDING"
        }
    }
    
    private static func isTodayInBusinessZone(_ dateRaw: String?) -> Bool {
        guard let dateRaw, !dateRaw.isEmpty else { return false }
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "en_US_POSIX")
        formatter.timeZone = TimeZone(identifier: "Europe/Istanbul")
        formatter.dateFormat = "yyyy-MM-dd"
        let today = formatter.string(from: Date())
        return dateRaw.hasPrefix(today) || dateRaw.contains(today)
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

func sharePDF(data: Data, fileName: String) {
    let url = FileManager.default.temporaryDirectory.appendingPathComponent(fileName)
    try? data.write(to: url)
    guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
          let root = scene.windows.first?.rootViewController else { return }
    let activity = UIActivityViewController(activityItems: [url], applicationActivities: nil)
    root.present(activity, animated: true)
}

import UIKit
