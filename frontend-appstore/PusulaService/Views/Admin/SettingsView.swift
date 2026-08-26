import SwiftUI
import PhotosUI
import UIKit

struct SettingsView: View {
    @ObservedObject private var session = SessionManager.shared
    @State private var selectedTab = 0
    @State private var users: [UserDTO] = []
    @State private var vehicles: [VehicleDTO] = []
    @State private var companyName = ""
    @State private var companyPhone = ""
    @State private var companyAddress = ""
    @State private var companyEmail = ""
    @State private var companyId: Int?
    @State private var isLoading = true
    @State private var editingUser: UserDTO?
    @State private var editingVehicle: VehicleDTO?
    @State private var showUserForm = false
    @State private var showVehicleForm = false
    @State private var isSavingCompany = false
    @State private var uploadingSignatureUserId: Int?
    @State private var userPendingDeletion: UserDTO?
    @State private var vehiclePendingDeletion: VehicleDTO?
    @State private var pendingReassignment: PendingUserReassignment?
    @State private var isDeleting = false
    @State private var showDeleteAccountConfirmation = false
    @State private var isDeletingAccount = false
    @State private var errorMessage: String?
    @State private var successMessage: String?
    
    var body: some View {
        VStack(spacing: 0) {
            Picker("Ayarlar", selection: $selectedTab) {
                Text("Ekip").tag(0)
                Text("Araçlar").tag(1)
                Text("Firma").tag(2)
                Text("Hesap").tag(3)
            }
            .pickerStyle(.segmented)
            .padding()
            .onboardingTarget(.settingsSections)
            
            if isLoading && selectedTab != 3 {
                Spacer()
                ProgressView()
                Spacer()
            } else {
                TabView(selection: $selectedTab) {
                    usersTab.tag(0)
                    vehiclesTab.tag(1)
                    companyTab.tag(2)
                    accountTab.tag(3)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
            }
        }
        .background(PusulaTheme.page)
        .navigationTitle("Ayarlar")
        .toolbar {
            ToolbarItem(placement: .topBarTrailing) {
                Button(action: { Task { await load(refresh: true) } }) {
                    Image(systemName: "arrow.clockwise")
                }
            }
        }
        .task { await load() }
        .sheet(isPresented: $showUserForm) {
            UserEditorSheet(user: editingUser) { await load(refresh: true) }
        }
        .sheet(isPresented: $showVehicleForm) {
            VehicleEditorSheet(vehicle: editingVehicle) { await load(refresh: true) }
        }
        .sheet(item: $pendingReassignment) { request in
            ReassignTicketsBeforeDeleteSheet(
                request: request,
                candidates: reassignmentCandidates(excluding: request.user.id)
            ) {
                await load(refresh: true)
                successMessage = "Kullanıcı silindi ve açık iş emirleri devredildi."
            }
        }
        .confirmationDialog(
            "Kullanıcı silinsin mi?",
            isPresented: Binding(get: { userPendingDeletion != nil }, set: { if !$0 { userPendingDeletion = nil } }),
            titleVisibility: .visible
        ) {
            Button("Kullanıcıyı Sil", role: .destructive) {
                guard let user = userPendingDeletion else { return }
                userPendingDeletion = nil
                Task { await deleteUser(user) }
            }
            Button("Vazgeç", role: .cancel) { userPendingDeletion = nil }
        } message: {
            Text("Bu kullanıcı hesabı kapatılacak. Açık iş emirleri varsa önce başka bir personele devretmeniz istenecek.")
        }
        .confirmationDialog(
            "Araç silinsin mi?",
            isPresented: Binding(get: { vehiclePendingDeletion != nil }, set: { if !$0 { vehiclePendingDeletion = nil } }),
            titleVisibility: .visible
        ) {
            Button("Aracı Sil", role: .destructive) {
                guard let vehicle = vehiclePendingDeletion else { return }
                vehiclePendingDeletion = nil
                Task { await deleteVehicle(vehicle) }
            }
            Button("Vazgeç", role: .cancel) { vehiclePendingDeletion = nil }
        } message: {
            Text("Araç kaydı kalıcı olarak kaldırılacak.")
        }
        .confirmationDialog(
            "Hesap kalıcı olarak silinsin mi?",
            isPresented: $showDeleteAccountConfirmation,
            titleVisibility: .visible
        ) {
            Button("Hesabı Kalıcı Olarak Sil", role: .destructive) {
                Task { await deleteCurrentAccount() }
            }
            Button("Vazgeç", role: .cancel) { }
        } message: {
            Text(deleteAccountConfirmationMessage)
        }
        .alert("Hata", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
        .alert("Tamamlandı", isPresented: Binding(get: { successMessage != nil }, set: { if !$0 { successMessage = nil } })) {
            Button("Tamam", role: .cancel) { successMessage = nil }
        } message: { Text(successMessage ?? "") }
    }
    
    private var usersTab: some View {
        List {
            Section {
                Button(action: {
                    editingUser = nil
                    showUserForm = true
                }) {
                    Label("Kullanıcı Ekle", systemImage: "person.badge.plus")
                }
                .readOnlyProtected()
            }
            
            if users.isEmpty {
                ContentUnavailableView(
                    "Kullanıcı Bulunamadı",
                    systemImage: "person.2.slash",
                    description: Text("Yeni kullanıcıyı yukarıdaki düğmeden ekleyebilirsiniz.")
                )
            } else {
                ForEach(users, id: \.username) { user in
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(user.fullName ?? user.username).font(.headline)
                            Text("@\(user.username) · \(roleTitle(user.role))")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }

                        Spacer()

                        if let userId = user.id, uploadingSignatureUserId == userId {
                            ProgressView()
                                .controlSize(.small)
                                .frame(width: 32, height: 32)
                                .accessibilityLabel("İmza yükleniyor")
                        } else {
                            PhotosPicker(selection: signaturePickerBinding(for: user), matching: .images) {
                                Image(systemName: "signature")
                                    .frame(width: 32, height: 32)
                            }
                            .buttonStyle(.borderless)
                            .disabled(user.id == nil || uploadingSignatureUserId != nil)
                            .readOnlyProtected()
                            .accessibilityLabel("\(user.fullName ?? user.username) için imza yükle")
                            .help("İmza yükle")
                        }

                        Button {
                            editingUser = user
                            showUserForm = true
                        } label: {
                            Image(systemName: "pencil")
                                .frame(width: 32, height: 32)
                        }
                        .buttonStyle(.borderless)
                        .readOnlyProtected()
                        .accessibilityLabel("Kullanıcıyı düzenle")
                        .help("Düzenle")

                        Button(role: .destructive) {
                            userPendingDeletion = user
                        } label: {
                            Image(systemName: "trash")
                                .frame(width: 32, height: 32)
                        }
                        .buttonStyle(.borderless)
                        .disabled(isDeleting)
                        .readOnlyProtected()
                        .accessibilityLabel("Kullanıcıyı sil")
                        .help("Sil")
                    }
                    .padding(.vertical, 4)
                }
            }
        }
        .listStyle(.insetGrouped)
    }
    
    private var vehiclesTab: some View {
        List {
            Section {
                Button(action: {
                    editingVehicle = nil
                    showVehicleForm = true
                }) {
                    Label("Araç Ekle", systemImage: "car.fill")
                }
                .readOnlyProtected()
            }
            
            if vehicles.isEmpty {
                ContentUnavailableView(
                    "Araç Bulunamadı",
                    systemImage: "car",
                    description: Text("Yeni aracı yukarıdaki düğmeden ekleyebilirsiniz.")
                )
            } else {
                ForEach(vehicles, id: \.licensePlate) { vehicle in
                    HStack(spacing: 12) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text(vehicle.licensePlate).font(.headline)
                            if let driver = vehicle.driverName {
                                Text(driver).font(.caption).foregroundStyle(.secondary)
                            }
                        }

                        Spacer()

                        Button {
                            editingVehicle = vehicle
                            showVehicleForm = true
                        } label: {
                            Image(systemName: "pencil")
                                .frame(width: 32, height: 32)
                        }
                        .buttonStyle(.borderless)
                        .readOnlyProtected()
                        .accessibilityLabel("Aracı düzenle")
                        .help("Düzenle")

                        Button(role: .destructive) {
                            vehiclePendingDeletion = vehicle
                        } label: {
                            Image(systemName: "trash")
                                .frame(width: 32, height: 32)
                        }
                        .buttonStyle(.borderless)
                        .disabled(isDeleting)
                        .readOnlyProtected()
                        .accessibilityLabel("Aracı sil")
                        .help("Sil")
                    }
                }
            }
        }
        .listStyle(.insetGrouped)
    }
    
    private var companyTab: some View {
        Form {
            TextField("Firma Adı", text: $companyName)
            TextField("Telefon", text: $companyPhone).keyboardType(.phonePad)
            TextField("Adres", text: $companyAddress, axis: .vertical)
            TextField("E-posta", text: $companyEmail).keyboardType(.emailAddress)
            
            Button("Firma Bilgilerini Kaydet") {
                Task { await saveCompany() }
            }
            .disabled(isSavingCompany || companyName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
            .readOnlyProtected()
            
            PhotosPicker(selection: Binding(
                get: { nil as PhotosPickerItem? },
                set: { item in
                    if let item { Task { await uploadLogo(item: item) } }
                }
            ), matching: .images) {
                Label("Logo Yükle", systemImage: "photo")
            }
            .readOnlyProtected()
        }
    }

    private var accountTab: some View {
        Form {
            Section("Hesap Bilgileri") {
                accountInfoRow("Ad Soyad", value: session.fullName.isEmpty ? "Kullanıcı" : session.fullName)
                accountInfoRow("Firma", value: session.companyName ?? "-")
                accountInfoRow("Paket", value: session.planType)
            }

            Section {
                PusulaAppearancePicker()
                    .padding(.vertical, 4)
            }

            Section("Abonelik") {
                if hasAppStorePlan {
                    Button {
                        StoreKitManager.shared.manageSubscriptions()
                    } label: {
                        Label("Aboneliği Yönet", systemImage: "creditcard")
                    }

                    Text("Hesabınızı silmek App Store aboneliğinizi otomatik olarak iptal etmez.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                } else {
                    Text("Aktif bir App Store aboneliği bulunmuyor.")
                        .foregroundStyle(.secondary)
                }
            }

            Section("Yasal") {
                Link(destination: AppLinks.privacyPolicy) {
                    Label("Gizlilik Politikası", systemImage: "hand.raised")
                }
                Link(destination: AppLinks.termsOfUse) {
                    Label("Kullanım Koşulları", systemImage: "doc.text")
                }
            }

            Section("Yardım") {
                Button {
                    OnboardingManager.shared.restartRoleTour(
                        role: session.role,
                        accountIdentifier: session.onboardingAccountIdentifier
                    )
                } label: {
                    Label("Uygulama Turunu Tekrar Başlat", systemImage: "questionmark.circle")
                }
            }

            Section {
                Button(role: .destructive) {
                    session.logout()
                } label: {
                    Label("Çıkış Yap", systemImage: "rectangle.portrait.and.arrow.right")
                }

                Button(role: .destructive) {
                    showDeleteAccountConfirmation = true
                } label: {
                    HStack(spacing: 8) {
                        if isDeletingAccount {
                            ProgressView()
                                .controlSize(.small)
                        }
                        Label(isDeletingAccount ? "Hesap Siliniyor…" : "Hesabımı Sil", systemImage: "trash")
                    }
                }
                .disabled(isDeletingAccount)
            } footer: {
                Text("Hesap silme işlemi hesabınızı ve ilişkili verileri kalıcı olarak kaldırır. Yasal olarak saklanması gereken kayıtlar mevzuatta belirtilen süre boyunca tutulabilir.")
            }
        }
    }

    private var hasAppStorePlan: Bool {
        session.planType.uppercased() != "CIRAK"
    }

    private var deleteAccountConfirmationMessage: String {
        let base = "Hesabınız ve ilişkili verileriniz kalıcı olarak silinecek. Bu işlem geri alınamaz."
        guard hasAppStorePlan else { return base }
        return base + " App Store aboneliğiniz otomatik olarak iptal olmaz; devam etmeden önce Aboneliği Yönet bölümünden iptal edin."
    }

    private func accountInfoRow(_ title: String, value: String) -> some View {
        HStack {
            Text(title)
                .foregroundStyle(.secondary)
            Spacer()
            Text(value)
                .font(.subheadline.weight(.medium))
                .multilineTextAlignment(.trailing)
        }
    }

    @MainActor
    private func deleteCurrentAccount() async {
        guard !isDeletingAccount else { return }
        isDeletingAccount = true
        defer { isDeletingAccount = false }

        do {
            try await session.deleteAccount()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
    
    private func load(refresh: Bool = false) async {
        if !refresh { isLoading = true }
        do {
            async let usersTask = SettingsService.getUsers()
            async let vehiclesTask = SettingsService.getVehicles()
            async let companyTask = SettingsService.getCompany()
            let (loadedUsers, loadedVehicles, loadedCompany) = try await (usersTask, vehiclesTask, companyTask)
            users = loadedUsers
            vehicles = loadedVehicles
            companyId = loadedCompany.id
            companyName = loadedCompany.name
            companyPhone = loadedCompany.phone ?? ""
            companyAddress = loadedCompany.address ?? ""
            companyEmail = loadedCompany.email ?? ""
        } catch {
            errorMessage = error.localizedDescription
        }
        isLoading = false
    }
    
    private func uploadLogo(item: PhotosPickerItem) async {
        do {
            let data = try await jpegData(from: item)
            _ = try await SettingsService.uploadCompanyLogo(imageData: data)
            await load(refresh: true)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func signaturePickerBinding(for user: UserDTO) -> Binding<PhotosPickerItem?> {
        Binding(
            get: { nil },
            set: { item in
                guard let item, let userId = user.id else { return }
                Task { await uploadSignature(item: item, userId: userId, userName: user.fullName ?? user.username) }
            }
        )
    }

    private func uploadSignature(item: PhotosPickerItem, userId: Int, userName: String) async {
        uploadingSignatureUserId = userId
        defer { uploadingSignatureUserId = nil }

        do {
            let data = try await signatureJPEGData(from: item)
            try await SettingsService.uploadUserSignature(userId: userId, imageData: data)
            successMessage = "\(userName) için imza başarıyla yüklendi."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func jpegData(from item: PhotosPickerItem) async throws -> Data {
        guard let originalData = try await item.loadTransferable(type: Data.self),
              let image = UIImage(data: originalData),
              let jpegData = image.jpegData(compressionQuality: 0.85) else {
            throw SettingsViewError.imageDataUnavailable
        }
        return jpegData
    }

    private func signatureJPEGData(from item: PhotosPickerItem) async throws -> Data {
        guard let originalData = try await item.loadTransferable(type: Data.self),
              let image = UIImage(data: originalData) else {
            throw SettingsViewError.imageDataUnavailable
        }

        let normalized = normalizedSignatureImage(image)
        guard let jpegData = normalized.jpegData(compressionQuality: 0.9) else {
            throw SettingsViewError.imageDataUnavailable
        }
        return jpegData
    }

    private func normalizedSignatureImage(_ image: UIImage) -> UIImage {
        let maxDimension: CGFloat = 2_048
        let downsample = min(1, maxDimension / max(image.size.width, image.size.height))
        let normalizedSize = CGSize(
            width: max(1, image.size.width * downsample),
            height: max(1, image.size.height * downsample)
        )
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        format.opaque = true
        let normalized = UIGraphicsImageRenderer(size: normalizedSize, format: format).image { context in
            UIColor.white.setFill()
            context.cgContext.fill(CGRect(origin: .zero, size: normalizedSize))
            image.draw(in: CGRect(origin: .zero, size: normalizedSize))
        }

        guard let cgImage = normalized.cgImage,
              let contentBounds = signatureContentBounds(in: cgImage),
              let cropped = cgImage.cropping(to: contentBounds) else {
            return normalized
        }

        let croppedImage = UIImage(cgImage: cropped, scale: 1, orientation: .up)
        let safetyMargin: CGFloat = 16
        let outputSize = CGSize(
            width: croppedImage.size.width + safetyMargin * 2,
            height: croppedImage.size.height + safetyMargin * 2
        )
        return UIGraphicsImageRenderer(size: outputSize, format: format).image { context in
            UIColor.white.setFill()
            context.cgContext.fill(CGRect(origin: .zero, size: outputSize))
            croppedImage.draw(at: CGPoint(x: safetyMargin, y: safetyMargin))
        }
    }

    private func signatureContentBounds(in image: CGImage) -> CGRect? {
        let width = image.width
        let height = image.height
        guard width > 0, height > 0 else { return nil }

        var pixels = [UInt8](repeating: 0, count: width * height * 4)
        guard let context = CGContext(
            data: &pixels,
            width: width,
            height: height,
            bitsPerComponent: 8,
            bytesPerRow: width * 4,
            space: CGColorSpaceCreateDeviceRGB(),
            bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue
        ) else { return nil }
        context.draw(image, in: CGRect(x: 0, y: 0, width: width, height: height))

        let cornerOffsets = [0, (width - 1) * 4, (height - 1) * width * 4, (width * height - 1) * 4]
        let background = (0..<3).map { channel in
            cornerOffsets.reduce(0) { $0 + Int(pixels[$1 + channel]) } / cornerOffsets.count
        }

        var minX = width
        var minY = height
        var maxX = -1
        var maxY = -1
        for y in 0..<height {
            for x in 0..<width {
                let offset = (y * width + x) * 4
                let colorDistance = (0..<3).reduce(0) {
                    $0 + abs(Int(pixels[offset + $1]) - background[$1])
                }
                if pixels[offset + 3] > 16 && colorDistance > 72 {
                    minX = min(minX, x)
                    minY = min(minY, y)
                    maxX = max(maxX, x)
                    maxY = max(maxY, y)
                }
            }
        }

        guard maxX >= minX, maxY >= minY else { return nil }
        let padding = max(12, Int(Double(max(width, height)) * 0.02))
        let x = max(0, minX - padding)
        let y = max(0, minY - padding)
        let right = min(width - 1, maxX + padding)
        let bottom = min(height - 1, maxY + padding)
        return CGRect(x: x, y: y, width: right - x + 1, height: bottom - y + 1)
    }

    private func roleTitle(_ role: String) -> String {
        switch role {
        case "COMPANY_ADMIN": return "Yönetici"
        case "TECHNICIAN": return "Teknisyen"
        case "SUPER_ADMIN": return "Süper Yönetici"
        default: return role
        }
    }

    private func reassignmentCandidates(excluding userId: Int?) -> [UserDTO] {
        users.filter {
            $0.id != nil && $0.id != userId && ["TECHNICIAN", "COMPANY_ADMIN"].contains($0.role)
        }
    }

    private func deleteUser(_ user: UserDTO) async {
        guard let userId = user.id else { return }
        isDeleting = true
        defer { isDeleting = false }

        do {
            try await SettingsService.deleteUser(id: userId)
            users.removeAll { $0.id == userId }
            successMessage = "Kullanıcı silindi."
        } catch NetworkError.conflict(let code, _, let count) where code == "ACTIVE_TICKETS" {
            pendingReassignment = PendingUserReassignment(user: user, activeTicketCount: count ?? 0)
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func deleteVehicle(_ vehicle: VehicleDTO) async {
        guard let vehicleId = vehicle.id else { return }
        isDeleting = true
        defer { isDeleting = false }

        do {
            try await SettingsService.deleteVehicle(id: vehicleId)
            vehicles.removeAll { $0.id == vehicleId }
            successMessage = "Araç silindi."
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    private func saveCompany() async {
        isSavingCompany = true
        defer { isSavingCompany = false }
        do {
            let company = CompanyDTO(
                id: companyId,
                name: companyName,
                phone: companyPhone.nilIfEmpty,
                address: companyAddress.nilIfEmpty,
                email: companyEmail.nilIfEmpty,
                logoUrl: nil
            )
            _ = try await SettingsService.updateCompany(company)
            await load(refresh: true)
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

struct UserEditorSheet: View {
    let user: UserDTO?
    let onSaved: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var username = ""
    @State private var fullName = ""
    @State private var role = "TECHNICIAN"
    @State private var password = ""
    @State private var isSaving = false
    @State private var errorMessage: String?

    private var canSave: Bool {
        !username.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        !fullName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty &&
        (user != nil || !password.isEmpty) &&
        !isSaving
    }
    
    var body: some View {
        NavigationStack {
            Form {
                TextField("Kullanıcı adı", text: $username)
                TextField("Ad Soyad", text: $fullName)
                Picker("Rol", selection: $role) {
                    Text("Teknisyen").tag("TECHNICIAN")
                    Text("Yönetici").tag("COMPANY_ADMIN")
                }
                SecureField(user == nil ? "Şifre" : "Yeni Şifre (opsiyonel)", text: $password)
            }
            .navigationTitle(user == nil ? "Kullanıcı Ekle" : "Kullanıcı Düzenle")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("İptal") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") { Task { await save() } }
                        .disabled(!canSave)
                        .readOnlyProtected()
                }
            }
            .onAppear {
                username = user?.username ?? ""
                fullName = user?.fullName ?? ""
                role = user?.role ?? "TECHNICIAN"
            }
            .alert("Kullanıcı Kaydedilemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Tamam", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
        }
    }
    
    private func save() async {
        let dto = UserDTO(
            id: user?.id,
            username: username.trimmingCharacters(in: .whitespacesAndNewlines),
            fullName: fullName.nilIfEmpty,
            role: role,
            password: password.nilIfEmpty
        )
        isSaving = true
        defer { isSaving = false }
        do {
            if let id = user?.id {
                _ = try await SettingsService.updateUser(id: id, user: dto)
            } else {
                _ = try await SettingsService.createUser(dto)
            }
            await onSaved()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

struct VehicleEditorSheet: View {
    let vehicle: VehicleDTO?
    let onSaved: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var plate = ""
    @State private var driver = ""
    @State private var isActive = true
    @State private var isSaving = false
    @State private var errorMessage: String?

    private var canSave: Bool {
        !plate.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty && !isSaving
    }
    
    var body: some View {
        NavigationStack {
            Form {
                TextField("Plaka", text: $plate)
                TextField("Sürücü", text: $driver)
                Toggle("Aktif", isOn: $isActive)
            }
            .navigationTitle(vehicle == nil ? "Araç Ekle" : "Araç Düzenle")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("İptal") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") { Task { await save() } }
                        .disabled(!canSave)
                        .readOnlyProtected()
                }
            }
            .onAppear {
                plate = vehicle?.licensePlate ?? ""
                driver = vehicle?.driverName ?? ""
                isActive = vehicle?.isActive ?? true
            }
            .alert("Araç Kaydedilemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Tamam", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
        }
    }
    
    private func save() async {
        let dto = VehicleDTO(
            id: vehicle?.id,
            companyId: vehicle?.companyId,
            licensePlate: plate.trimmingCharacters(in: .whitespacesAndNewlines).uppercased(with: Locale(identifier: "tr_TR")),
            driverName: driver.nilIfEmpty,
            isActive: isActive
        )
        isSaving = true
        defer { isSaving = false }
        do {
            if let id = vehicle?.id {
                _ = try await SettingsService.updateVehicle(id: id, vehicle: dto)
            } else {
                _ = try await SettingsService.createVehicle(dto)
            }
            await onSaved()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private struct PendingUserReassignment: Identifiable {
    let user: UserDTO
    let activeTicketCount: Int

    var id: Int { user.id ?? -1 }
}

private struct ReassignTicketsBeforeDeleteSheet: View {
    let request: PendingUserReassignment
    let candidates: [UserDTO]
    let onCompleted: () async -> Void

    @Environment(\.dismiss) private var dismiss
    @State private var selectedUserId: Int?
    @State private var isDeleting = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("\(request.user.fullName ?? request.user.username) kullanıcısına atanmış \(request.activeTicketCount) açık iş emri var.")
                } footer: {
                    Text("Kullanıcı silinmeden önce açık işler seçtiğiniz personele devredilecek.")
                }

                if candidates.isEmpty {
                    Section {
                        ContentUnavailableView(
                            "Devredilecek Personel Yok",
                            systemImage: "person.crop.circle.badge.exclamationmark",
                            description: Text("Önce yeni bir teknisyen ekleyin veya açık iş emirlerini tamamlayın.")
                        )
                    }
                } else {
                    Section("İş Emirlerini Devret") {
                        Picker("Personel", selection: $selectedUserId) {
                            Text("Seçiniz").tag(Optional<Int>.none)
                            ForEach(candidates, id: \.username) { user in
                                if let id = user.id {
                                    Text(user.fullName ?? user.username).tag(Optional(id))
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("İşleri Devret")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Vazgeç") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Devret ve Sil", role: .destructive) {
                        Task { await reassignAndDelete() }
                    }
                    .disabled(selectedUserId == nil || isDeleting)
                }
            }
            .interactiveDismissDisabled(isDeleting)
            .alert("Kullanıcı Silinemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Tamam", role: .cancel) { errorMessage = nil }
            } message: {
                Text(errorMessage ?? "")
            }
        }
    }

    private func reassignAndDelete() async {
        guard let userId = request.user.id, let selectedUserId else { return }
        isDeleting = true
        defer { isDeleting = false }

        do {
            try await SettingsService.deleteUser(id: userId, reassignTo: selectedUserId)
            await onCompleted()
            dismiss()
        } catch {
            errorMessage = error.localizedDescription
        }
    }
}

private enum SettingsViewError: LocalizedError {
    case imageDataUnavailable

    var errorDescription: String? { "Seçilen görsel okunamadı." }
}

private extension String {
    var nilIfEmpty: String? {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : self
    }
}
