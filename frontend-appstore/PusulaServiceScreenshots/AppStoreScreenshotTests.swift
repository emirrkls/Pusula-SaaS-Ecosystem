import XCTest
import UIKit

final class AppStoreScreenshotTests: XCTestCase {
    private let scenes = ["overview", "operations", "finance", "inventory"]

    override func setUpWithError() throws {
        continueAfterFailure = false
    }

    func testAppStoreScreenshots() throws {
        for scene in scenes {
            let app = XCUIApplication()
            app.launchArguments = ["--app-store-screenshots"]
            app.launchEnvironment["PUSULA_SCREENSHOT_SCENE"] = scene
            app.launch()

            XCTAssertTrue(
                app.otherElements["screenshot.ready.\(scene)"].waitForExistence(timeout: 15),
                "Screenshot scene did not become ready: \(scene)"
            )

            // Fresh Cloud simulators may show a one-time Apple system banner
            // immediately after their first launch. Let it disappear so the
            // captured image contains only the app UI.
            if scene == scenes.first {
                sleep(6)
            }

            let attachment = XCTAttachment(screenshot: XCUIScreen.main.screenshot())
            attachment.name = "Pusula-\(deviceFamily)-\(scene)"
            attachment.lifetime = .keepAlways
            add(attachment)

            app.terminate()
        }
    }

    private var deviceFamily: String {
        UIDevice.current.userInterfaceIdiom == .pad ? "iPad" : "iPhone"
    }
}
