import Foundation
import CryptoKit

enum SupportService {
    static func unread(token:String) async throws -> Int { let value:[String:Int]=try await NetworkManager.shared.request(.GET,path:"/api/support/unread-count",authTokenOverride:token);return value["count"] ?? 0 }
    static func contact() async throws -> SupportContact { try await NetworkManager.shared.get("/api/public/support-contact", requiresAuth: false) }
    static func list(page: Int, token: String) async throws -> SupportPageDTO { try await NetworkManager.shared.request(.GET,path:"/api/support/tickets?page=\(page)",authTokenOverride:token) }
    static func detail(id: Int, before: Int? = nil, token: String) async throws -> SupportDetailDTO {
        try await NetworkManager.shared.request(.GET,path:"/api/support/tickets/\(id)" + (before.map { "?before=\($0)" } ?? ""),authTokenOverride:token)
    }
    static func send(_ draft: SupportDraft, token: String) async throws -> SupportDetailDTO {
        if let id = draft.ticketId { return try await NetworkManager.shared.request(.POST,path:"/api/support/tickets/\(id)/messages",body:SupportReplyRequest(draft),authTokenOverride:token) }
        return try await NetworkManager.shared.request(.POST,path:"/api/support/tickets",body:SupportCreateRequest(draft),authTokenOverride:token)
    }
    static func markRead(id: Int, token: String) async throws {
        let _: EmptyResponse = try await NetworkManager.shared.request(.PATCH,path:"/api/support/tickets/\(id)/read",authTokenOverride:token)
    }
    // Only for partitioning protected local drafts. Never used as authorization or displayed.
    static func accountKey(_ token: String, company: Int) -> String? {
        let parts=token.split(separator:"."); guard parts.count==3 else { return nil }
        var part=String(parts[1]).replacingOccurrences(of:"-",with:"+").replacingOccurrences(of:"_",with:"/")
        while part.count % 4 != 0 { part += "=" }
        guard let data=Data(base64Encoded:part),let json=try? JSONSerialization.jsonObject(with:data) as? [String:Any],let subject=json["sub"] as? String else { return nil }
        return SHA256.hash(data:Data("\(company):\(subject)".utf8)).map { String(format:"%02x",$0) }.joined()
    }
}

actor SupportDraftStore {
    static let shared = SupportDraftStore()
    private var folder: URL? { FileManager.default.urls(for:.applicationSupportDirectory,in:.userDomainMask).first?.appendingPathComponent("SupportDrafts",isDirectory:true) }
    private func url(_ key: String) -> URL? { guard key.count==64,key.allSatisfy({ $0.isHexDigit }) else { return nil };return folder?.appendingPathComponent(key+".json") }
    func load(_ key: String) -> SupportDraft? {
        guard let file=url(key), let size=try? file.resourceValues(forKeys:[.fileSizeKey]).fileSize,size<=9_000_000,let data=try? Data(contentsOf:file) else { return nil }
        return try? JSONDecoder().decode(SupportDraft.self,from:data)
    }
    func save(_ draft: SupportDraft, key: String) throws {
        try Task.checkCancellation()
        guard let folder,let file=url(key) else { throw CocoaError(.fileWriteInvalidFileName) }
        try FileManager.default.createDirectory(at:folder,withIntermediateDirectories:true,attributes:[.protectionKey:FileProtectionType.completeUntilFirstUserAuthentication])
        var directory=folder; var values=URLResourceValues();values.isExcludedFromBackup=true;try directory.setResourceValues(values)
        let data=try JSONEncoder().encode(draft); guard data.count<=9_000_000 else { throw CocoaError(.fileWriteOutOfSpace) }
        try data.write(to:file,options:[.atomic,.completeFileProtectionUntilFirstUserAuthentication])
    }
    func clear(_ key: String) { if let file=url(key) { try? FileManager.default.removeItem(at:file) } }
    func clearAll() { guard let folder,let files=try? FileManager.default.contentsOfDirectory(at:folder,includingPropertiesForKeys:nil) else { return }; for file in files where file.pathExtension=="json" { try? FileManager.default.removeItem(at:file) } }
}
