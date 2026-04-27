import Foundation

/// Centralized network manager using async/await.
/// Automatically attaches JWT Bearer token to authenticated requests.
actor NetworkManager {
    static let shared = NetworkManager()
    
    private let baseURL = "http://168.231.104.133:8080"
    
    private var authToken: String?
    
    func setToken(_ token: String?) {
        self.authToken = token
    }
    
    // MARK: - Generic Request
    
    func request<T: Decodable>(
        _ method: HTTPMethod,
        path: String,
        body: (any Encodable)? = nil,
        requiresAuth: Bool = true
    ) async throws -> T {
        guard let url = URL(string: baseURL + path) else {
            throw NetworkError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = method.rawValue
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.setValue("application/json", forHTTPHeaderField: "Accept")
        request.timeoutInterval = 30
        
        // Attach JWT if authenticated
        if requiresAuth, let token = authToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        
        // Encode body
        if let body = body {
            let encoder = JSONEncoder()
            request.httpBody = try encoder.encode(body)
        }
        
        // Execute
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NetworkError.invalidResponse
        }
        
        // Handle HTTP errors
        switch httpResponse.statusCode {
        case 200...299:
            break
        case 401:
            throw NetworkError.unauthorized
        case 403:
            // Check for feature gate or quota error
            if let errorBody = try? JSONDecoder().decode(ErrorResponse.self, from: data) {
                throw NetworkError.forbidden(errorBody.message ?? "Erişim reddedildi")
            }
            throw NetworkError.forbidden("Bu işlem için yetkiniz yok")
        case 429:
            if let errorBody = try? JSONDecoder().decode(ErrorResponse.self, from: data) {
                throw NetworkError.quotaExceeded(errorBody.message ?? "Kota aşıldı")
            }
            throw NetworkError.quotaExceeded("Kota limitinize ulaştınız")
        default:
            throw NetworkError.serverError(httpResponse.statusCode)
        }
        
        let decoder = JSONDecoder()
        return try decoder.decode(T.self, from: data)
    }
    
    // MARK: - Convenience Methods
    
    func get<T: Decodable>(_ path: String, requiresAuth: Bool = true) async throws -> T {
        try await request(.GET, path: path, requiresAuth: requiresAuth)
    }
    
    func post<T: Decodable>(_ path: String, body: any Encodable, requiresAuth: Bool = true) async throws -> T {
        try await request(.POST, path: path, body: body, requiresAuth: requiresAuth)
    }
    
    func put<T: Decodable>(_ path: String, body: any Encodable) async throws -> T {
        try await request(.PUT, path: path, body: body)
    }
    
    func delete(_ path: String) async throws {
        let _: EmptyResponse = try await request(.DELETE, path: path)
    }
}

// MARK: - Supporting Types

enum HTTPMethod: String {
    case GET, POST, PUT, DELETE, PATCH
}

enum NetworkError: LocalizedError {
    case invalidURL
    case invalidResponse
    case unauthorized
    case forbidden(String)
    case quotaExceeded(String)
    case serverError(Int)
    case decodingError(Error)
    
    var errorDescription: String? {
        switch self {
        case .invalidURL: return "Geçersiz URL"
        case .invalidResponse: return "Sunucu yanıtı geçersiz"
        case .unauthorized: return "Oturum süresi doldu. Lütfen tekrar giriş yapın."
        case .forbidden(let msg): return msg
        case .quotaExceeded(let msg): return msg
        case .serverError(let code): return "Sunucu hatası (\(code))"
        case .decodingError(let err): return "Veri çözümleme hatası: \(err.localizedDescription)"
        }
    }
}

struct ErrorResponse: Decodable {
    let message: String?
    let error: String?
}

struct EmptyResponse: Decodable {}
