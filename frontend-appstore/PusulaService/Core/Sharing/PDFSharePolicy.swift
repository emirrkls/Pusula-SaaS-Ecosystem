import Foundation

enum PDFShareFailure: String, Error, Equatable {
    case unavailableFile = "pdf.share.file_unavailable"
    case invalidDocument = "pdf.share.invalid_document"
    case presentationUnavailable = "pdf.share.presentation_unavailable"
    case systemFailure = "pdf.share.system_failure"
}

enum PDFShareFile {
    /// Recheck immediately before sharing: a previously previewed temporary file
    /// may have disappeared. Never send an arbitrary web URL or a directory.
    static func validate(_ url: URL) throws {
        guard url.isFileURL, url.pathExtension.lowercased() == "pdf" else {
            throw PDFShareFailure.invalidDocument
        }
        do {
            let values = try url.resourceValues(forKeys: [.isRegularFileKey, .isSymbolicLinkKey])
            guard values.isRegularFile == true, values.isSymbolicLink != true,
                  FileManager.default.isReadableFile(atPath: url.path) else {
                throw PDFShareFailure.unavailableFile
            }
            let file = try FileHandle(forReadingFrom: url)
            defer { try? file.close() }
            guard try file.read(upToCount: 5) == Data("%PDF-".utf8) else {
                throw PDFShareFailure.invalidDocument
            }
        } catch let failure as PDFShareFailure { throw failure }
        catch { throw PDFShareFailure.unavailableFile }
    }
}

/// A late completion/timeout from one presentation cannot close a newer share.
struct PDFSharePresentationState {
    enum Phase: Equatable { case idle, presenting, active, dismissing }
    private(set) var phase: Phase = .idle
    private(set) var lease: UUID?

    mutating func begin() -> UUID? {
        guard phase == .idle else { return nil }
        let id = UUID()
        lease = id
        phase = .presenting
        return id
    }

    @discardableResult
    mutating func didPresent(_ id: UUID) -> Bool {
        guard lease == id, phase == .presenting else { return false }
        phase = .active
        return true
    }

    func isAwaitingPresentation(_ id: UUID) -> Bool { lease == id && phase == .presenting }

    mutating func beginFinish(_ id: UUID) -> Bool {
        guard lease == id, phase == .presenting || phase == .active else { return false }
        phase = .dismissing
        return true
    }

    @discardableResult
    mutating func finish(_ id: UUID) -> Bool {
        guard lease == id, phase == .dismissing else { return false }
        lease = nil
        phase = .idle
        return true
    }
}
