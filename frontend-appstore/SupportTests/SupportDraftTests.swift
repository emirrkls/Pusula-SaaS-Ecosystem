import XCTest
@testable import PusulaSupport

final class SupportDraftTests: XCTestCase {
    func testDisplayDatesRespectOffsetsNaiveBusinessTimeAndInvalidInput() {
        let expected=SupportPresentation.displayDate("2026-10-17T16:20:00Z")
        XCTAssertEqual(expected,SupportPresentation.displayDate("2026-10-17T19:20:00+03:00"))
        XCTAssertEqual(expected,SupportPresentation.displayDate("2026-10-17T19:20:00.000000"))
        XCTAssertNotEqual(expected,"2026-10-17T16:20:00Z")
        XCTAssertEqual(SupportPresentation.displayDate("invalid"),"invalid")
    }
    func testFrozenDraftRoundTripKeepsSameIdentityContentAndAttachment() throws {
        var draft=SupportDraft();draft.subject="TLS";draft.body="Bağlantı kurulamadı";draft.frozen=true;draft.ticketId=7
        draft.attachments=[SupportUpload(mediaType:"application/json",dataBase64:"e30=")]
        let restored=try JSONDecoder().decode(SupportDraft.self,from:JSONEncoder().encode(draft))
        XCTAssertEqual(restored,draft);XCTAssertEqual(restored.clientKey,draft.clientKey)
    }
    func testCreateAndReplyRequestsDoNotContainStorageFlagsOrAuthCredentials() throws {
        let draft=SupportDraft()
        let create=try XCTUnwrap(JSONSerialization.jsonObject(with:JSONEncoder().encode(SupportCreateRequest(draft))) as? [String:Any])
        XCTAssertNil(create["token"]);XCTAssertNil(create["companyId"]);XCTAssertNil(create["userId"]);XCTAssertNil(create["frozen"]);XCTAssertNil(create["ticketId"])
        let reply=try XCTUnwrap(JSONSerialization.jsonObject(with:JSONEncoder().encode(SupportReplyRequest(draft))) as? [String:Any])
        XCTAssertEqual(reply["clientKey"] as? String,draft.clientKey);XCTAssertEqual(reply["internalNote"] as? Bool,false);XCTAssertNil(reply["status"])
    }
    func testNewDraftHasNewKeyButCopyForReplayKeepsIt() {
        let first=SupportDraft();let replay=first
        XCTAssertEqual(first.clientKey,replay.clientKey);XCTAssertNotEqual(first.clientKey,SupportDraft().clientKey)
    }
    func testSubjectOnlyOrAttachmentOnlyDraftIsNotConsideredEmpty() {
        var draft=SupportDraft();XCTAssertFalse(draft.hasContent)
        draft.subject="Soru";XCTAssertTrue(draft.hasContent)
        draft=SupportDraft();draft.attachments=[SupportUpload(mediaType:"application/json",dataBase64:"e30=")];XCTAssertTrue(draft.hasContent)
    }
    func testTechnicalUploadsKeepDeclaredMediaTypeWithoutClientFilename() throws {
        let file=SupportUpload(mediaType:"image/jpeg",dataBase64:"AA==")
        let json=try XCTUnwrap(JSONSerialization.jsonObject(with:JSONEncoder().encode(file)) as? [String:Any])
        XCTAssertEqual(Set(json.keys),Set(["mediaType","dataBase64"]))
    }
}
