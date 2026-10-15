import Foundation
import XCTest
@testable import PusulaDiagnostics

final class ConnectionTimestampTests: XCTestCase {
    func testSubsecondStartIsNotLostAndLegacyReportsStillDecode() throws {
        let started = Date(timeIntervalSince1970: 1_800_000_000.281)
        let record = ConnectionDiagnosticRecord(startedAt: started, operation: .api)
        let bytes = try ConnectionDiagnosticsStore.encoder().encode(record)
        let decoded = try ConnectionDiagnosticsStore.decoder().decode(ConnectionDiagnosticRecord.self, from: bytes)
        XCTAssertEqual(decoded.startedAt.timeIntervalSince1970, started.timeIntervalSince1970, accuracy: 0.001)
        var object = try XCTUnwrap(JSONSerialization.jsonObject(with: bytes) as? [String: Any])
        object["startedAt"] = "2026-10-14T20:33:58Z"
        XCTAssertNoThrow(try ConnectionDiagnosticsStore.decoder().decode(ConnectionDiagnosticRecord.self,
            from: JSONSerialization.data(withJSONObject: object)))
    }
    func testConnectionHintsCannotExportArbitraryStringsOrInvalidPorts() {
        var metric = ConnectionTransaction(); metric.localPort = 70000; metric.remoteAddressFamily = "SECRET-IP"
        XCTAssertNil(metric.redacted().localPort); XCTAssertNil(metric.redacted().remoteAddressFamily)
        metric.localPort = 55000; metric.remoteAddressFamily = "ipv4"
        XCTAssertEqual(metric.redacted().localPort, 55000); XCTAssertEqual(metric.redacted().remoteAddressFamily, "ipv4")
    }
}
