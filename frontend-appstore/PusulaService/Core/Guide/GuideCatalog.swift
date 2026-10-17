import Foundation

public struct GuideCategory: Codable, Identifiable, Sendable {
    public let id: String
    public let title: String
    public let summary: String
}
public struct GuideLessonStep: Codable, Sendable {
    public let title: String
    public let body: String
    public let tour: GuideTourRoute
}
public struct GuideTopic: Codable, Identifiable, Sendable {
    public let id: String
    public let category: String
    public let title: String
    public let summary: String
    public let revision: Int
    public let roles: [String]
    public let platforms: [String]
    public let requiredFeature: String?
    public let isNew: Bool
    public let locations: [String: String]
    public let steps: [GuideLessonStep]

    public func matches(_ query: String) -> Bool {
        let query = Self.normalized(query.trimmingCharacters(in: .whitespacesAndNewlines))
        return query.isEmpty || Self.normalized(([title, summary] + Array(locations.values) + steps.flatMap { [$0.title, $0.body] }).joined(separator: " ")).contains(query)
    }
    public func titleMatches(_ query: String) -> Bool {
        let query = Self.normalized(query.trimmingCharacters(in: .whitespacesAndNewlines))
        return !query.isEmpty && Self.normalized(title).contains(query)
    }
    private static func normalized(_ text: String) -> String {
        text.folding(options: [.diacriticInsensitive, .caseInsensitive], locale: Locale(identifier: "en_US_POSIX")).replacingOccurrences(of: "ı", with: "i")
    }
}
public struct GuideCatalog: Codable, Sendable {
    public let schemaVersion: Int
    public let revision: Int
    public let categories: [GuideCategory]
    public let topics: [GuideTopic]

    public static func decode(_ data: Data) throws -> GuideCatalog {
        let value = try JSONDecoder().decode(GuideCatalog.self, from: data)
        guard value.schemaVersion == 1, value.revision > 0,
              !value.categories.isEmpty, !value.topics.isEmpty, value.topics.count <= 500,
              Set(value.categories.map(\.id)).count == value.categories.count,
              Set(value.topics.map(\.id)).count == value.topics.count else { throw GuideCatalogError.invalid }
        let categories = Set(value.categories.map(\.id))
        let roles: Set<String> = ["COMPANY_ADMIN", "SUPER_ADMIN", "TECHNICIAN"]
        for topic in value.topics {
            guard categories.contains(topic.category), !topic.id.isEmpty, topic.revision > 0,
                  !topic.title.isEmpty, !topic.summary.isEmpty,
                  !topic.roles.isEmpty, Set(topic.roles).isSubset(of: roles),
                  !topic.platforms.isEmpty, Set(topic.platforms).isSubset(of: ["ios", "android"]),
                  topic.platforms.allSatisfy({ !(topic.locations[$0] ?? "").isEmpty }),
                  (2...6).contains(topic.steps.count),
                  topic.steps.allSatisfy({ !$0.title.isEmpty && !$0.body.isEmpty && $0.tour.target.range(of:"^[a-z][a-z-]{0,60}$",options:.regularExpression) != nil }) else { throw GuideCatalogError.invalid }
        }
        return value
    }
    public func visibleTopics(role: String, platform: String, category: String? = nil, query: String = "") -> [GuideTopic] {
        topics.filter { $0.roles.contains(role) && $0.platforms.contains(platform) && (category == nil || $0.category == category) && $0.matches(query) }
    }
}
public enum GuideCatalogError: Error { case invalid }

/// Both onboarding and mini-tours use bounded indices; the last press finishes.
public struct GuideCursor: Equatable, Sendable {
    public let count: Int
    public private(set) var index: Int
    public init(count: Int, index: Int = 0) {
        self.count = max(1, count)
        self.index = min(max(0, index), max(0, count - 1))
    }
    public var isLast: Bool { index == count - 1 }
    @discardableResult public mutating func advance() -> Bool {
        guard !isLast else { return true }
        index += 1
        return false
    }
    public mutating func back() { index = max(0, index - 1) }
}
public struct GuideRecord: Codable, Equatable, Sendable {
    public var step: Int = 0
    public var completedRevision: Int = 0
    public init(step: Int = 0, completedRevision: Int = 0) { self.step = step; self.completedRevision = completedRevision }
    public func isComplete(_ topic: GuideTopic) -> Bool { completedRevision >= topic.revision }
    public func startIndex(_ topic: GuideTopic) -> Int { isComplete(topic) ? 0 : GuideCursor(count: topic.steps.count, index: step).index }
}
