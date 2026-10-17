import Foundation
import XCTest
@testable import PusulaTickets

final class ServicePhotoUploadBatchTests: XCTestCase {
    func testSelectionIsBoundedAndEmptySelectionDoesNothing() {
        XCTAssertTrue(ServicePhotoUploadBatch<String, Data>(sources: [], type: "BEFORE", note: "").pendingIDs.isEmpty)
        let batch = ServicePhotoUploadBatch<Int, Data>(sources: Array(0..<20), type: "AFTER", note: "Frozen")
        XCTAssertEqual(batch.items.count, 10)
        XCTAssertEqual(Set(batch.pendingIDs).count, 10)
    }
    func testPartialFailureReplaysOnlyUnconfirmedWithFrozenKeyAndBytes() throws {
        var batch = ServicePhotoUploadBatch<String, Data>(sources: ["a", "b", "c"], type: "AFTER", note: "Frozen")
        let ids = batch.pendingIDs
        batch.prepare(ids[0], payload: Data([1])); batch.succeed(ids[0])
        batch.prepare(ids[1], payload: Data([2])); batch.fail(ids[1], message: "Lost response")
        batch.prepare(ids[2], payload: Data([3])); batch.succeed(ids[2])
        batch.prepare(ids[1], payload: Data([99])) // Re-encoding cannot change a replay.
        XCTAssertEqual(batch.pendingIDs, [ids[1]])
        XCTAssertEqual(batch.item(ids[1])?.payload, Data([2]))
        XCTAssertEqual(batch.type, "AFTER"); XCTAssertEqual(batch.note, "Frozen")
        XCTAssertEqual(batch.confirmedCount, 2)
        XCTAssertNil(batch.item(ids[0])?.payload)
        batch.succeed(ids[1]); batch.fail(ids[1], message: "Stale failure")
        XCTAssertTrue(batch.pendingIDs.isEmpty)
        XCTAssertNil(batch.item(ids[1])?.error)
    }
    func testUnreadableSourceRemainsRetryableAndNewBatchGetsNewKeys() {
        var batch = ServicePhotoUploadBatch<String, Data>(sources: ["unreadable"], type: "BEFORE", note: "")
        let id = batch.pendingIDs[0]
        batch.fail(id, message: "Cannot read")
        XCTAssertEqual(batch.item(id)?.source, "unreadable")
        batch.prepare(id, payload: Data([5]))
        XCTAssertNil(batch.item(id)?.source)
        let other = ServicePhotoUploadBatch<String, Data>(sources: ["unreadable"], type: "BEFORE", note: "")
        XCTAssertNotEqual(id, other.pendingIDs[0])
    }
}
