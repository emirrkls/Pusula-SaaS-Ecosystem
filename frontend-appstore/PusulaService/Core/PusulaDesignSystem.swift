import SwiftUI

enum PusulaTheme {
    // Official Pusula İklimlendirme palette from the logo and website.
    static let brandCyan = Color(red: 0.00, green: 0.714, blue: 0.922) // #00B6EB
    static let brandNavy = Color(red: 0.110, green: 0.204, blue: 0.380) // #1C3461
    static let accent = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0.00, green: 0.714, blue: 0.922, alpha: 1)
            : UIColor(red: 0.00, green: 0.47, blue: 0.61, alpha: 1)
    })
    static let accentStrong = Color(uiColor: UIColor { traits in
        traits.userInterfaceStyle == .dark
            ? UIColor(red: 0.27, green: 0.82, blue: 0.98, alpha: 1)
            : UIColor(red: 0.110, green: 0.204, blue: 0.380, alpha: 1)
    })
    static let ink = brandNavy
    static let brandGray = Color(red: 0.333, green: 0.333, blue: 0.333) // #555555
    static let amber = Color(red: 0.91, green: 0.56, blue: 0.10)
    static let page = Color(uiColor: .systemGroupedBackground)
    static let surface = Color(uiColor: .secondarySystemGroupedBackground)
    static let raisedSurface = Color(uiColor: .systemBackground)
    static let border = Color(uiColor: .separator).opacity(0.45)

    static let radius: CGFloat = 8
    static let controlHeight: CGFloat = 52
    static let pagePadding: CGFloat = 20
}

struct PusulaBrandMark: View {
    var size: CGFloat = 48

    var body: some View {
        ZStack {
            RoundedRectangle(cornerRadius: PusulaTheme.radius)
                .fill(PusulaTheme.ink)
            Image(systemName: "location.north.fill")
                .font(.system(size: size * 0.42, weight: .semibold))
                .foregroundStyle(PusulaTheme.brandCyan)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

enum PusulaAppearance: String, CaseIterable, Identifiable {
    static let storageKey = "pusula.appearance"

    case system
    case light
    case dark

    var id: String { rawValue }

    var title: String {
        switch self {
        case .system: return "Sistem"
        case .light: return "Açık"
        case .dark: return "Koyu"
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .system: return nil
        case .light: return .light
        case .dark: return .dark
        }
    }
}

struct PusulaAppearancePicker: View {
    @AppStorage(PusulaAppearance.storageKey) private var selection = PusulaAppearance.system.rawValue

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label("Görünüm", systemImage: "circle.lefthalf.filled")
                .font(.subheadline.weight(.semibold))

            Picker("Görünüm", selection: $selection) {
                ForEach(PusulaAppearance.allCases) { mode in
                    Text(mode.title).tag(mode.rawValue)
                }
            }
            .pickerStyle(.segmented)
        }
    }
}

struct PusulaTextField: View {
    let title: String
    let icon: String
    @Binding var text: String
    var contentType: UITextContentType?
    var keyboardType: UIKeyboardType = .default
    var textInputAutocapitalization: TextInputAutocapitalization? = .sentences
    var isSecure = false
    var isInvalid = false
    var submitLabel: SubmitLabel = .next
    var onSubmit: () -> Void = { }
    @FocusState private var isFocused: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(title)
                .font(.caption.weight(.semibold))
                .foregroundStyle(.secondary)
                .contentShape(Rectangle())
                .onTapGesture { isFocused = true }

            HStack(spacing: 12) {
                Image(systemName: icon)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(isInvalid ? Color.red : PusulaTheme.accent)
                    .frame(width: 20)

                Group {
                    if isSecure {
                        SecureField(title, text: $text)
                    } else {
                        TextField(title, text: $text)
                    }
                }
                .textContentType(contentType)
                .keyboardType(keyboardType)
                .textInputAutocapitalization(textInputAutocapitalization)
                .submitLabel(submitLabel)
                .onSubmit(onSubmit)
                .focused($isFocused)
            }
            .padding(.horizontal, 14)
            .frame(height: PusulaTheme.controlHeight)
            .contentShape(Rectangle())
            .simultaneousGesture(
                TapGesture().onEnded { isFocused = true }
            )
            .background(PusulaTheme.raisedSurface)
            .overlay {
                RoundedRectangle(cornerRadius: PusulaTheme.radius)
                    .stroke(isInvalid ? Color.red.opacity(0.8) : PusulaTheme.border, lineWidth: 1)
            }
            .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
        }
    }
}

struct PusulaPrimaryButton: View {
    let title: String
    var icon: String?
    var isLoading = false
    var isDisabled = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 8) {
                if isLoading {
                    ProgressView().tint(.white)
                } else {
                    Text(title).fontWeight(.semibold)
                    if let icon {
                        Image(systemName: icon)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: PusulaTheme.controlHeight)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .foregroundStyle(.white)
        .background(isDisabled ? Color(uiColor: .systemGray3) : PusulaTheme.accent)
        .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
        .disabled(isDisabled || isLoading)
    }
}

struct PusulaInlineMessage: View {
    enum Kind {
        case error, warning, info

        var color: Color {
            switch self {
            case .error: .red
            case .warning: .orange
            case .info: PusulaTheme.accent
            }
        }

        var icon: String {
            switch self {
            case .error: "exclamationmark.circle.fill"
            case .warning: "exclamationmark.triangle.fill"
            case .info: "info.circle.fill"
            }
        }
    }

    let text: String
    var kind: Kind = .error

    var body: some View {
        HStack(alignment: .top, spacing: 10) {
            Image(systemName: kind.icon)
            Text(text)
                .font(.footnote)
                .fixedSize(horizontal: false, vertical: true)
            Spacer(minLength: 0)
        }
        .foregroundStyle(kind.color)
        .padding(12)
        .background(kind.color.opacity(0.09))
        .overlay {
            RoundedRectangle(cornerRadius: PusulaTheme.radius)
                .stroke(kind.color.opacity(0.22), lineWidth: 1)
        }
        .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
    }
}

struct PusulaSectionHeader: View {
    let title: String
    var subtitle: String?
    var icon: String?

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 8) {
            if let icon {
                Image(systemName: icon)
                    .foregroundStyle(PusulaTheme.accent)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text(title).font(.headline)
                if let subtitle {
                    Text(subtitle)
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
            Spacer()
        }
    }
}

struct PusulaCardModifier: ViewModifier {
    var padding: CGFloat = 16

    func body(content: Content) -> some View {
        content
            .padding(padding)
            .background(PusulaTheme.raisedSurface)
            .overlay {
                RoundedRectangle(cornerRadius: PusulaTheme.radius)
                    .stroke(PusulaTheme.border, lineWidth: 1)
            }
            .clipShape(RoundedRectangle(cornerRadius: PusulaTheme.radius))
    }
}

extension View {
    func pusulaCard(padding: CGFloat = 16) -> some View {
        modifier(PusulaCardModifier(padding: padding))
    }
}
