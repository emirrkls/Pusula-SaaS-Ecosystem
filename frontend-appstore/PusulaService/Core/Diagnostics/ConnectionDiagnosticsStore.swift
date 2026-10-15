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
              let handle = try? FileHandle(forReadingFrom: file) else { return }
        defer { try? handle.close() }
        // Bounded content read; no file timestamps or disk-space metadata.
        guard let data = try? handle.read(upToCount: 512 * 1_024 + 1),
              data.count <= 512 * 1_024 else { return }
        let decoder = Self.decoder()
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
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys, .withoutEscapingSlashes]
        let timestamp = Date.ISO8601FormatStyle(includingFractionalSeconds: true)
        encoder.dateEncodingStrategy = .custom { date, encoder in
            var value = encoder.singleValueContainer()
            try value.encode(timestamp.format(date))
        }
        return encoder
    }

    static func decoder() -> JSONDecoder {
        let decoder = JSONDecoder()
        let precise = Date.ISO8601FormatStyle(includingFractionalSeconds: true)
        let legacy = Date.ISO8601FormatStyle()
        decoder.dateDecodingStrategy = .custom { decoder in
            let value = try decoder.singleValueContainer()
            let text = try value.decode(String.self)
            guard let date = (try? precise.parse(text)) ?? (try? legacy.parse(text)) else {
                throw DecodingError.dataCorruptedError(in: value, debugDescription: "Invalid diagnostic timestamp")
            }
            return date
        }
        return decoder
    }
}
