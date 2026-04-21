import SwiftUI

struct RegisterView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var fullName = ""
    @State private var email = ""
    @State private var password = ""
    @State private var confirmPassword = ""
    @State private var isLoading = false
    @State private var errorMessage: String?
    
    let session = SessionManager.shared
    
    var passwordsMatch: Bool { password == confirmPassword && !password.isEmpty }
    var isFormValid: Bool { !fullName.isEmpty && !email.isEmpty && passwordsMatch }
    
    var body: some View {
        NavigationStack {
            ZStack {
                LinearGradient(
                    colors: [
                        Color(red: 0.06, green: 0.09, blue: 0.16),
                        Color(red: 0.10, green: 0.14, blue: 0.25)
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                .ignoresSafeArea()
                
                ScrollView {
                    VStack(spacing: 24) {
                        // Header
                        VStack(spacing: 8) {
                            Image(systemName: "person.badge.plus")
                                .font(.system(size: 48))
                                .foregroundStyle(.cyan.gradient)
                            
                            Text("Ücretsiz Hesap Oluştur")
                                .font(.title2.weight(.bold))
                                .foregroundColor(.white)
                            
                            Text("14 gün ücretsiz deneme süresi ile\nÇırak Paketi'ne başlayın")
                                .font(.subheadline)
                                .foregroundColor(.white.opacity(0.6))
                                .multilineTextAlignment(.center)
                        }
                        .padding(.top, 32)
                        
                        // Form Fields
                        VStack(spacing: 16) {
                            formField(icon: "person", placeholder: "Ad Soyad", text: $fullName)
                                .textContentType(.name)
                            
                            formField(icon: "envelope", placeholder: "E-posta", text: $email)
                                .textContentType(.emailAddress)
                                .keyboardType(.emailAddress)
                                .autocapitalization(.none)
                            
                            HStack {
                                Image(systemName: "lock")
                                    .foregroundColor(.white.opacity(0.5))
                                SecureField("Şifre (min 6 karakter)", text: $password)
                                    .textContentType(.newPassword)
                                    .foregroundColor(.white)
                            }
                            .padding()
                            .background(.white.opacity(0.08))
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            
                            HStack {
                                Image(systemName: "lock.rotation")
                                    .foregroundColor(.white.opacity(0.5))
                                SecureField("Şifre Tekrar", text: $confirmPassword)
                                    .foregroundColor(.white)
                            }
                            .padding()
                            .background(
                                !confirmPassword.isEmpty && !passwordsMatch
                                    ? Color.red.opacity(0.15)
                                    : Color.white.opacity(0.08)
                            )
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            .overlay(
                                RoundedRectangle(cornerRadius: 14)
                                    .stroke(
                                        !confirmPassword.isEmpty && !passwordsMatch
                                            ? .red.opacity(0.5)
                                            : .clear,
                                        lineWidth: 1
                                    )
                            )
                        }
                        .padding(.horizontal, 32)
                        
                        // Plan badge
                        HStack(spacing: 8) {
                            Image(systemName: "gift")
                            Text("Çırak Paketi • 14 Gün Ücretsiz")
                        }
                        .font(.caption.weight(.medium))
                        .foregroundColor(.cyan)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(.cyan.opacity(0.1))
                        .clipShape(Capsule())
                        
                        // Error
                        if let error = errorMessage {
                            Text(error)
                                .font(.caption)
                                .foregroundColor(.red)
                                .padding(.horizontal, 32)
                        }
                        
                        // Register Button
                        Button(action: handleRegister) {
                            HStack {
                                if isLoading {
                                    ProgressView().tint(.white)
                                } else {
                                    Text("Hesap Oluştur")
                                        .fontWeight(.semibold)
                                    Image(systemName: "arrow.right")
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 16)
                        }
                        .background(
                            LinearGradient(
                                colors: [.blue, .cyan.opacity(0.8)],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .foregroundColor(.white)
                        .clipShape(RoundedRectangle(cornerRadius: 14))
                        .disabled(!isFormValid || isLoading)
                        .opacity(isFormValid ? 1 : 0.5)
                        .padding(.horizontal, 32)
                        
                        Spacer(minLength: 40)
                    }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarLeading) {
                    Button(action: { dismiss() }) {
                        Image(systemName: "xmark.circle.fill")
                            .foregroundColor(.white.opacity(0.6))
                            .font(.title3)
                    }
                }
            }
        }
    }
    
    private func formField(icon: String, placeholder: String, text: Binding<String>) -> some View {
        HStack {
            Image(systemName: icon)
                .foregroundColor(.white.opacity(0.5))
            TextField(placeholder, text: text)
                .foregroundColor(.white)
        }
        .padding()
        .background(.white.opacity(0.08))
        .clipShape(RoundedRectangle(cornerRadius: 14))
    }
    
    private func handleRegister() {
        guard isFormValid, password.count >= 6 else {
            errorMessage = "Lütfen tüm alanları doldurun (şifre min. 6 karakter)"
            return
        }
        
        isLoading = true
        errorMessage = nil
        
        Task {
            do {
                let response = try await AuthService.registerIndividual(
                    email: email,
                    password: password,
                    fullName: fullName
                )
                await MainActor.run {
                    session.configure(from: response)
                    isLoading = false
                    dismiss()
                }
            } catch {
                await MainActor.run {
                    errorMessage = error.localizedDescription
                    isLoading = false
                }
            }
        }
    }
}

#Preview {
    RegisterView()
}
