import Foundation

actor ConnectionDiagnosticsStore {
    static let shared = ConnectionDiagnosticsStore(directory: defaultDirectory())
    private let directory: URL?
    private let limit: Int
    private let retention: TimeInterval
    private var records: [ConnectionDiagnosticRecord] = []
    private var didRestore = false
    private var generation = UUID()
    private(set) var storageAvailable = true

    init(directory: URL? = nil, limit: Int = 100, retention: TimeInterval = 24 * 60 * 60) {
        self.directory = directory
        self.limit = max(1, min(limit, 100))
        self.retention = max(1, min(retention, 24 * 60 * 60))
    }

    static func defaultDirectory() -> URL? {
        FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first?
            .appendingPathComponent("ConnectionDiagnostics", isDirectory: true)
    }

    func currentGeneration() -> UUID { generation }

    func append(_ record: ConnectionDiagnosticRecord, generation expected: UUID, now: Date = Date()) {
        guard generation == expected else { return }
        restoreIfNeeded(now: now)
        records.append(record.redacted())
        prune(now: now)
        persist()
    }

    func report(metadata: ConnectionReportMetadata, now: Date = Date()) -> ConnectionDiagnosticReport {
        restoreIfNeeded(now: now)
        prune(now: now)
        persist()
        return ConnectionDiagnosticReport(generatedAt: now, metadata: metadata, records: records.map { $0.redacted() })
    }

    func export(metadata: ConnectionReportMetadata, now: Date = Date()) throws -> (URL, ConnectionDiagnosticReport) {
        let report = report(metadata: metadata, now: now)
        guard let directory else { throw CocoaError(.fileWriteUnknown) }
        try prepareDirectory(directory)
        // One replaceable file, not an unbounded trail of exported copies.
        let url = directory.appendingPathComponent("Pusula-Connection-Report.json")
        try protectedWrite(try Self.encoder().encode(report), to: url)
        return (url, report)
    }

    @discardableResult func clear() -> Bool {
        generation = UUID() // Reject completions of requests started before clearing.
        didRestore = true
        records = []
        guard let directory else { return true }
        var success = true
        for name in ["records.json", "Pusula-Connection-Report.json"] {
            let url = directory.appendingPathComponent(name)
            if FileManager.default.fileExists(atPath: url.path) {
                do { try FileManager.default.removeItem(at: url) }
                catch { storageAvailable = false; success = false }
            }
        }
        return success
    }

    private func restoreIfNeeded(now: Date) {
        guard !didRestore else { return }
        didRestore = true
        guard let file = directory?.appendingPathComponent("records.json"),
              let size = (try? FileManager.default.attributesOfItem(atPath: file.path)[.size]) as? NSNumber,
              size.intValue <= 512 * 1_024,
              let data = try? Data(contentsOf: file), data.count <= 512 * 1_024 else { return }
        let decoder = JSONDecoder()
        decoder.dateDecodingStrategy = .iso8601
        if let decoded = try? decoder.decode([ConnectionDiagnosticRecord].self, from: data) {
            records = Array(decoded.suffix(limit)).map { $0.redacted() }
            prune(now: now)
        }
    }

    private func prune(now: Date) {
        records = records.filter { $0.startedAt <= now && now.timeIntervalSince($0.startedAt) <= retention }
            .sorted { $0.startedAt < $1.startedAt }
        records = Array(records.suffix(limit))
    }

    private func persist() {
        guard let directory else { return }
        do {
            try prepareDirectory(directory)
            try protectedWrite(try Self.encoder().encode(records), to: directory.appendingPathComponent("records.json"))
            storageAvailable = true
        } catch { storageAvailable = false } // A diagnostics failure must never fail the user's request.
    }

    private func prepareDirectory(_ url: URL) throws {
        try FileManager.default.createDirectory(at: url, withIntermediateDirectories: true)
        var location = url
        var values = URLResourceValues()
        values.isExcludedFromBackup = true
        try location.setResourceValues(values)
    }

    private func protectedWrite(_ data: Data, to url: URL) throws {
#if os(iOS)
        try data.write(to: url, options: [.atomic, .completeFileProtectionUntilFirstUserAuthentication])
#else
        try data.write(to: url, options: .atomic)
#endif
    }

    static func encoder() -> JSONEncoder {
        let encoder = JSONEncoder()
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        return encoder
    }
}
