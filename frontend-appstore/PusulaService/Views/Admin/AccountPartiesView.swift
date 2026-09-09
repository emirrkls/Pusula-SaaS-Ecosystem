import SwiftUI

struct AccountPartiesView: View {
    @State private var organizations: [AccountPartyDTO] = []
    @State private var showAdd = false
    @State private var isLoading = true
    @State private var errorMessage: String?

    var body: some View {
        List {
            Section {
                Text("Garanti, yetkili servis veya anlaşmalı işlerde bedelin aktarılacağı kurum/firma cari kartlarıdır.")
                    .font(.caption).foregroundStyle(.secondary)
            }
            Section("Kurum ve Firmalar") {
                ForEach(organizations) { party in
                    VStack(alignment: .leading, spacing: 5) {
                        Text(party.displayName).font(.headline)
                        HStack {
                            if let contact = party.contactPerson, !contact.isEmpty { Text(contact) }
                            if let phone = party.phone, !phone.isEmpty { Text(phone) }
                        }.font(.caption).foregroundStyle(.secondary)
                    }.padding(.vertical, 3)
                }
            }
        }
        .navigationTitle("Kurum Cari Kartları")
        .toolbar { ToolbarItem(placement: .primaryAction) { Button { showAdd = true } label: { Image(systemName: "plus") } } }
        .overlay { if isLoading { ProgressView("Kurumlar yükleniyor...") } }
        .task { await load() }
        .refreshable { await load() }
        .sheet(isPresented: $showAdd) { AddAccountPartySheet { await load() } }
        .alert("İşlem Başarısız", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
            Button("Tamam", role: .cancel) { errorMessage = nil }
        } message: { Text(errorMessage ?? "") }
    }

    private func load() async {
        isLoading = true; defer { isLoading = false }
        do { organizations = try await FinanceService.getAccountParties(type: "ORGANIZATION") }
        catch { errorMessage = error.localizedDescription }
    }
}

private struct AddAccountPartySheet: View {
    let onSaved: () async -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var name = ""
    @State private var legalName = ""
    @State private var contact = ""
    @State private var phone = ""
    @State private var taxNumber = ""
    @State private var paymentTermDays = "0"
    @State private var isSaving = false
    @State private var errorMessage: String?

    var body: some View {
        NavigationStack {
            Form {
                Section("Temel Bilgiler") {
                    TextField("Kurum / firma adı", text: $name)
                    TextField("Resmî unvan (isteğe bağlı)", text: $legalName)
                    TextField("Yetkili kişi", text: $contact)
                    TextField("Telefon", text: $phone).keyboardType(.phonePad)
                }
                Section("Finans") {
                    TextField("Vergi numarası", text: $taxNumber).keyboardType(.numberPad)
                    TextField("Ödeme vadesi (gün)", text: $paymentTermDays).keyboardType(.numberPad)
                }
            }
            .navigationTitle("Yeni Kurum Cari Kartı")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("İptal") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Kaydet") { Task { await save() } }
                        .disabled(isSaving || name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
            }
            .alert("Kurum Kaydedilemedi", isPresented: Binding(get: { errorMessage != nil }, set: { if !$0 { errorMessage = nil } })) {
                Button("Tamam", role: .cancel) { errorMessage = nil }
            } message: { Text(errorMessage ?? "") }
        }
    }

    private func save() async {
        isSaving = true; defer { isSaving = false }
        do {
            _ = try await FinanceService.createAccountParty(.init(
                partyType: "ORGANIZATION", displayName: name.trimmingCharacters(in: .whitespacesAndNewlines),
                legalName: legalName.isEmpty ? nil : legalName, taxNumber: taxNumber.isEmpty ? nil : taxNumber,
                phone: phone.isEmpty ? nil : phone, contactPerson: contact.isEmpty ? nil : contact,
                paymentTermDays: Int(paymentTermDays) ?? 0))
            await onSaved(); dismiss()
        } catch { errorMessage = error.localizedDescription }
    }
}
