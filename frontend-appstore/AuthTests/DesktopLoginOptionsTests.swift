import XCTest
@testable import PusulaAuth

final class DesktopLoginOptionsTests: XCTestCase {
    func testLegacyNewSocialAccountOffersOnlyOptionalDesktopSetup() throws {
        let options = try decode(#"{"requiresPasswordSetup":true,"loginUsername":"demo@privaterelay.appleid.com"}"#)
        XCTAssertTrue(options.canSetPassword)
        XCTAssertEqual(options.loginUsername, "demo@privaterelay.appleid.com")
    }

    func testExistingLocalCredentialCannotBeOverwrittenFromInitialSetup() throws {
        let options = try decode(#"{"requiresPasswordSetup":false,"loginUsername":"existing"}"#)
        XCTAssertFalse(options.canSetPassword)
    }

    func testPartialOlderProfileDoesNotInventPasswordCapabilityOrUsername() throws {
        let options = try decode(#"{"role":"COMPANY_ADMIN","companyId":7}"#)
        XCTAssertEqual(options, .unavailable)
        XCTAssertFalse(options.canSetPassword)
    }

    func testNullCapabilityIsNotTreatedAsAnInitialPasswordRequirement() throws {
        XCTAssertFalse(try decode(#"{"requiresPasswordSetup":null}"#).canSetPassword)
    }

    func testSettingPasswordDisablesSetupAndRetainsExactLoginUsername() throws {
        let options = try decode(#"{"requiresPasswordSetup":true,"loginUsername":"relay@privaterelay.appleid.com"}"#)
        let saved = options.afterSettingPassword
        XCTAssertFalse(saved.canSetPassword)
        XCTAssertEqual(saved.loginUsername, options.loginUsername)
        XCTAssertEqual(saved.afterSettingPassword, saved)
    }

    func testMalformedCapabilityIsRejectedInsteadOfBeingTruthy() {
        XCTAssertThrowsError(try decode(#"{"requiresPasswordSetup":"true"}"#))
    }

    private func decode(_ json: String) throws -> DesktopLoginOptions {
        try JSONDecoder().decode(DesktopLoginOptions.self, from: Data(json.utf8))
    }
}
