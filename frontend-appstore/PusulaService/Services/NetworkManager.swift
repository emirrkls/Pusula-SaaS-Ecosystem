import Foundation

/// Centralized network manager using async/await.
/// Automatically attaches JWT Bearer token to authenticated requests.
actor NetworkManager {
    static let shared = NetworkManager()
    
    private let baseURL = "https://api.pusulaiklimlendirme.com"
    
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
        case 400, 422:
            throw NetworkError.badRequest(apiErrorMessage(from: data) ?? "Gönderilen bilgiler geçersiz. Lütfen alanları kontrol edin.")
        case 401:
            await MainActor.run {
                SessionManager.shared.handleUnauthorized()
            }
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
        case 409:
            let errorBody = try? JSONDecoder().decode(ErrorResponse.self, from: data)
            throw NetworkError.conflict(
                code: errorBody?.error,
                message: errorBody?.message,
                count: errorBody?.count
            )
        default:
            throw NetworkError.serverError(httpResponse.statusCode)
        }
        
        if data.isEmpty, T.self == EmptyResponse.self {
            return EmptyResponse() as! T
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
    
    func downloadData(_ path: String, requiresAuth: Bool = true) async throws -> Data {
        guard let url = URL(string: baseURL + path) else {
            throw NetworkError.invalidURL
        }
        
        var request = URLRequest(url: url)
        request.httpMethod = HTTPMethod.GET.rawValue
        request.timeoutInterval = 60
        
        if requiresAuth, let token = authToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NetworkError.invalidResponse
        }
        
        if httpResponse.statusCode == 401 {
            await MainActor.run {
                SessionManager.shared.handleUnauthorized()
            }
            throw NetworkError.unauthorized
        }

        guard (200...299).contains(httpResponse.statusCode) else {
            if httpResponse.statusCode == 400 || httpResponse.statusCode == 422 {
                throw NetworkError.badRequest(apiErrorMessage(from: data) ?? "İstek tamamlanamadı. Lütfen bilgileri kontrol edin.")
            }
            throw NetworkError.serverError(httpResponse.statusCode)
        }
        
        return data
    }
    
    func uploadMultipart<T: Decodable>(
        path: String,
        fileData: Data,
        fileName: String,
        mimeType: String,
        fieldName: String = "file",
        textFields: [String: String] = [:]
    ) async throws -> T {
        let data = try await uploadMultipartData(
            path: path,
            fileData: fileData,
            fileName: fileName,
            mimeType: mimeType,
            fieldName: fieldName,
            textFields: textFields
        )

        if data.isEmpty, T.self == EmptyResponse.self {
            return EmptyResponse() as! T
        }

        return try JSONDecoder().decode(T.self, from: data)
    }

    func uploadMultipartString(
        path: String,
        fileData: Data,
        fileName: String,
        mimeType: String,
        fieldName: String = "file",
        textFields: [String: String] = [:]
    ) async throws -> String {
        let data = try await uploadMultipartData(
            path: path,
            fileData: fileData,
            fileName: fileName,
            mimeType: mimeType,
            fieldName: fieldName,
            textFields: textFields
        )
        return String(decoding: data, as: UTF8.self)
    }

    private func uploadMultipartData(
        path: String,
        fileData: Data,
        fileName: String,
        mimeType: String,
        fieldName: String,
        textFields: [String: String]
    ) async throws -> Data {
        guard let url = URL(string: baseURL + path) else {
            throw NetworkError.invalidURL
        }
        
        let boundary = "Boundary-\(UUID().uuidString)"
        var request = URLRequest(url: url)
        request.httpMethod = HTTPMethod.POST.rawValue
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.timeoutInterval = 120
        
        if let token = authToken {
            request.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        
        var body = Data()
        for (key, value) in textFields {
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"\(key)\"\r\n\r\n".data(using: .utf8)!)
            body.append("\(value)\r\n".data(using: .utf8)!)
        }
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"\(fieldName)\"; filename=\"\(fileName)\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: \(mimeType)\r\n\r\n".data(using: .utf8)!)
        body.append(fileData)
        body.append("\r\n".data(using: .utf8)!)
        body.append("--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body
        
        let (data, response) = try await URLSession.shared.data(for: request)
        
        guard let httpResponse = response as? HTTPURLResponse else {
            throw NetworkError.invalidResponse
        }
        
        switch httpResponse.statusCode {
        case 200...299:
            break
        case 400, 422:
            throw NetworkError.badRequest(apiErrorMessage(from: data) ?? "Gönderilen bilgiler geçersiz. Lütfen alanları kontrol edin.")
        case 401:
            await MainActor.run {
                SessionManager.shared.handleUnauthorized()
            }
            throw NetworkError.unauthorized
        case 403:
            if let errorBody = try? JSONDecoder().decode(ErrorResponse.self, from: data) {
                throw NetworkError.forbidden(errorBody.message ?? "Erişim reddedildi")
            }
            throw NetworkError.forbidden("Bu işlem için yetkiniz yok")
        case 429:
            if let errorBody = try? JSONDecoder().decode(ErrorResponse.self, from: data) {
                throw NetworkError.quotaExceeded(errorBody.message ?? "Kota aşıldı")
            }
            throw NetworkError.quotaExceeded("Kota limitinize ulaştınız")
        case 409:
            let errorBody = try? JSONDecoder().decode(ErrorResponse.self, from: data)
            throw NetworkError.conflict(
                code: errorBody?.error,
                message: errorBody?.message,
                count: errorBody?.count
            )
        default:
            throw NetworkError.serverError(httpResponse.statusCode)
        }

        return data
    }

    private func apiErrorMessage(from data: Data) -> String? {
        guard let response = try? JSONDecoder().decode(ErrorResponse.self, from: data),
              let message = response.message?.trimmingCharacters(in: .whitespacesAndNewlines),
              !message.isEmpty else {
            return nil
        }
        return message
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
    case badRequest(String)
    case forbidden(String)
    case quotaExceeded(String)
    case conflict(code: String?, message: String?, count: Int?)
    case serverError(Int)
    case decodingError(Error)
    
    var errorDescription: String? {
        switch self {
        case .invalidURL: return "Geçersiz URL"
        case .invalidResponse: return "Sunucu yanıtı geçersiz"
        case .unauthorized: return "Oturum süresi doldu. Lütfen tekrar giriş yapın."
        case .badRequest(let message): return message
        case .forbidden(let msg): return msg
        case .quotaExceeded(let msg): return msg
        case .conflict(_, let message, _): return message ?? "İşlem mevcut verilerle çakışıyor."
        case .serverError(let code): return "Sunucu hatası (\(code))"
        case .decodingError(let err): return "Veri çözümleme hatası: \(err.localizedDescription)"
        }
    }
}

struct ErrorResponse: Decodable {
    let message: String?
    let error: String?
    let count: Int?
}

struct EmptyResponse: Decodable {}
