import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import ImageIO

struct SupportCenterView: View {
#if DEBUG
    var flowFixture: SupportFlowFixture? = nil
#endif
    @ObservedObject private var session=SessionManager.shared
    @State private var contact: SupportContact?
    @State private var tickets: [SupportTicketDTO]=[]
    @State private var detail: SupportDetailDTO?
    @State private var page=0
    @State private var hasNext=false
    @State private var busy=false
    @State private var error: String?
    @State private var draft=SupportDraft()
    @State private var restored=false
    @State private var draftKey: String?
    @State private var photos: [PhotosPickerItem]=[]
    @State private var importReport=false
    @State private var attachmentToShare: URL?
    @State private var showComposer=false
    @State private var snapshot: AuthenticatedSessionSnapshot?
    @State private var reportPreview=""
    @State private var previewReport=false
    @State private var previewPhoto=false
    @State private var photoPreviewData: Data?
    @State private var success: String?
    @State private var draftSaved=false
    private var email: String { contact?.email ?? SupportPresentation.fallbackEmail }
    private var authenticated: Bool {
#if DEBUG
        if SupportFlowFixture.isEnabled,let flowFixture {return flowFixture.authenticated}
#endif
        return session.isAuthenticated
    }
    private var activeSnapshot: AuthenticatedSessionSnapshot? {
#if DEBUG
        if SupportFlowFixture.isEnabled,let flowFixture {return flowFixture.authenticated ? flowFixture.snapshot:nil}
#endif
        return session.authenticatedSnapshot
    }
    private func isCurrent(_ captured:AuthenticatedSessionSnapshot)->Bool {
#if DEBUG
        if SupportFlowFixture.isEnabled,let flowFixture {return flowFixture.authenticated && captured==flowFixture.snapshot}
#endif
        return session.isCurrent(captured)
    }
    var body: some View {
        ScrollView {
            VStack(alignment:.leading,spacing:18) {
                Text("support.intro").foregroundStyle(.secondary)
                HStack {
                    Link("support.email",destination:URL(string:"mailto:\(email)")!)
                    ShareLink(item:draft.body) { Label("support.share_draft",systemImage:"square.and.arrow.up") }
                }
                Text(email).font(.caption).textSelection(.enabled)
                if let success { Text(success).foregroundStyle(.green).accessibilityIdentifier("support.success") }
                if let error { Text(error).foregroundStyle(.orange).accessibilityIdentifier("support.error") }
                if busy { ProgressView("support.loading") }
                if !authenticated { Text("support.login_help").accessibilityIdentifier("support.login_help") }
                else if contact?.enabled != true { Text("support.not_enabled") }
                else {
                    HStack {
                        Button("support.new") { if draft.ticketId==nil {showComposer=true;detail=nil} else {error=String(localized:"support.finish_draft")} }.accessibilityIdentifier("support.new")
                        Spacer()
                        Button("support.refresh") { Task { await refresh() } }.disabled(busy).accessibilityIdentifier("support.refresh")
                    }
                    if let detail {
                        Text(detail.ticket.number+" · "+detail.ticket.subject).font(.headline)
                        Text(SupportPresentation.label(detail.ticket.status)).foregroundStyle(.secondary)
                        if let before=detail.nextBefore { Button("support.older") { Task { await open(detail.ticket.id,before:before) } } }
                        ForEach(detail.messages) { message in
                            VStack(alignment:.leading,spacing:8) {
                                Text(LocalizedStringKey(message.staff ? "support.team" : "support.you")).font(.caption.bold())
                                Text(message.body).textSelection(.enabled)
                                Text(message.createdAt).font(.caption2).foregroundStyle(.secondary)
                                ForEach(message.attachments) { attachment in
                                    Button(attachment.fileName) { Task { await download(attachment) } }
                                }
                            }.frame(maxWidth:.infinity,alignment:.leading).padding().background(message.staff ? PusulaTheme.accent.opacity(0.08) : PusulaTheme.surface).clipShape(RoundedRectangle(cornerRadius:14))
                        }
                        Button("support.reply") {
                            if !draft.hasContent { draft=SupportDraft();draft.ticketId=detail.ticket.id;showComposer=true }
                            else if draft.ticketId==detail.ticket.id {showComposer=true}
                            else {error=String(localized:"support.finish_draft")}
                        }
                        Button("support.back_list") { self.detail=nil;showComposer=false }
                    } else if !showComposer {
                        ForEach(tickets) { ticket in
                            Button { Task { await open(ticket.id) } } label: {
                                VStack(alignment:.leading,spacing:5) {
                                    Text((ticket.unread ? "● " : "")+ticket.number+" · "+ticket.subject).font(.headline)
                                    Text(SupportPresentation.label(ticket.status)).font(.caption)
                                }.frame(maxWidth:.infinity,alignment:.leading).padding().pusulaCard()
                            }.buttonStyle(.plain).accessibilityIdentifier("support.ticket.\(ticket.id)")
                        }
                        if tickets.isEmpty && !busy { Text("support.empty") }
                        HStack { if page>0 {Button("support.previous") {page-=1;Task{await refresh()}}};if hasNext {Button("support.next") {page+=1;Task{await refresh()}}} }
                    }
                    if showComposer { composer }
                    if let attachmentToShare { ShareLink(item:attachmentToShare) { Label("support.share_attachment",systemImage:"square.and.arrow.up") } }
                }
            }.padding()
        }
        .pusulaKeyboardDismissal()
        .background(PusulaTheme.page)
        .navigationTitle("support.title")
        .task { await start() }
        .task(id:draft) {
            guard restored,let key=draftKey else{return};draftSaved=false
            do {try await SupportDraftStore.shared.save(draft,key:key);if !Task.isCancelled {draftSaved=true}} catch {if !Task.isCancelled && !(error is CancellationError) {self.error=String(localized:"support.draft_failed")}}
        }
        .onChange(of:session.token) { _,_ in clearSharedFile();tickets=[];detail=nil;draft=SupportDraft();showComposer=false;restored=false;snapshot=nil;draftKey=nil;reportPreview="";photoPreviewData=nil;previewReport=false;previewPhoto=false;photos=[] }
        .onDisappear { clearSharedFile() }
        .onChange(of:photos) { _,items in Task { await attachPhotos(items) } }
        .sheet(isPresented:$previewReport) { NavigationStack { ScrollView { Text(reportPreview).font(.system(.caption,design:.monospaced)).padding().textSelection(.enabled) }.navigationTitle("support.report_preview").toolbar {Button("support.done"){previewReport=false}} } }
        .sheet(isPresented:$previewPhoto) { NavigationStack { Group {if let photoPreviewData,let image=UIImage(data:photoPreviewData) {Image(uiImage:image).resizable().scaledToFit().padding()}}.navigationTitle("support.photo").toolbar {Button("support.done"){previewPhoto=false;photoPreviewData=nil}} } }
        .fileImporter(isPresented:$importReport,allowedContentTypes:[.json],allowsMultipleSelection:false) { result in
            Task {
                do {
                    guard let snapshot,isCurrent(snapshot),!draft.frozen else{return}
                    guard let file=try result.get().first else{return}
                    let access=file.startAccessingSecurityScopedResource();defer{if access{file.stopAccessingSecurityScopedResource()}}
                    let size=try file.resourceValues(forKeys:[.fileSizeKey]).fileSize ?? 0
                    guard size>0,size<=256*1024 else{throw CocoaError(.fileReadTooLarge)}
                    let data=try Data(contentsOf:file); reportPreview=String(decoding:data,as:UTF8.self)
                    guard draft.attachments.count<3, !draft.frozen else{return}
                    draft.attachments.append(SupportUpload(mediaType:"application/json",dataBase64:data.base64EncodedString()));previewReport=true
                }catch{self.error=String(localized:"support.attachment_failed")}
            }
        }
    }
    private var composer: some View {
        VStack(alignment:.leading,spacing:14) {
            if draft.ticketId==nil {
                TextField("support.subject",text:$draft.subject).textFieldStyle(.roundedBorder).disabled(draft.frozen).accessibilityIdentifier("support.subject")
                Picker("support.category",selection:$draft.category) { ForEach(["QUESTION","BUG","SUGGESTION"],id:\.self){Text(SupportPresentation.label($0)).tag($0)} }.disabled(draft.frozen)
            }
            TextField("support.description",text:$draft.body,axis:.vertical).lineLimit(5...12).textFieldStyle(.roundedBorder)
                .accessibilityIdentifier("support.body")
                .disabled(draft.frozen)
            Text("support.privacy_hint").font(.caption).foregroundStyle(.secondary)
            if draftSaved && draft.hasContent {Text("support.draft_saved").font(.caption).foregroundStyle(.secondary).accessibilityIdentifier("support.draft.saved")}
            if !draft.frozen {
                PhotosPicker(selection:$photos,maxSelectionCount:max(1,3-draft.attachments.count),matching:.images) { Label("support.add_photo",systemImage:"photo") }.disabled(draft.attachments.count>=3 || busy)
                Button("support.add_report") {importReport=true}.disabled(draft.attachments.count>=3 || busy)
                ForEach(Array(draft.attachments.enumerated()),id:\.offset) { index,file in
                    HStack {Text(LocalizedStringKey(file.mediaType=="application/json" ? "support.report" : "support.photo"));Button("support.preview") {if let data=Data(base64Encoded:file.dataBase64) {if file.mediaType=="application/json" {reportPreview=String(decoding:data,as:UTF8.self);previewReport=true}else{photoPreviewData=data;previewPhoto=true}}};Button("support.remove",role:.destructive){draft.attachments.remove(at:index)}}
                }
            }
            if draft.frozen { Text("support.unconfirmed").foregroundStyle(.orange) }
            Button { Task { await send() } } label: { Text(LocalizedStringKey(busy ? "support.sending" : draft.frozen ? "support.retry_same" : "support.send")).frame(maxWidth:.infinity).padding(.vertical,10) }
                .buttonStyle(.borderedProminent).disabled(busy || draft.body.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty || draft.body.count>4000 || draft.ticketId==nil && (draft.subject.isEmpty || draft.subject.count>120))
                .accessibilityIdentifier("support.send")
            if !draft.frozen {Button("support.close_draft"){showComposer=false}.accessibilityIdentifier("support.draft.close")}
        }
        .disabled(busy)
        // Only inputs freeze; the same-intent retry button must remain usable.
        .onChange(of:draft.body) { _,new in if draft.frozen,let frozen=lastFrozen { draft=frozen } }
    }
    @State private var lastFrozen: SupportDraft?
    @MainActor private func start() async {
        snapshot=activeSnapshot
#if DEBUG
        if SupportFlowFixture.isEnabled,let flowFixture,flowFixture.resetDraft {await SupportDraftStore.shared.clear(flowFixture.draftKey)}
#endif
        if let snapshot,let key=SupportService.accountKey(snapshot.token,company:snapshot.companyId) {
            draftKey=key
            if let saved=await SupportDraftStore.shared.load(key),isCurrent(snapshot) {draft=saved;lastFrozen=saved.frozen ? saved:nil;showComposer=saved.hasContent}
        }
        restored=true
        do{let value=try await loadContact();contact=value}catch{self.error=String(localized:"support.connection_fallback")}
        if contact?.enabled==true {await refresh()}
    }
    @MainActor private func refresh() async {
        guard let snapshot,isCurrent(snapshot),!busy else{return};busy=true;defer{busy=false}
        do {let result=try await loadList(token:snapshot.token);guard isCurrent(snapshot) else{return};tickets=result.items;hasNext=result.hasNext;error=nil}
        catch {if isCurrent(snapshot),!RefreshRequestState.isCancellation(error){self.error=String(localized:"support.connection_fallback")}}
    }
    @MainActor private func open(_ id:Int,before:Int?=nil) async {
        guard let snapshot,isCurrent(snapshot),!busy else{return};busy=true;defer{busy=false}
        do{let value=try await loadDetail(id:id,before:before,token:snapshot.token);guard isCurrent(snapshot) else{return};
            if before != nil,let old=detail {detail=SupportDetailDTO(ticket:value.ticket,messages:value.messages+old.messages,nextBefore:value.nextBefore)}else{detail=value;showComposer=false}
            try? await markRead(id:id,token:snapshot.token)
        }catch{if isCurrent(snapshot){self.error=String(localized:"support.connection_fallback")}}
    }
    @MainActor private func send() async {
        guard let snapshot,isCurrent(snapshot),!busy else{return}
        PusulaKeyboard.dismiss();busy=true;defer{busy=false}
        if !draft.frozen {draft.appVersion=String(Bundle.main.object(forInfoDictionaryKey:"CFBundleShortVersionString") as? String ?? "")+" ("+String(Bundle.main.object(forInfoDictionaryKey:"CFBundleVersion") as? String ?? "")+")";draft.frozen=true;lastFrozen=draft}
        do {
            guard let key=draftKey else{throw CocoaError(.fileWriteInvalidFileName)}
            try await SupportDraftStore.shared.save(draft,key:key)
            let result=try await submit(draft,token:snapshot.token);guard isCurrent(snapshot) else{return}
            await SupportDraftStore.shared.clear(key);detail=result;draft=SupportDraft();lastFrozen=nil;photos=[];showComposer=false;error=nil;success=String(localized:"support.sent")
        }catch {
            guard isCurrent(snapshot) else{return}
            if let network=error as? NetworkError, case .badRequest = network {draft.frozen=false;draft.clientKey=UUID().uuidString;lastFrozen=nil}
            self.error=error.localizedDescription
        }
    }
    @MainActor private func attachPhotos(_ items:[PhotosPickerItem]) async {
        guard !draft.frozen,!busy,!items.isEmpty else{return};busy=true;defer{busy=false;photos=[]}
        let expected=snapshot
        for item in items.prefix(3-draft.attachments.count) {
            do {
                guard let input=try await item.loadTransferable(type:Data.self) else{continue}
                let output=await Task.detached(priority:.utility) { () -> Data? in
                    guard let source=CGImageSourceCreateWithData(input as CFData,nil),let image=CGImageSourceCreateThumbnailAtIndex(source,0,[kCGImageSourceCreateThumbnailFromImageAlways:true,kCGImageSourceThumbnailMaxPixelSize:1600,kCGImageSourceCreateThumbnailWithTransform:true] as CFDictionary) else{return nil}
                    return UIImage(cgImage:image).jpegData(compressionQuality:0.8)
                }.value
                guard let expected,isCurrent(expected),let output,output.count<=2*1024*1024 else{continue}
                draft.attachments.append(SupportUpload(mediaType:"image/jpeg",dataBase64:output.base64EncodedString()))
            }catch{if !Task.isCancelled && !(error is CancellationError) {self.error=String(localized:"support.attachment_failed")}}
        }
    }
    @MainActor private func download(_ file:SupportAttachmentDTO) async {
        guard let snapshot,isCurrent(snapshot) else{return}
        do {
            // request() pins the captured credential; binary data uses the same authenticated transport.
            let data=try await downloadBytes(file.id,token:snapshot.token)
            guard isCurrent(snapshot),data.count<=2*1024*1024 else{return}
            clearSharedFile()
            let name=file.mediaType=="application/json" ? "baglanti-raporu.json":"ekran-goruntusu.jpg"
            let url=FileManager.default.temporaryDirectory.appendingPathComponent("support-\(UUID().uuidString)-\(name)")
            try data.write(to:url,options:[.atomic,.completeFileProtection]);attachmentToShare=url
        }catch{self.error=String(localized:"support.attachment_failed")}
    }
    private func clearSharedFile() {if let file=attachmentToShare {try? FileManager.default.removeItem(at:file)};attachmentToShare=nil}
    // Offline UI regressions exercise this real view without configuring a real session or API.
    private func loadContact() async throws -> SupportContact {
#if DEBUG
        if SupportFlowFixture.isEnabled,let flowFixture {return try await flowFixture.contact()}
#endif
        return try await SupportService.contact()
    }
    private func loadList(token:String) async throws -> SupportPageDTO {
#if DEBUG
        if SupportFlowFixture.isEnabled,let flowFixture {return try await flowFixture.list()}
#endif
        return try await SupportService.list(page:page,token:token)
    }
    private func loadDetail(id:Int,before:Int?,token:String) async throws -> SupportDetailDTO {
#if DEBUG
        if SupportFlowFixture.isEnabled,let flowFixture {return flowFixture.detail()}
#endif
        return try await SupportService.detail(id:id,before:before,token:token)
    }
    private func submit(_ value:SupportDraft,token:String) async throws -> SupportDetailDTO {
#if DEBUG
        if SupportFlowFixture.isEnabled,let flowFixture {return try await flowFixture.send(value)}
#endif
        return try await SupportService.send(value,token:token)
    }
    private func markRead(id:Int,token:String) async throws {
#if DEBUG
        if SupportFlowFixture.isEnabled,flowFixture != nil {return}
#endif
        try await SupportService.markRead(id:id,token:token)
    }
    private func downloadBytes(_ id:Int,token:String) async throws -> Data {
#if DEBUG
        if SupportFlowFixture.isEnabled,flowFixture != nil {throw URLError(.fileDoesNotExist)}
#endif
        return try await NetworkManager.shared.downloadData("/api/support/attachments/\(id)",authTokenOverride:token)
    }
}
