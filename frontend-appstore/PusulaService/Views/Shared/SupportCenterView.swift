import SwiftUI
import PhotosUI
import UniformTypeIdentifiers
import ImageIO

struct SupportCenterView: View {
    var tourComposer = false
    @Environment(\.guideTourMode) private var tourMode
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
        Group {
        if tourMode && tourComposer { composer }
        else {
        ScrollView {
            VStack(alignment:.leading,spacing:20) {
                if detail == nil { supportHero }
                feedback
                if busy { ProgressView("support.loading").frame(maxWidth:.infinity) }
                if !authenticated {
                    Label("support.login_help",systemImage:"person.crop.circle.badge.exclamationmark")
                        .foregroundStyle(.secondary).supportSurface().accessibilityIdentifier("support.login_help")
                } else if contact?.enabled != true {
                    if contact == nil && error == nil { ProgressView("support.loading").frame(maxWidth:.infinity) }
                    else if contact == nil { Button("support.refresh") { Task { await start(retry:true) } } }
                    else { Label("support.not_enabled",systemImage:"info.circle").supportSurface() }
                } else if let detail { conversation(detail) }
                else { requestList }
                contactCard
                if let attachmentToShare { ShareLink(item:attachmentToShare) { Label("support.share_attachment",systemImage:"square.and.arrow.up") } }
            }.padding(20).frame(maxWidth:760).frame(maxWidth:.infinity)
        }
        .guideTourScrollable()
        .safeAreaInset(edge:.bottom) {
            if detail != nil && !showComposer {
                Button { reply() } label: { Label("support.reply",systemImage:"arrowshape.turn.up.left").frame(maxWidth:.infinity).padding(.vertical,8) }
                    .buttonStyle(.borderedProminent).disabled(busy).padding().background(.regularMaterial)
            }
        }
        .background(PusulaTheme.page)
        .navigationTitle("support.title")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar(.hidden,for:.tabBar)
        .navigationDestination(isPresented:$showComposer) { composer }
        }
        }
        .task { await start() }
        .task(id:draft) { await persistDraft() }
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
    private var supportHero: some View {
        VStack(alignment:.leading,spacing:14) {
            HStack(spacing:10) {
                Image(systemName:"headphones.circle.fill").font(.system(size:34)).foregroundStyle(PusulaTheme.brandCyan)
                Text("support.hub.brand").font(.subheadline.weight(.semibold)).foregroundStyle(.white.opacity(0.85))
            }
            Text("support.hub.heading").font(.title2.bold()).foregroundStyle(.white)
            Text("support.hub.subtitle").font(.subheadline).foregroundStyle(.white.opacity(0.78)).fixedSize(horizontal:false,vertical:true)
        }.frame(maxWidth:.infinity,alignment:.leading).padding(22)
            .background(LinearGradient(colors:[PusulaTheme.brandNavy,Color(red:0.08,green:0.28,blue:0.42)],startPoint:.topLeading,endPoint:.bottomTrailing),in:RoundedRectangle(cornerRadius:24))
            .accessibilityIdentifier("support.hero")
    }
    @ViewBuilder private var feedback: some View {
        if let success { Label(success,systemImage:"checkmark.circle.fill").foregroundStyle(.green).font(.subheadline).supportSurface().accessibilityIdentifier("support.success") }
        if let error { Label(error,systemImage:"exclamationmark.triangle.fill").foregroundStyle(.orange).font(.subheadline).supportSurface().accessibilityIdentifier("support.error") }
    }
    private var requestList: some View {
        VStack(alignment:.leading,spacing:16) {
            Button {
                if draft.ticketId==nil { showComposer=true;detail=nil }
                else { error=String(localized:"support.finish_draft") }
            } label: { Label("support.new",systemImage:"plus.bubble.fill").font(.headline).frame(maxWidth:.infinity).padding(.vertical,10) }
                .buttonStyle(.borderedProminent).disabled(busy).accessibilityIdentifier("support.new")
            HStack {
                Text("support.hub.requests").font(.title3.bold()).accessibilityIdentifier("support.requests")
                Spacer()
                Button { Task { await refresh() } } label: { Label("support.refresh",systemImage:"arrow.clockwise").font(.subheadline) }
                    .disabled(busy).accessibilityIdentifier("support.refresh")
            }
            if tickets.isEmpty && !busy {
                ContentUnavailableView("support.empty",systemImage:"tray",description:Text("support.hub.empty_hint"))
                    .supportSurface().accessibilityIdentifier("support.empty")
            }
            ForEach(tickets) { ticket in
                Button { Task { await open(ticket.id) } } label: {
                    VStack(alignment:.leading,spacing:12) {
                        HStack(alignment:.top) {
                            Image(systemName:categoryIcon(ticket.category)).font(.title3).foregroundStyle(PusulaTheme.accent)
                                .padding(10).background(PusulaTheme.accent.opacity(0.10),in:RoundedRectangle(cornerRadius:12))
                            VStack(alignment:.leading,spacing:5) {
                                Text(ticket.number).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                                Text(ticket.subject).font(.headline).foregroundStyle(.primary).multilineTextAlignment(.leading)
                            }
                            Spacer(minLength:4)
                            Image(systemName:"chevron.right").font(.caption.bold()).foregroundStyle(.tertiary)
                        }
                        HStack { statusBadge(ticket.status);Spacer();Text(SupportPresentation.displayDate(ticket.updatedAt)).font(.caption).foregroundStyle(.secondary) }
                        if ticket.unread { Label("support.hub.unread",systemImage:"circle.fill").font(.caption.weight(.semibold)).foregroundStyle(PusulaTheme.accent) }
                    }.supportSurface()
                }.buttonStyle(.plain).accessibilityIdentifier("support.ticket.\(ticket.id)")
            }
            HStack {
                if page>0 { Button("support.previous") { page-=1;Task { await refresh() } }.disabled(busy) }
                Spacer()
                if hasNext { Button("support.next") { page+=1;Task { await refresh() } }.disabled(busy) }
            }
        }.guideTourTarget("support-requests")
    }
    private var contactCard: some View {
        VStack(alignment:.leading,spacing:12) {
            Label("support.hub.other_channels",systemImage:"envelope").font(.subheadline.weight(.semibold))
            Text("support.hub.email_hint").font(.caption).foregroundStyle(.secondary)
            Link(destination:URL(string:"mailto:\(email)")!) { HStack {Text(email).font(.subheadline);Spacer();Image(systemName:"arrow.up.right")} }
                .accessibilityIdentifier("support.email")
        }.supportSurface()
    }
    private func conversation(_ value:SupportDetailDTO)->some View {
        VStack(alignment:.leading,spacing:18) {
            Button { detail=nil;showComposer=false } label: {Label("support.back_list",systemImage:"chevron.left").font(.subheadline)}
            VStack(alignment:.leading,spacing:12) {
                HStack {Text(value.ticket.number).font(.caption.weight(.semibold)).foregroundStyle(.secondary);Spacer();statusBadge(value.ticket.status)}
                Text(value.ticket.subject).font(.title2.bold()).textSelection(.enabled)
                Label(SupportPresentation.label(value.ticket.category),systemImage:categoryIcon(value.ticket.category)).font(.caption).foregroundStyle(.secondary)
            }.supportSurface()
            if let before=value.nextBefore { Button("support.older") { Task { await open(value.ticket.id,before:before) } }.frame(maxWidth:.infinity) }
            ForEach(value.messages) { message in
                HStack(alignment:.top,spacing:10) {
                    if !message.staff { Spacer(minLength:20) }
                    VStack(alignment:.leading,spacing:10) {
                        Label(LocalizedStringKey(message.staff ? "support.team" : "support.you"),systemImage:message.staff ? "headphones" : "person.crop.circle")
                            .font(.caption.weight(.semibold)).foregroundStyle(message.staff ? PusulaTheme.accent : .secondary)
                        Text(message.body).font(.body).textSelection(.enabled).fixedSize(horizontal:false,vertical:true)
                        ForEach(message.attachments) { attachment in
                            Button { Task { await download(attachment) } } label: {Label(attachment.fileName,systemImage:"paperclip").font(.subheadline)}
                        }
                        Text(SupportPresentation.displayDate(message.createdAt)).font(.caption2).foregroundStyle(.secondary)
                    }.frame(maxWidth:.infinity,alignment:.leading).padding(16)
                        .background(message.staff ? PusulaTheme.accent.opacity(0.09) : PusulaTheme.surface,in:RoundedRectangle(cornerRadius:18))
                    if message.staff { Spacer(minLength:20) }
                }
            }
        }.accessibilityIdentifier("support.conversation")
    }
    private func statusBadge(_ status:String)->some View {
        let color:Color = status == "RESOLVED" ? .green : status == "WAITING_USER" ? .orange : PusulaTheme.accent
        return Text(SupportPresentation.label(status)).font(.caption.weight(.semibold))
            .foregroundStyle(color).padding(.horizontal,10).padding(.vertical,6).background(color.opacity(0.12),in:Capsule())
    }
    private func categoryIcon(_ category:String)->String {
        switch category {case "BUG":"ladybug";case "SUGGESTION":"lightbulb";default:"questionmark.bubble"}
    }
    private func reply() {
        guard let detail else{return}
        if !draft.hasContent {draft=SupportDraft();draft.ticketId=detail.ticket.id;showComposer=true}
        else if draft.ticketId==detail.ticket.id {showComposer=true}
        else {error=String(localized:"support.finish_draft")}
    }
    private var composer: some View {
        ScrollView {
        VStack(alignment:.leading,spacing:20) {
            Label(LocalizedStringKey(draft.ticketId==nil ? "support.hub.compose_hint" : "support.hub.reply_hint"),systemImage:"bubble.left.and.text.bubble.right")
                .font(.subheadline).foregroundStyle(.secondary)
            feedback
            VStack(alignment:.leading,spacing:16) {
            if draft.ticketId==nil {
                Text("support.subject").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                TextField("support.subject",text:$draft.subject).font(.headline).disabled(draft.frozen).accessibilityIdentifier("support.subject")
                Divider()
                Picker("support.category",selection:$draft.category) { ForEach(["QUESTION","BUG","SUGGESTION"],id:\.self){Text(SupportPresentation.label($0)).tag($0)} }.pickerStyle(.menu).disabled(draft.frozen)
                Divider()
            }
            Text("support.description").font(.caption.weight(.semibold)).foregroundStyle(.secondary)
            TextField("support.description",text:$draft.body,axis:.vertical).lineLimit(3...12)
                .accessibilityIdentifier("support.body")
                .disabled(draft.frozen)
            HStack {Spacer();Text("\(draft.body.count) / 4000").font(.caption.monospacedDigit()).foregroundStyle(draft.body.count>4000 ? .red : .secondary)}
            }.supportSurface().guideTourTarget("support-form")
            VStack(alignment:.leading,spacing:14) {
            Label("support.hub.attachments",systemImage:"paperclip").font(.headline)
            if !draft.frozen {
                PhotosPicker(selection:$photos,maxSelectionCount:max(1,3-draft.attachments.count),matching:.images) { Label("support.add_photo",systemImage:"photo") }.disabled(draft.attachments.count>=3 || busy)
                Button("support.add_report") {importReport=true}.disabled(draft.attachments.count>=3 || busy)
                ForEach(Array(draft.attachments.enumerated()),id:\.offset) { index,file in
                    VStack(alignment:.leading,spacing:8) {Label(LocalizedStringKey(file.mediaType=="application/json" ? "support.report" : "support.photo"),systemImage:file.mediaType=="application/json" ? "doc.text":"photo");HStack {Button("support.preview") {if let data=Data(base64Encoded:file.dataBase64) {if file.mediaType=="application/json" {reportPreview=String(decoding:data,as:UTF8.self);previewReport=true}else{photoPreviewData=data;previewPhoto=true}}};Spacer();Button("support.remove",role:.destructive){draft.attachments.remove(at:index)}}}.font(.subheadline)
                }
            }
            Text("support.privacy_hint").font(.caption).foregroundStyle(.secondary).fixedSize(horizontal:false,vertical:true)
            }.supportSurface().guideTourTarget("support-attachments")
            ShareLink(item:draft.body) {Label("support.share_draft",systemImage:"square.and.arrow.up").font(.subheadline)}
            if draft.frozen { Text("support.unconfirmed").foregroundStyle(.orange) }
        }.padding(20).frame(maxWidth:760).frame(maxWidth:.infinity)
        }
        .guideTourScrollable()
        .accessibilityIdentifier("support.composer.scroll")
        .background(PusulaTheme.page)
        .navigationTitle(LocalizedStringKey(draft.ticketId==nil ? "support.new" : "support.reply"))
        .navigationBarTitleDisplayMode(.inline)
        .pusulaKeyboardDismissal()
        .toolbar {
            if !draft.frozen {
                ToolbarItem(placement:.topBarTrailing) {
                    Button("support.done") {
                        PusulaKeyboard.dismiss()
                        Task {
                            await persistDraft()
                            if draftSaved || !draft.hasContent {showComposer=false}
                        }
                    }.disabled(busy)
                        .accessibilityLabel(Text("support.close_draft"))
                        .accessibilityIdentifier("support.draft.close")
                }
            }
        }
        .safeAreaInset(edge:.bottom) {
            VStack(spacing:10) {
            if draftSaved && draft.hasContent {
                Text("support.draft_saved_short").font(.caption).foregroundStyle(.secondary)
                    .accessibilityLabel(Text("support.draft_saved"))
                    .accessibilityIdentifier("support.draft.saved")
            }
            Button { Task { await send() } } label: { Text(LocalizedStringKey(busy ? "support.sending" : draft.frozen ? "support.retry_same" : "support.send")).frame(maxWidth:.infinity).padding(.vertical,10) }
                .buttonStyle(.borderedProminent).disabled(busy || draft.body.trimmingCharacters(in:.whitespacesAndNewlines).isEmpty || draft.body.count>4000 || draft.ticketId==nil && (draft.subject.isEmpty || draft.subject.count>120))
                .accessibilityIdentifier("support.send")
            }.padding().background(.regularMaterial).guideTourTarget("support-submit")
        }
        .disabled(busy)
        // The list's task disappears when this navigation destination opens.
        // Autosave must follow the visible editor, not only its presenting list.
        .task(id:draft) { await persistDraft() }
        // Only inputs freeze; the same-intent retry button must remain usable.
        .onChange(of:draft.body) { _,new in if draft.frozen,let frozen=lastFrozen { draft=frozen } }
    }
    @State private var lastFrozen: SupportDraft?
    @MainActor private func persistDraft() async {
        guard !tourMode,restored,let captured=snapshot,isCurrent(captured),let key=draftKey else{return}
        let value=draft
        draftSaved=false
        do {
            try await SupportDraftStore.shared.save(value,key:key)
            guard !Task.isCancelled,isCurrent(captured),draftKey==key,draft==value else{return}
            draftSaved=true
        } catch {
            guard !Task.isCancelled,isCurrent(captured),draftKey==key,draft==value,
                  !(error is CancellationError) else{return}
            self.error=String(localized:"support.draft_failed")
        }
    }
    @MainActor private func start(retry:Bool=false) async {
        guard !restored || retry else { return }
        snapshot=activeSnapshot
#if DEBUG
        if SupportFlowFixture.isEnabled,let flowFixture,flowFixture.resetDraft {await SupportDraftStore.shared.clear(flowFixture.draftKey)}
#endif
        if !tourMode,let snapshot,let key=SupportService.accountKey(snapshot.token,company:snapshot.companyId) {
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
        catch {if isCurrent(snapshot),!LoadCancellationPolicy.isCancellation(error){self.error=String(localized:"support.connection_fallback")}}
    }
    @MainActor private func open(_ id:Int,before:Int?=nil) async {
        guard let snapshot,isCurrent(snapshot),!busy else{return};busy=true;defer{busy=false}
        do{let value=try await loadDetail(id:id,before:before,token:snapshot.token);guard isCurrent(snapshot) else{return};
            if before != nil,let old=detail {detail=SupportDetailDTO(ticket:value.ticket,messages:value.messages+old.messages,nextBefore:value.nextBefore)}else{detail=value;showComposer=false}
            if !tourMode { try? await markRead(id:id,token:snapshot.token) }
        }catch{if isCurrent(snapshot){self.error=String(localized:"support.connection_fallback")}}
    }
    @MainActor private func send() async {
        guard !tourMode,let snapshot,isCurrent(snapshot),!busy else{return}
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

private extension View {
    func supportSurface()->some View {
        frame(maxWidth:.infinity,alignment:.leading).padding(18)
            .background(PusulaTheme.surface,in:RoundedRectangle(cornerRadius:20))
            .overlay {RoundedRectangle(cornerRadius:20).stroke(PusulaTheme.border.opacity(0.55),lineWidth:1)}
    }
}
