// swift-tools-version: 5.9
import PackageDescription

// Production routing, catalog and ticket policies have no UIKit dependency. Run their regressions
// on macOS/Linux without an iOS simulator or production credentials.
let package = Package(
    name: "PusulaRouting",
    products: [
        .library(name: "PusulaRouting", targets: ["PusulaRouting"]),
        .library(name: "PusulaBilling", targets: ["PusulaBilling"]),
        .library(name: "PusulaTickets", targets: ["PusulaTickets"])
    ],
    targets: [
        .target(name: "PusulaRouting", path: "PusulaService/Core/Routing"),
        .testTarget(name: "PusulaRoutingTests", dependencies: ["PusulaRouting"], path: "RoutingTests"),
        .target(name: "PusulaBilling", path: "PusulaService/Core/Billing"),
        .testTarget(name: "PusulaBillingTests", dependencies: ["PusulaBilling"], path: "BillingTests"),
        .target(name: "PusulaTickets", path: "PusulaService/Core/Tickets"),
        .testTarget(name: "PusulaTicketTests", dependencies: ["PusulaTickets"], path: "TicketTests")
    ]
)
