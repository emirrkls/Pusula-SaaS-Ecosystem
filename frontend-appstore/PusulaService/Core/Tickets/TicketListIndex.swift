import Foundation

protocol TicketListRecord: Sendable {
    var id: Int { get }
    var status: String? { get }
    var assignedTechnicianId: Int? { get }
    var scheduledDate: String? { get }
    var scheduledEndDate: String? { get }
    var createdAt: String? { get }
    var completedAt: String? { get }
    var updatedAt: String? { get }
    var ticketSearchValues: [String] { get }
}

struct TicketListQuery: Equatable, Sendable {
    static let adminFilters = ["Atama Bekleyen", "Bugün Açılan", "Atanan", "İşlemde", "Tamamlanan", "İptal Edilen", "Tümü"]
    static let technicianFilters = ["Bugünkü İşler", "İleri Tarihli İşler", "Tamamlanan", "İptal Edilen"]
    var filter: String
    var isAdmin: Bool
    var search: String = ""
    var startDate: Date? = nil
    var endDate: Date? = nil
    var businessDay: Date

    var isAscending: Bool {
        ["Bugünkü İşler", "Bugünün Çağrıları", "İleri Tarihli İşler", "İleri Tarihli"].contains(filter)
    }
}

struct TicketProjectionRequest: Equatable, Sendable {
    let revision: UUID
    let companyID: Int?
    let query: TicketListQuery
    let isActive: Bool

    func accepts(_ completed: Self, isCancelled: Bool) -> Bool {
        isActive && !isCancelled && self == completed
    }
}

struct TicketListRow<Record: TicketListRecord>: Identifiable, Sendable {
    let ticket: Record
    let status: String
    let scheduleLabel: String?
    let scheduledDay: Date?
    let createdDay: Date?
    let relevantDate: Date?
    let relevantDay: Date?
    let searchValues: [String]
    var id: Int { ticket.id }

    func isOverdue(on businessDay: Date) -> Bool {
        guard status == "ASSIGNED" || status == "IN_PROGRESS", let scheduledDay else { return false }
        return scheduledDay < businessDay
    }
}

struct TicketListSnapshot<Record: TicketListRecord>: Sendable {
    let rows: [TicketListRow<Record>]
    let pendingUnassigned: [Record]
}

/// One index per server-data revision. Parsing, formatting, search normalization
/// and both sort orders happen here, never inside a SwiftUI body/comparator.
struct TicketListIndex<Record: TicketListRecord>: Sendable {
    private let ascending: [TicketListRow<Record>]
    private let descending: [TicketListRow<Record>]
    private let pending: [Record]
    private static var searchLocale: Locale { Locale(identifier: "tr_TR") }

    static func normalize(_ text: String) -> String {
        // Lowercase before removing accents so Turkish İ becomes i, not ı.
        // Combining both folding options produces different ICU results across hosts.
        text.lowercased(with: searchLocale)
            .folding(options: [.diacriticInsensitive], locale: searchLocale)
    }

    init(records: [Record], parseDate: (String?) -> Date? = BusinessDateParser.parse) throws {
        // These two formatters are local to this background index build. Their
        // configuration never changes per ticket and they are not shared across actors.
        let schedule = DateFormatter()
        schedule.calendar = BusinessDateParser.calendar
        schedule.locale = Locale(identifier: "tr_TR")
        schedule.timeZone = BusinessDateParser.timeZone
        schedule.dateFormat = "d MMM HH:mm"
        let time = DateFormatter()
        time.calendar = BusinessDateParser.calendar
        time.locale = Locale(identifier: "tr_TR")
        time.timeZone = BusinessDateParser.timeZone
        time.dateFormat = "HH:mm"
        var rows: [TicketListRow<Record>] = []
        var pending: [Record] = []
        rows.reserveCapacity(records.count)
        for (offset, record) in records.enumerated() {
            if offset.isMultiple(of: 64) { try Task.checkCancellation() }
            let status = record.status?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() ?? ""
            let start = parseDate(record.scheduledDate)
            let end = parseDate(record.scheduledEndDate)
            let created = parseDate(record.createdAt)
            let relevant: Date?
            switch status {
            case "COMPLETED": relevant = parseDate(record.completedAt) ?? start ?? created
            case "CANCELLED": relevant = parseDate(record.updatedAt) ?? start ?? created
            default: relevant = start ?? created
            }
            var label = start.map(schedule.string(from:)) ?? record.scheduledDate
            if start != nil, let end { label = (label ?? "") + "–" + time.string(from: end) }
            rows.append(TicketListRow(
                ticket: record, status: status, scheduleLabel: label,
                scheduledDay: start.map(BusinessDateParser.startOfDay),
                createdDay: created.map(BusinessDateParser.startOfDay), relevantDate: relevant,
                relevantDay: relevant.map(BusinessDateParser.startOfDay),
                searchValues: record.ticketSearchValues.map(Self.normalize)
            ))
            if record.assignedTechnicianId == nil, status == "PENDING" { pending.append(record) }
        }
        func ordered(_ ascending: Bool) throws -> [TicketListRow<Record>] {
            var comparisons = 0
            return try rows.sorted { left, right in
                comparisons += 1
                if comparisons.isMultiple(of: 256) { try Task.checkCancellation() }
                switch (left.relevantDate, right.relevantDate) {
                case let (lhs?, rhs?) where lhs != rhs: return ascending ? lhs < rhs : lhs > rhs
                case (_?, nil): return true
                case (nil, _?): return false
                default: return left.id > right.id
                }
            }
        }
        ascending = try ordered(true)
        descending = try ordered(false)
        self.pending = pending
        try Task.checkCancellation()
    }

    func snapshot(for query: TicketListQuery) throws -> TicketListSnapshot<Record> {
        let term = Self.normalize(query.search.trimmingCharacters(in: .whitespacesAndNewlines))
        let start = query.startDate.map(BusinessDateParser.startOfDay)
        let end = query.endDate.map(BusinessDateParser.startOfDay)
        let today = BusinessDateParser.startOfDay(query.businessDay)
        var result: [TicketListRow<Record>] = []
        for (offset, row) in (query.isAscending ? ascending : descending).enumerated() {
            if offset.isMultiple(of: 128) { try Task.checkCancellation() }
            guard matches(row, query: query, today: today) else { continue }
            if !term.isEmpty, !row.searchValues.contains(where: { $0.contains(term) }) { continue }
            if start != nil || end != nil {
                guard let day = row.relevantDay else { continue }
                if let start, day < start { continue }
                if let end, day > end { continue }
            }
            result.append(row)
        }
        try Task.checkCancellation()
        return TicketListSnapshot(rows: result, pendingUnassigned: pending)
    }

    private func matches(_ row: TicketListRow<Record>, query: TicketListQuery, today: Date) -> Bool {
        let active = row.status == "ASSIGNED" || row.status == "IN_PROGRESS"
        switch query.filter {
        case "Tümü": return true
        case "Atama Bekleyen": return row.ticket.assignedTechnicianId == nil && row.status == "PENDING"
        case "Bugün Açılan": return row.createdDay == today
        case "Atanan": return query.isAdmin ? row.status == "ASSIGNED" : active
        case "Bugünkü İşler", "Bugünün Çağrıları": return active && (row.scheduledDay.map { $0 <= today } ?? true)
        case "İleri Tarihli İşler", "İleri Tarihli": return active && (row.scheduledDay.map { $0 > today } ?? false)
        case "İşlemde", "Devam Eden": return row.status == "IN_PROGRESS"
        case "Tamamlanan", "Kapanan": return row.status == "COMPLETED"
        case "İptal Edilen": return row.status == "CANCELLED"
        default: return row.status == query.filter.uppercased()
        }
    }
}

/// An independent actor, not MainActor: even the first list after session restore
/// must not spend the UI watchdog budget parsing thousands of timestamps.
actor TicketListProcessor<Record: TicketListRecord> {
    private var revision: UUID?
    private var index: TicketListIndex<Record>?

    func project(records: [Record], request: TicketProjectionRequest) throws -> TicketListSnapshot<Record> {
        try Task.checkCancellation()
        guard request.isActive else { throw CancellationError() }
        if revision != request.revision {
            let prepared = try TicketListIndex(records: records)
            try Task.checkCancellation()
            index = prepared
            revision = request.revision
        }
        guard let index else { throw CancellationError() }
        return try index.snapshot(for: request.query)
    }
}
