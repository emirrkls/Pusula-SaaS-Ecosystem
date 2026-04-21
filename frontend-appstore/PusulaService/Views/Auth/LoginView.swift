import SwiftUI

struct LoginView: View {
    @State private var selectedTab = 0 // 0 = Individual, 1 = Corporate
    @State private var username = ""
    @State private var password = ""
    @State private var orgCode = ""
    @State private var isLoading = false
    @State private var errorMessage: String?
    @State private var showRegister = false
    
    let session = SessionManager.shared
    
    var body: some View {
        NavigationStack {
            ZStack {
                // Background gradient
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
                    VStack(spacing: 32) {
                        // Logo & Brand
                        VStack(spacing: 12) {
                            Image(systemName: "safari")
                                .font(.system(size: 64))
                                .foregroundStyle(
                                    LinearGradient(
                                        colors: [.blue, .cyan],
                                        startPoint: .topLeading,
                                        endPoint: .bottomTrailing
                                    )
                                )
                            
                            Text("Pusula Servis")
                                .font(.system(size: 32, weight: .bold, design: .rounded))
                                .foregroundColor(.white)
                            
                            Text("Servis yönetim ekosistemine hoş geldiniz")
                                .font(.subheadline)
                                .foregroundColor(.white.opacity(0.6))
                        }
                        .padding(.top, 60)
                        
                        // Tab Selection: Individual / Corporate
                        Picker("Giriş Türü", selection: $selectedTab) {
                            Text("Bireysel").tag(0)
                            Text("Kurumsal").tag(1)
                        }
                        .pickerStyle(.segmented)
                        .padding(.horizontal, 32)
                        
                        // Login Form
                        VStack(spacing: 16) {
                            if selectedTab == 1 {
                                // Corporate: Org Code field
                                HStack {
                                    Image(systemName: "building.2")
                                        .foregroundColor(.white.opacity(0.5))
                                    TextField("Kurum Kodu", text: $orgCode)
                                        .textContentType(.organizationName)
                                        .autocapitalization(.allCharacters)
                                        .foregroundColor(.white)
                                }
                                .padding()
                                .background(.white.opacity(0.08))
                                .clipShape(RoundedRectangle(cornerRadius: 14))
                            }
                            
                            // Username
                            HStack {
                                Image(systemName: "person")
                                    .foregroundColor(.white.opacity(0.5))
                                TextField(
                                    selectedTab == 0 ? "E-posta veya kullanıcı adı" : "Kullanıcı adı",
                                    text: $username
                                )
                                .textContentType(.username)
                                .autocapitalization(.none)
                                .keyboardType(selectedTab == 0 ? .emailAddress : .default)
                                .foregroundColor(.white)
                            }
                            .padding()
                            .background(.white.opacity(0.08))
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            
                            // Password
                            HStack {
                                Image(systemName: "lock")
                                    .foregroundColor(.white.opacity(0.5))
                                SecureField("Şifre", text: $password)
                                    .textContentType(.password)
                                    .foregroundColor(.white)
                            }
                            .padding()
                            .background(.white.opacity(0.08))
                            .clipShape(RoundedRectangle(cornerRadius: 14))
                            
                            // Error message
                            if let error = errorMessage {
                                HStack {
                                    Image(systemName: "exclamationmark.triangle.fill")
                                    Text(error)
                                }
                                .font(.caption)
                                .foregroundColor(.red)
                                .padding(.horizontal)
                                .transition(.opacity)
                            }
                            
                            // Login Button
                            Button(action: handleLogin) {
                                HStack {
                                    if isLoading {
                                        ProgressView()
                                            .tint(.white)
                                    } else {
                                        Text("Giriş Yap")
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
                            .disabled(isLoading || username.isEmpty || password.isEmpty)
                            .opacity(username.isEmpty || password.isEmpty ? 0.6 : 1)
                        }
                        .padding(.horizontal, 32)
                        
                        // Register link (only for individual)
                        if selectedTab == 0 {
                            Button(action: { showRegister = true }) {
                                HStack(spacing: 4) {
                                    Text("Hesabınız yok mu?")
                                        .foregroundColor(.white.opacity(0.5))
                                    Text("Ücretsiz Kayıt Ol")
                                        .foregroundColor(.cyan)
                                        .fontWeight(.semibold)
                                }
                                .font(.subheadline)
                            }
                        }
                        
                        Spacer(minLength: 40)
                    }
                }
            }
            .animation(.easeInOut(duration: 0.25), value: selectedTab)
            .animation(.easeInOut(duration: 0.25), value: errorMessage)
            .sheet(isPresented: $showRegister) {
                RegisterView()
            }
        }
    }
    
    private func handleLogin() {
        guard !username.isEmpty, !password.isEmpty else { return }
        isLoading = true
        errorMessage = nil
        
        Task {
            do {
                let response: AuthResponse
                if selectedTab == 1 && !orgCode.isEmpty {
                    response = try await AuthService.loginCorporate(
                        orgCode: orgCode,
                        username: username,
                        password: password
                    )
                } else {
                    response = try await AuthService.login(
                        username: username,
                        password: password
                    )
                }
                
                await MainActor.run {
                    session.configure(from: response)
                    isLoading = false
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
    LoginView()
}
