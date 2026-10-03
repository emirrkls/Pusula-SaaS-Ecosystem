// swift-tools-version: 5.9
import PackageDescription

// The production routing policy has no UIKit dependency. Run its regressions
// on macOS/Linux without an iOS simulator or production credentials.
let package = Package(
    name: "PusulaRouting",
    products: [.library(name: "PusulaRouting", targets: ["PusulaRouting"])],
    targets: [
        .target(name: "PusulaRouting", path: "PusulaService/Core/Routing"),
        .testTarget(name: "PusulaRoutingTests", dependencies: ["PusulaRouting"], path: "RoutingTests")
    ]
)
