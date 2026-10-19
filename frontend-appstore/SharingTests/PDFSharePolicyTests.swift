import Foundation
import XCTest
@testable import PusulaSharing

final class PDFSharePolicyTests: XCTestCase {
    private func withFolder(_ work: (URL) throws -> Void) throws {
        let folder = FileManager.default.temporaryDirectory.appendingPathComponent(UUID().uuidString, isDirectory: true)
        try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
        defer { try? FileManager.default.removeItem(at: folder) }
        try work(folder)
    }

    func testReadableLocalPDFHeaderIsAcceptedIncludingUppercaseExtension() throws {
        try withFolder { folder in
            let url = folder.appendingPathComponent("synthetic.PDF")
            try Data("%PDF-1.7\nSynthetic fixture".utf8).write(to: url)
            XCTAssertNoThrow(try PDFShareFile.validate(url))
        }
    }

    func testMissingOrRemovedFileFailsBeforePresentation() throws {
        try withFolder { folder in
            let url = folder.appendingPathComponent("synthetic.pdf")
            XCTAssertThrowsError(try PDFShareFile.validate(url)) { XCTAssertEqual($0 as? PDFShareFailure, .unavailableFile) }
            try Data("%PDF-1.7".utf8).write(to: url)
            try PDFShareFile.validate(url)
            try FileManager.default.removeItem(at: url)
            XCTAssertThrowsError(try PDFShareFile.validate(url)) { XCTAssertEqual($0 as? PDFShareFailure, .unavailableFile) }
        }
    }

    func testEmptyTruncatedAndNonPDFContentIsRejected() throws {
        try withFolder { folder in
            let url = folder.appendingPathComponent("synthetic.pdf")
            for data in [Data(), Data("%PDF".utf8), Data("<html>error</html>".utf8)] {
                try data.write(to: url)
                XCTAssertThrowsError(try PDFShareFile.validate(url)) { XCTAssertEqual($0 as? PDFShareFailure, .invalidDocument) }
            }
        }
    }

    func testWebURLAndWrongExtensionCannotBeSharedAsPDF() {
        for url in [URL(string: "https://example.invalid/document.pdf")!, URL(fileURLWithPath: "/tmp/synthetic.txt")] {
            XCTAssertThrowsError(try PDFShareFile.validate(url)) { XCTAssertEqual($0 as? PDFShareFailure, .invalidDocument) }
        }
    }

    func testDirectoryAndSymlinkAreNotShared() throws {
        try withFolder { folder in
            let directory = folder.appendingPathComponent("directory.pdf", isDirectory: true)
            try FileManager.default.createDirectory(at: directory, withIntermediateDirectories: true)
            XCTAssertThrowsError(try PDFShareFile.validate(directory)) { XCTAssertEqual($0 as? PDFShareFailure, .unavailableFile) }
            let original = folder.appendingPathComponent("original.pdf")
            try Data("%PDF-1.7".utf8).write(to: original)
            let link = folder.appendingPathComponent("link.pdf")
            try FileManager.default.createSymbolicLink(at: link, withDestinationURL: original)
            XCTAssertThrowsError(try PDFShareFile.validate(link)) { XCTAssertEqual($0 as? PDFShareFailure, .unavailableFile) }
        }
    }

    func testDuplicateTapCannotStartAnotherPresentation() throws {
        var state = PDFSharePresentationState()
        let lease = try XCTUnwrap(state.begin())
        XCTAssertNil(state.begin())
        XCTAssertTrue(state.didPresent(lease))
        XCTAssertNil(state.begin())
        XCTAssertTrue(state.beginFinish(lease))
        XCTAssertNil(state.begin())
        XCTAssertTrue(state.finish(lease))
        XCTAssertNotNil(state.begin())
    }

    func testCancellationAndCompletionAllowReopeningWithANewLease() throws {
        var state = PDFSharePresentationState()
        let first = try XCTUnwrap(state.begin())
        XCTAssertTrue(state.didPresent(first))
        XCTAssertTrue(state.beginFinish(first))
        XCTAssertTrue(state.finish(first))
        XCTAssertEqual(state.phase, .idle)
        let second = try XCTUnwrap(state.begin())
        XCTAssertNotEqual(first, second)
        XCTAssertFalse(state.finish(first))
        XCTAssertFalse(state.beginFinish(first))
        XCTAssertEqual(state.lease, second)
        XCTAssertEqual(state.phase, .presenting)
    }

    func testTimeoutCannotTerminateAnAlreadyVisibleShare() throws {
        var state = PDFSharePresentationState()
        let lease = try XCTUnwrap(state.begin())
        XCTAssertTrue(state.isAwaitingPresentation(lease))
        XCTAssertTrue(state.didPresent(lease))
        XCTAssertFalse(state.isAwaitingPresentation(lease))
    }

    func testFailedPresentationRecoversAndIgnoresItsLateCallbacks() throws {
        var state = PDFSharePresentationState()
        let failed = try XCTUnwrap(state.begin())
        XCTAssertTrue(state.beginFinish(failed))
        XCTAssertFalse(state.didPresent(failed))
        XCTAssertTrue(state.finish(failed))
        let retry = try XCTUnwrap(state.begin())
        XCTAssertFalse(state.isAwaitingPresentation(failed))
        XCTAssertFalse(state.didPresent(failed))
        XCTAssertFalse(state.beginFinish(failed))
        XCTAssertEqual(state.lease, retry)
    }

    func testRepeatedDismissalCannotReleaseOrFinishANewerPresentation() throws {
        var state = PDFSharePresentationState()
        let lease = try XCTUnwrap(state.begin())
        XCTAssertTrue(state.beginFinish(lease))
        XCTAssertFalse(state.beginFinish(lease))
        XCTAssertTrue(state.finish(lease))
        XCTAssertFalse(state.finish(lease))
    }
}
