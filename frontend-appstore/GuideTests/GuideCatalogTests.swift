import XCTest
@testable import PusulaGuide

final class GuideCatalogTests: XCTestCase {
    private func data() throws -> Data {
        let root = URL(fileURLWithPath: #filePath).deletingLastPathComponent().deletingLastPathComponent()
        return try Data(contentsOf: root.appendingPathComponent("PusulaService/Core/Guide/app-guide.json"))
    }
    func testCatalogHasCompleteRoleAndPlatformCoverage() throws {
        let catalog = try GuideCatalog.decode(data())
        XCTAssertEqual(catalog.categories.count, 10)
        XCTAssertEqual(catalog.topics.count, 56)
        let technician = catalog.visibleTopics(role: "TECHNICIAN", platform: "ios")
        XCTAssertFalse(technician.contains { $0.id == "company-settings" })
        XCTAssertTrue(technician.contains { $0.id == "tickets-notes" })
        XCTAssertFalse(catalog.visibleTopics(role: "COMPANY_ADMIN", platform: "android").contains { $0.id == "apple-login" })
        XCTAssertTrue(catalog.visibleTopics(role: "COMPANY_ADMIN", platform: "ios").contains { $0.id == "discount" })
        XCTAssertTrue(catalog.visibleTopics(role: "INVALID", platform: "ios").isEmpty)
    }
    func testTurkishSearchAndCategoryFilter() throws {
        let catalog = try GuideCatalog.decode(data())
        XCTAssertTrue(catalog.visibleTopics(role: "COMPANY_ADMIN", platform: "ios", query: "ISKONTO").contains { $0.id == "discount" })
        XCTAssertTrue(catalog.visibleTopics(role: "TECHNICIAN", platform: "ios", query: "NOTLAR").contains { $0.id == "tickets-notes" })
        XCTAssertTrue(catalog.visibleTopics(role: "COMPANY_ADMIN", platform: "ios", category: "payments").allSatisfy { $0.category == "payments" })
        XCTAssertTrue(catalog.visibleTopics(role: "TECHNICIAN", platform: "ios", query: "olmayan-konu-123").isEmpty)
    }
    func testEveryNextPressAdvancesAndLastCompletes() {
        for count in 1...6 {
            var cursor = GuideCursor(count: count)
            cursor.back()
            XCTAssertEqual(cursor.index, 0)
            for index in 0..<count {
                XCTAssertEqual(cursor.index, index)
                XCTAssertEqual(cursor.advance(), index == count - 1)
            }
            XCTAssertEqual(cursor.index, count - 1)
            XCTAssertTrue(cursor.advance())
        }
        XCTAssertEqual(GuideCursor(count: 0, index: 99).index, 0)
        XCTAssertEqual(GuideCursor(count: 3, index: -3).index, 0)
        XCTAssertEqual(GuideCursor(count: 3, index: 99).index, 2)
    }
    func testResumeReplayAndNewRevision() throws {
        let topic = try GuideCatalog.decode(data()).topics[0]
        XCTAssertEqual(GuideRecord(step: 1).startIndex(topic), 1)
        XCTAssertEqual(GuideRecord(step: 999).startIndex(topic), topic.steps.count - 1)
        XCTAssertEqual(GuideRecord(step: 2, completedRevision: topic.revision).startIndex(topic), 0)
        XCTAssertFalse(GuideRecord(completedRevision: topic.revision - 1).isComplete(topic))
    }
    func testMalformedCatalogRejected() throws {
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: data()) as? [String: Any])
        object["schemaVersion"] = 99
        XCTAssertThrowsError(try GuideCatalog.decode(JSONSerialization.data(withJSONObject: object)))
        object["schemaVersion"] = 1
        var topics = try XCTUnwrap(object["topics"] as? [[String: Any]])
        topics[0]["steps"] = []
        object["topics"] = topics
        XCTAssertThrowsError(try GuideCatalog.decode(JSONSerialization.data(withJSONObject: object)))
    }
}
