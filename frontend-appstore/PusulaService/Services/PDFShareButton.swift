import SwiftUI
import UIKit

/// A real UIKit source button anchors the iPad popover and finds its own PDF
/// sheet's presenter. No global window/root controller or competing SwiftUI sheet.
struct PDFShareButton: UIViewRepresentable {
    let url: URL
    @Binding var isSharing: Bool
    let onFailure: @MainActor (PDFShareFailure) -> Void

    func makeCoordinator() -> Coordinator { Coordinator(url: url, onBusy: { isSharing = $0 }, onFailure: onFailure) }

    func makeUIView(context: Context) -> UIButton {
        let button = UIButton(type: .system)
        var configuration = UIButton.Configuration.plain()
        configuration.image = UIImage(systemName: "square.and.arrow.up")
        configuration.title = String(localized: "pdf.share.action")
        configuration.imagePadding = 6
        configuration.contentInsets = NSDirectionalEdgeInsets(top: 10, leading: 8, bottom: 10, trailing: 8)
        button.configuration = configuration
        button.accessibilityLabel = String(localized: "pdf.share.accessibility")
        button.accessibilityIdentifier = "pdf.share.open"
        button.tintColor = UIColor(PusulaTheme.accent)
        button.addTarget(context.coordinator, action: #selector(Coordinator.share(_:)), for: .touchUpInside)
        context.coordinator.button = button
        return button
    }

    func updateUIView(_ button: UIButton, context: Context) {
        context.coordinator.url = url
        context.coordinator.onBusy = { isSharing = $0 }
        context.coordinator.onFailure = onFailure
        button.isEnabled = !isSharing
    }

    static func dismantleUIView(_ button: UIButton, coordinator: Coordinator) { coordinator.invalidate() }

    @MainActor
    final class Coordinator: NSObject, UIPopoverPresentationControllerDelegate {
        var url: URL
        var onBusy: @MainActor (Bool) -> Void
        var onFailure: @MainActor (PDFShareFailure) -> Void
        weak var button: UIButton?
        private var state = PDFSharePresentationState()
        private var activity: UIActivityViewController?
        private var watchdog: Task<Void, Never>?
        private var invalidated = false

        init(url: URL, onBusy: @escaping @MainActor (Bool) -> Void,
             onFailure: @escaping @MainActor (PDFShareFailure) -> Void) {
            self.url = url; self.onBusy = onBusy; self.onFailure = onFailure
            super.init()
        }

        @objc func share(_ source: UIButton) {
            guard !invalidated, let lease = state.begin() else { return }
            source.isEnabled = false
            onBusy(true)
            do { try PDFShareFile.validate(url) }
            catch { finish(lease, failure: (error as? PDFShareFailure) ?? .unavailableFile); return }
#if DEBUG
            if PDFShareFixture.forcePresentationFailure {
                finish(lease, failure: .presentationUnavailable)
                return
            }
#endif
            guard let presenter = presenter(for: source), presenter.presentedViewController == nil,
                  !presenter.isBeingDismissed, !presenter.isBeingPresented else {
                finish(lease, failure: .presentationUnavailable)
                return
            }
            let controller = UIActivityViewController(activityItems: [url], applicationActivities: nil)
            controller.view.accessibilityIdentifier = "pdf.share.activity"
            controller.completionWithItemsHandler = { [weak self] _, _, _, error in
                let failed = error != nil
                Task { @MainActor [weak self] in
                    self?.finish(lease, failure: failed ? .systemFailure : nil)
                }
            }
            if let popover = controller.popoverPresentationController {
                popover.sourceView = source
                popover.sourceRect = source.bounds
                popover.permittedArrowDirections = [.up, .down]
                popover.delegate = self
            }
            activity = controller
            presenter.present(controller, animated: true) { [weak self] in
                guard let self, !self.invalidated else { return }
                self.state.didPresent(lease)
                self.watchdog?.cancel()
            }
            controller.presentationController?.delegate = self
            watchdog = Task { @MainActor [weak self] in
                do { try await Task.sleep(nanoseconds: 4_000_000_000) } catch { return }
                guard let self, !self.invalidated, self.state.isAwaitingPresentation(lease) else { return }
                if let activity = self.activity, activity.presentingViewController != nil,
                   activity.viewIfLoaded?.window != nil {
                    self.state.didPresent(lease)
                } else {
                    self.finish(lease, failure: .presentationUnavailable)
                }
            }
        }

        private func presenter(for source: UIView) -> UIViewController? {
            guard let window = source.window else { return nil }
            var responder: UIResponder? = source
            while let current = responder {
                if var controller = current as? UIViewController {
                    // A modal sheet has no parent relationship to the sheet below
                    // it. Stop at this sheet's root, not UIApplication's root.
                    while let parent = controller.parent, parent.viewIfLoaded?.window === window {
                        controller = parent
                    }
                    return controller.viewIfLoaded?.window === window ? controller : nil
                }
                responder = current.next
            }
            return nil
        }

        func presentationControllerDidDismiss(_ presentationController: UIPresentationController) {
            guard presentationController.presentedViewController === activity, let lease = state.lease else { return }
            finish(lease, failure: nil) // Dismissing/cancelling sharing is not an error.
        }

        func popoverPresentationControllerDidDismissPopover(_ popoverPresentationController: UIPopoverPresentationController) {
            guard popoverPresentationController.presentedViewController === activity, let lease = state.lease else { return }
            finish(lease, failure: nil)
        }

        private func finish(_ lease: UUID, failure: PDFShareFailure?) {
            guard !invalidated, state.beginFinish(lease) else { return }
            watchdog?.cancel()
            watchdog = nil
            let complete: @MainActor () -> Void = { [weak self] in
                guard let self, !self.invalidated, self.state.finish(lease) else { return }
                self.activity = nil
                self.button?.isEnabled = true
                self.onBusy(false)
                if let failure { self.onFailure(failure) }
            }
            // Only dismiss our activity, never the presenting PDF or ticket sheet.
            if let activity, activity.isBeingDismissed, let transition = activity.transitionCoordinator {
                let scheduled = transition.animate(alongsideTransition: nil) { _ in
                    Task { @MainActor in complete() }
                }
                if !scheduled { complete() }
            } else if let activity, activity.presentingViewController != nil {
                activity.dismiss(animated: true) { Task { @MainActor in complete() } }
            } else {
                complete()
            }
        }

        func invalidate() {
            invalidated = true
            watchdog?.cancel()
            watchdog = nil
            activity?.completionWithItemsHandler = nil
            activity?.presentationController?.delegate = nil
            if activity?.presentingViewController != nil { activity?.dismiss(animated: false) }
            activity = nil
        }
    }
}
