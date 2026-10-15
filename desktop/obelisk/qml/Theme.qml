pragma Singleton
import QtQuick

// Port of lib/theme/app_theme.dart's RuneColors + RuneDecor. Kept as a
// singleton so every chamber (screen, calling, notifications, files)
// shares the same dark-fantasy palette, matching the Android companion app.
QtObject {
    readonly property color voidColor: "#0A0912"
    readonly property color abyss: "#14121F"
    readonly property color panel: "#1C1930"
    readonly property color panelAlt: "#25203E"
    readonly property color panelBorder: "#3A2F5C"
    readonly property color gold: "#D4AF37"
    readonly property color goldBright: "#F2D06B"
    readonly property color arcane: "#7B5CFA"
    readonly property color arcaneBright: "#9B82FF"
    readonly property color blood: "#B2273A"
    readonly property color ember: "#E2703A"
    readonly property color textPrimary: "#EDE6F5"
    readonly property color textMuted: "#9A8FB5"
    readonly property color success: "#4FA678"

    readonly property string displayFont: "Cinzel"
    readonly property string bodyFont: "Manrope"

    readonly property Gradient backgroundGradient: Gradient {
        orientation: Gradient.Vertical
        GradientStop { position: 0.0; color: "#1A1530" }
        GradientStop { position: 1.0; color: Theme.voidColor }
    }

    function withAlpha(base, alpha) {
        return Qt.rgba(base.r, base.g, base.b, alpha);
    }
}
