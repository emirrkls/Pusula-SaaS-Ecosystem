import Foundation
import XCTest
@testable import PusulaTickets

private struct Ticket: TicketListRecord {
    var id: Int
    var status: String? = "ASSIGNED"
    var assignedTechnicianId: Int? = 7
    var scheduledDate: String? = "2026-10-10T10:00:00"
    var scheduledEndDate: String? = nil
    var createdAt: String? = "2026-10-10T08:00:00"
    var completedAt: String? = nil
    var updatedAt: String? = nil
    var name: String = "Örnek müşteri"
    var probe: ExecutionProbe? = nil
    var ticketSearchValues: [String] {
        probe?.observe()
        return [String(id), name, "Klima bakım"]
    }
}

private final class ExecutionProbe: @unchecked Sendable {
    private let lock = NSLock()
    private var calls = 0
    private var ranOnMain = false
    func observe() {
        lock.lock(); defer { lock.unlock() }
        calls += 1
        ranOnMain = ranOnMain || Thread.isMainThread
    }
    func value() -> (calls: Int, ranOnMain: Bool) {
        lock.lock(); defer { lock.unlock() }
        return (calls, ranOnMain)
    }
}

private actor Gate {
    private var isOpen = false
    private var continuation: CheckedContinuation<Void, Never>?
    func wait() async {
        if !isOpen { await withCheckedContinuation { continuation = $0 } }
    }
    func open() {
        isOpen = true
        continuation?.resume()
        continuation = nil
    }
}

final class BusinessDateParserTests: XCTestCase {
    func testBackendWallTimeMatchesExplicitIstanbulOffset() throws {
        let expected = try XCTUnwrap(BusinessDateParser.parse("2026-10-10T12:21:19+03:00"))
        XCTAssertEqual(BusinessDateParser.parse("2026-10-10T12:21:19"), expected)
        XCTAssertEqual(BusinessDateParser.parse("2026-10-10T09:21:19Z"), expected)
        XCTAssertEqual(BusinessDateParser.parse("2026-10-10T12:21:19+0300"), expected)
    }

    func testFractionalSecondsFromOneThroughNineDigits() throws {
        let base = try XCTUnwrap(BusinessDateParser.parse("2026-10-10T12:21:19"))
        for digits in 1...9 {
            let text = "2026-10-10T12:21:19." + String(repeating: "1", count: digits)
            let date = try XCTUnwrap(BusinessDateParser.parse(text))
            let expected = (1...digits).reduce(0.0) { $0 + pow(0.1, Double($1)) }
            XCTAssertEqual(date.timeIntervalSince(base), expected, accuracy: 0.000001)
        }
        XCTAssertEqual(BusinessDateParser.parse("2026-10-10T12:21:19.123456+03:00"),
                       BusinessDateParser.parse("2026-10-10T09:21:19.123456Z"))
    }

    func testDatesAndLeapDaysAreStrict() {
        XCTAssertEqual(BusinessDateParser.parse("2026-10-10"), BusinessDateParser.parse("2026-10-10T00:00:00"))
        XCTAssertNotNil(BusinessDateParser.parse("2024-02-29T00:00:00"))
        for invalid in ["2026-02-29", "2026-04-31", "2026-13-01", "0000-01-01",
                        "2026-10-10T24:00:00", "2026-10-10T12:60:00", "2026-10-10T12:00:60"] {
            XCTAssertNil(BusinessDateParser.parse(invalid), invalid)
        }
    }

    func testMalformedAndUnboundedInputsAreRejected() {
        let invalid: [String?] = [nil, "", "not-a-date", "2026-10-10X12:00:00", "2026-10-10T12:00:00.",
                                 "2026-10-10T12:00:00.1234567890", "2026-10-10T12:00:00Zjunk",
                                 "2026-10-10T12:00:00+03:99", "2026-10-10T12:00:00+19:00",
                                 "2026-10-10T12:00:00+03", "２０２６-10-10", String(repeating: "9", count: 100_000)]
        for value in invalid { XCTAssertNil(BusinessDateParser.parse(value), String((value ?? "nil").prefix(50))) }
    }

    func testUTCDateBoundaryUsesBusinessDay() throws {
        let date = try XCTUnwrap(BusinessDateParser.parse("2026-10-09T22:30:00Z"))
        XCTAssertEqual(BusinessDateParser.startOfDay(date), BusinessDateParser.parse("2026-10-10"))
    }
}

final class TicketListTests: XCTestCase {
    private let today = BusinessDateParser.parse("2026-10-10")!
    private func query(_ filter: String = "Tümü", admin: Bool = true, search: String = "") -> TicketListQuery {
        TicketListQuery(filter: filter, isAdmin: admin, search: search, businessDay: today)
    }
    private func ids(_ records: [Ticket], _ query: TicketListQuery) throws -> [Int] {
        try TicketListIndex(records: records).snapshot(for: query).rows.map(\.id)
    }

    func testPendingUnassignedAndAdminFilters() throws {
        let records = [Ticket(id: 1, status: "PENDING", assignedTechnicianId: nil),
                       Ticket(id: 2, status: "ASSIGNED"), Ticket(id: 3, status: "IN_PROGRESS"),
                       Ticket(id: 4, status: "COMPLETED"), Ticket(id: 5, status: "CANCELLED")]
        XCTAssertEqual(try ids(records, query("Atama Bekleyen")), [1])
        XCTAssertEqual(try ids(records, query("Atanan")), [2])
        XCTAssertEqual(try ids(records, query("İşlemde")), [3])
        XCTAssertEqual(try ids(records, query("Tamamlanan")), [4])
        XCTAssertEqual(try ids(records, query("İptal Edilen")), [5])
        XCTAssertEqual(try TicketListIndex(records: records).snapshot(for: query()).pendingUnassigned.map(\.id), [1])
    }

    func testTechnicianTodayIncludesOverdueAndUndatedButNotFuture() throws {
        let records = [Ticket(id: 1, scheduledDate: "2026-10-09T10:00:00"),
                       Ticket(id: 2, scheduledDate: nil), Ticket(id: 3, scheduledDate: "2026-10-11T10:00:00"),
                       Ticket(id: 4, status: "COMPLETED")]
        XCTAssertEqual(try ids(records, query("Bugünkü İşler", admin: false)), [1, 2])
        XCTAssertEqual(try ids(records, query("İleri Tarihli İşler", admin: false)), [3])
        XCTAssertEqual(try ids(records, query("Atanan", admin: false)), [3, 2, 1])
    }

    func testSortOrderTiesAndMissingDatesStayDeterministic() throws {
        let records = [Ticket(id: 1, scheduledDate: "2026-10-09T10:00:00"),
                       Ticket(id: 2), Ticket(id: 3), Ticket(id: 4, scheduledDate: nil, createdAt: nil)]
        XCTAssertEqual(try ids(records, query()), [3, 2, 1, 4])
        XCTAssertEqual(try ids(records, query("Bugünkü İşler", admin: false)), [1, 3, 2, 4])
    }

    func testCompletedAndCancelledUseEventDateWithFallback() throws {
        let records = [Ticket(id: 1, status: "COMPLETED", completedAt: "2026-10-12T10:00:00"),
                       Ticket(id: 2, status: "CANCELLED", updatedAt: "2026-10-11T10:00:00"),
                       Ticket(id: 3, status: "COMPLETED", completedAt: "invalid")]
        XCTAssertEqual(try ids(records, query()), [1, 2, 3])
    }

    func testDateRangeIsInclusiveAndUsesRelevantBusinessDate() throws {
        let records = [Ticket(id: 1, scheduledDate: "2026-10-09T22:30:00Z"),
                       Ticket(id: 2, status: "COMPLETED", completedAt: "2026-10-11T01:00:00"),
                       Ticket(id: 3, scheduledDate: nil, createdAt: nil)]
        var selection = query()
        selection.startDate = today; selection.endDate = today
        XCTAssertEqual(try ids(records, selection), [1])
        selection.startDate = nil
        XCTAssertEqual(try ids(records, selection), [1])
        selection.startDate = BusinessDateParser.parse("2026-10-11")
        selection.endDate = BusinessDateParser.parse("2026-10-12")
        XCTAssertEqual(try ids(records, selection), [2])
    }

    func testTodayOpenedRespectsUTCOffset() throws {
        XCTAssertEqual(try ids([Ticket(id: 1, createdAt: "2026-10-09T22:30:00Z"),
                                Ticket(id: 2, createdAt: "2026-10-09T20:30:00Z")], query("Bugün Açılan")), [1])
    }

    func testTurkishSearchAndWhitespace() throws {
        let records = [Ticket(id: 1, name: "İKLİMLENDİRME"), Ticket(id: 2, name: "Özsoy Yapı")]
        XCTAssertEqual(try ids(records, query(search: "  iklimlendirme  ")), [1])
        XCTAssertEqual(try ids(records, query(search: "özsoy")), [2])
        XCTAssertEqual(try ids(records, query(search: "bakım")), [2, 1])
        XCTAssertEqual(try ids(records, query(search: "missing")), [])
    }

    func testCardDatesAndOverdueArePreparedOnce() throws {
        let record = Ticket(id: 1, scheduledDate: "2026-10-09T10:00:00", scheduledEndDate: "2026-10-09T12:00:00")
        let row = try XCTUnwrap(TicketListIndex(records: [record]).snapshot(for: query()).rows.first)
        XCTAssertTrue(row.scheduleLabel?.contains("10:00–12:00") == true)
        XCTAssertTrue(row.isOverdue(on: today))
        XCTAssertFalse(row.isOverdue(on: BusinessDateParser.parse("2026-10-09")!))
    }

    func testTenThousandTicketsAreParsedLinearlyAndQueriesNeverReparse() throws {
        let records = (1...10_000).map { Ticket(id: $0, name: "Müşteri \($0)") }
        var calls = 0
        let index = try TicketListIndex(records: records) { value in
            calls += 1
            return BusinessDateParser.parse(value)
        }
        XCTAssertEqual(calls, 30_000) // start, end, created: not once per sort comparison
        for _ in 0..<20 {
            XCTAssertEqual(try index.snapshot(for: query()).rows.count, 10_000)
            XCTAssertEqual(try index.snapshot(for: query(search: "9999")).rows.map(\.id), [9999])
        }
        XCTAssertEqual(calls, 30_000)
    }

    func testStaleQueryDataTenantAndBackgroundResultsAreRejected() {
        let request = TicketProjectionRequest(revision: UUID(), companyID: 10, query: query(), isActive: true)
        XCTAssertTrue(request.accepts(request, isCancelled: false))
        XCTAssertFalse(request.accepts(request, isCancelled: true))
        let newData = TicketProjectionRequest(revision: UUID(), companyID: 10, query: query(), isActive: true)
        let tenant = TicketProjectionRequest(revision: request.revision, companyID: 20, query: query(), isActive: true)
        let search = TicketProjectionRequest(revision: request.revision, companyID: 10, query: query(search: "new"), isActive: true)
        let background = TicketProjectionRequest(revision: request.revision, companyID: 10, query: query(), isActive: false)
        for current in [newData, tenant, search, background] {
            XCTAssertFalse(current.accepts(request, isCancelled: false))
        }
    }

    func testActorDoesNotRebuildIndexOnSearchOrForegroundAndRunsOffMainThread() async throws {
        let probe = ExecutionProbe()
        let records = [Ticket(id: 1, probe: probe)]
        let worker = TicketListProcessor<Ticket>()
        let revision = UUID()
        for selection in [query(), query(search: "1"), query("Bugünkü İşler", admin: false)] {
            let request = TicketProjectionRequest(revision: revision, companyID: 10, query: selection, isActive: true)
            let snapshot = try await worker.project(records: records, request: request)
            XCTAssertEqual(snapshot.rows.map(\.id), [1])
        }
        XCTAssertEqual(probe.value().calls, 1)
        XCTAssertFalse(probe.value().ranOnMain)
        let changed = TicketProjectionRequest(revision: UUID(), companyID: 10, query: query(), isActive: true)
        _ = try await worker.project(records: records, request: changed)
        XCTAssertEqual(probe.value().calls, 2)
    }

    func testCancelledAndBackgroundJobsNeverPublishOrBuildIndex() async throws {
        let probe = ExecutionProbe()
        let records = [Ticket(id: 1, probe: probe)]
        let worker = TicketListProcessor<Ticket>()
        let request = TicketProjectionRequest(revision: UUID(), companyID: 10, query: query(), isActive: true)
        let gate = Gate()
        let job = Task {
            await gate.wait()
            return try await worker.project(records: records, request: request)
        }
        job.cancel()
        await gate.open()
        do {
            _ = try await job.value
            XCTFail("Cancelled projection unexpectedly completed")
        } catch is CancellationError { }
        let background = TicketProjectionRequest(revision: request.revision, companyID: 10, query: query(), isActive: false)
        do {
            _ = try await worker.project(records: records, request: background)
            XCTFail("Background projection unexpectedly completed")
        } catch is CancellationError { }
        XCTAssertEqual(probe.value().calls, 0)
    }
}
