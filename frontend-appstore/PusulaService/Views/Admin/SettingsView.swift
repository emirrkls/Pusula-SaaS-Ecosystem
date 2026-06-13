import SwiftUI
import PhotosUI

struct SettingsView: View {
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
    
    var body: some View {
        VStack(spacing: 0) {
            Picker("Ayarlar", selection: $selectedTab) {
                Text("Kullanıcılar").tag(0)
                Text("Araçlar").tag(1)
                Text("Firma").tag(2)
            }
            .pickerStyle(.segmented)
            .padding()
            
            if isLoading {
                Spacer()
                ProgressView()
                Spacer()
            } else {
                TabView(selection: $selectedTab) {
                    usersTab.tag(0)
                    vehiclesTab.tag(1)
                    companyTab.tag(2)
                }
                .tabViewStyle(.page(indexDisplayMode: .never))
            }
        }
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
            
            ForEach(users, id: \.username) { user in
                VStack(alignment: .leading, spacing: 4) {
                    Text(user.fullName ?? user.username).font(.headline)
                    Text(user.role).font(.caption).foregroundStyle(.secondary)
                    Button("Düzenle") {
                        editingUser = user
                        showUserForm = true
                    }
                    .font(.caption.weight(.semibold))
                    .readOnlyProtected()
                }
                .padding(.vertical, 4)
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
            
            ForEach(vehicles, id: \.licensePlate) { vehicle in
                VStack(alignment: .leading, spacing: 4) {
                    Text(vehicle.licensePlate).font(.headline)
                    if let driver = vehicle.driverName {
                        Text(driver).font(.caption).foregroundStyle(.secondary)
                    }
                    Button("Düzenle") {
                        editingVehicle = vehicle
                        showVehicleForm = true
                    }
                    .font(.caption.weight(.semibold))
                    .readOnlyProtected()
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
                Task {
                    let company = CompanyDTO(
                        id: companyId,
                        name: companyName,
                        phone: companyPhone.nilIfEmpty,
                        address: companyAddress.nilIfEmpty,
                        email: companyEmail.nilIfEmpty,
                        logoUrl: nil
                    )
                    _ = try? await SettingsService.updateCompany(company)
                    await load(refresh: true)
                }
            }
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
    
    private func load(refresh: Bool = false) async {
        if !refresh { isLoading = true }
        async let usersTask = SettingsService.getUsers()
        async let vehiclesTask = SettingsService.getVehicles()
        async let companyTask = SettingsService.getCompany()
        let (loadedUsers, loadedVehicles, loadedCompany) = (
            (try? await usersTask) ?? [],
            (try? await vehiclesTask) ?? [],
            try? await companyTask
        )
        await MainActor.run {
            users = loadedUsers
            vehicles = loadedVehicles
            if let loadedCompany {
                companyId = loadedCompany.id
                companyName = loadedCompany.name
                companyPhone = loadedCompany.phone ?? ""
                companyAddress = loadedCompany.address ?? ""
                companyEmail = loadedCompany.email ?? ""
            }
            isLoading = false
        }
    }
    
    private func uploadLogo(item: PhotosPickerItem) async {
        guard let data = try? await item.loadTransferable(type: Data.self) else { return }
        _ = try? await SettingsService.uploadCompanyLogo(imageData: data)
        await load(refresh: true)
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
                        .readOnlyProtected()
                }
            }
            .onAppear {
                username = user?.username ?? ""
                fullName = user?.fullName ?? ""
                role = user?.role ?? "TECHNICIAN"
            }
        }
    }
    
    private func save() async {
        let dto = UserDTO(id: user?.id, username: username, fullName: fullName.nilIfEmpty, role: role, password: password.nilIfEmpty)
        if let id = user?.id {
            _ = try? await SettingsService.updateUser(id: id, user: dto)
            if !password.isEmpty {
                try? await SettingsService.resetPassword(userId: id, newPassword: password)
            }
        } else {
            _ = try? await SettingsService.createUser(dto)
        }
        await onSaved()
        dismiss()
    }
}

struct VehicleEditorSheet: View {
    let vehicle: VehicleDTO?
    let onSaved: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var plate = ""
    @State private var driver = ""
    @State private var isActive = true
    
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
                        .readOnlyProtected()
                }
            }
            .onAppear {
                plate = vehicle?.licensePlate ?? ""
                driver = vehicle?.driverName ?? ""
                isActive = vehicle?.isActive ?? true
            }
        }
    }
    
    private func save() async {
        let dto = VehicleDTO(id: vehicle?.id, companyId: vehicle?.companyId, licensePlate: plate, driverName: driver.nilIfEmpty, isActive: isActive)
        if let id = vehicle?.id {
            _ = try? await SettingsService.updateVehicle(id: id, vehicle: dto)
        } else {
            _ = try? await SettingsService.createVehicle(dto)
        }
        await onSaved()
        dismiss()
    }
}

private extension String {
    var nilIfEmpty: String? {
        trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? nil : self
    }
}
