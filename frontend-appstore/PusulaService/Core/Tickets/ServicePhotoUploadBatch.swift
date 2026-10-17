import Foundation

/// One immutable intent/key per photo. An uncertain response must replay the
/// same bytes, category and note; confirmed photos are never sent again.
struct ServicePhotoUploadBatch<Source, Payload> {
    static var limit: Int { 10 }
    struct Item: Identifiable {
        let id = UUID()
        var source: Source?
        var payload: Payload?
        var confirmed = false
        var error: String?
    }
    let type: String
    let note: String
    private(set) var items: [Item]
    var pendingIDs: [UUID] { items.filter { !$0.confirmed }.map(\.id) }
    var confirmedCount: Int { items.filter(\.confirmed).count }

    init(sources: [Source], type: String, note: String) {
        self.type = type
        self.note = note
        items = sources.prefix(Self.limit).map { Item(source: $0) }
    }
    func item(_ id: UUID) -> Item? { items.first { $0.id == id } }
    mutating func prepare(_ id: UUID, payload: Payload) {
        guard let index = items.firstIndex(where: { $0.id == id }),
              !items[index].confirmed, items[index].payload == nil else { return }
        items[index].payload = payload
        items[index].source = nil
    }
    mutating func succeed(_ id: UUID) {
        guard let index = items.firstIndex(where: { $0.id == id }) else { return }
        items[index].confirmed = true
        items[index].payload = nil
        items[index].source = nil
        items[index].error = nil
    }
    mutating func fail(_ id: UUID, message: String) {
        guard let index = items.firstIndex(where: { $0.id == id }), !items[index].confirmed else { return }
        items[index].error = message
    }
}
