import QtQuick
import QtQuick.Controls
import Obelisk

// Port of lib/widgets/qr_panel.dart. Shows the rotating pairing sigil,
// the current link status, and a rotating line of fantasy flavor text.
Rectangle {
    id: root
    gradient: Theme.backgroundGradient

    readonly property var fantasyLines: [
        "\u201cSpeak the sigil aloud to your phone, and the obelisk shall answer.\u201d",
        "\u201cTwo vessels, one will. Scan to bind them.\u201d",
        "\u201cThe scroll awaits a sigil to bind it to the far obelisk.\u201d",
        "\u201cNo sigil lasts forever \u2014 a new one blooms should this one fade.\u201d",
        "\u201cThrough ether and copper, the far-seeing begins.\u201d"
    ]
    property string currentLine: fantasyLines[Math.floor(Math.random() * fantasyLines.length)]

    Connections {
        target: towerServer
        function onQrPayloadChanged() {
            root.currentLine = root.fantasyLines[Math.floor(Math.random() * root.fantasyLines.length)];
        }
    }

    // Slow glow pulse behind the QR card, matching the AnimationController
    // (3s, reverse: true) driving glow/spread in the Flutter version.
    SequentialAnimation on glowPhase {
        loops: Animation.Infinite
        NumberAnimation { from: 0.0; to: 1.0; duration: 3000; easing.type: Easing.InOutSine }
        NumberAnimation { from: 1.0; to: 0.0; duration: 3000; easing.type: Easing.InOutSine }
    }
    property real glowPhase: 0.0

    Column {
        anchors.centerIn: parent
        spacing: 0
        width: Math.min(root.width - 80, 480)

        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: "Obelisk"
            font.family: Theme.displayFont
            font.pixelSize: 46
            font.weight: Font.DemiBold
            font.letterSpacing: 1.2
            color: Theme.goldBright
        }

        Item { width: 1; height: 6 }

        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            text: "the obelisk awaits a bearer of the sigil"
            font.family: Theme.bodyFont
            font.italic: true
            font.pixelSize: 13
            color: Theme.textMuted
        }

        Item { width: 1; height: 32 }

        Item {
            anchors.horizontalCenter: parent.horizontalCenter
            width: 284
            height: 284

            // Soft pulsing glow behind the card, built from a few
            // concentric semi-transparent rounded rects rather than
            // QtQuick.Effects' MultiEffect (needs Qt 6.5+) — this keeps
            // the port buildable against the Qt 6.4 baseline most distro
            // packages still ship, at the cost of a softer-edged blur.
            Repeater {
                model: 3
                Rectangle {
                    readonly property real growth: (index + 1) * (6 + root.glowPhase * 8)
                    anchors.centerIn: parent
                    width: 284 + growth * 2
                    height: 284 + growth * 2
                    radius: 20 + growth
                    color: "transparent"
                    border.width: growth
                    border.color: Qt.rgba(Theme.arcane.r, Theme.arcane.g, Theme.arcane.b,
                                           (0.14 - index * 0.04) * (0.6 + root.glowPhase * 0.6))
                }
            }

            Rectangle {
                id: qrCard
                anchors.fill: parent
                radius: 20
                color: "white"
                border.color: Qt.rgba(Theme.arcaneBright.r, Theme.arcaneBright.g, Theme.arcaneBright.b, 0.6)
                border.width: 1.5
            }

            QrCodeItem {
                anchors.centerIn: parent
                width: 240
                height: 240
                text: towerServer.qrPayload
                moduleColor: Theme.voidColor
                backgroundColor: "white"
                visible: towerServer.qrPayload.length > 0
            }

            BusyIndicator {
                anchors.centerIn: parent
                running: towerServer.qrPayload.length === 0
                visible: running
            }
        }

        Item { width: 1; height: 28 }

        // Status chip
        Rectangle {
            id: statusChip
            anchors.horizontalCenter: parent.horizontalCenter
            radius: 24
            height: statusRow.implicitHeight + 20
            width: statusRow.implicitWidth + 36

            readonly property color chipColor: {
                switch (towerServer.status) {
                case TowerServer.Connected: return Theme.success;
                case TowerServer.Disconnected: return Theme.blood;
                default: return Theme.gold;
                }
            }
            readonly property string chipLabel: {
                switch (towerServer.status) {
                case TowerServer.Connected: return "Bound to your phone";
                case TowerServer.Disconnected: return "The link was severed";
                default: return "Awaiting a bearer of the sigil\u2026";
                }
            }

            color: Qt.rgba(chipColor.r, chipColor.g, chipColor.b, 0.13)
            border.color: Qt.rgba(chipColor.r, chipColor.g, chipColor.b, 0.55)
            border.width: 1

            Row {
                id: statusRow
                anchors.centerIn: parent
                spacing: 8

                Rectangle {
                    width: 8; height: 8; radius: 4
                    anchors.verticalCenter: parent.verticalCenter
                    color: statusChip.chipColor
                }

                Text {
                    text: statusChip.chipLabel
                    color: statusChip.chipColor
                    font.family: Theme.bodyFont
                    font.pixelSize: 13
                    font.weight: Font.DemiBold
                }
            }
        }

        Item { width: 1; height: 20 }

        Text {
            anchors.horizontalCenter: parent.horizontalCenter
            width: parent.width - 120
            horizontalAlignment: Text.AlignHCenter
            wrapMode: Text.WordWrap
            text: root.currentLine
            font.family: Theme.bodyFont
            font.italic: true
            font.pixelSize: 15
            color: Theme.textMuted
            // qr_panel.dart crossfades this line via AnimatedSwitcher on
            // each rotation; skipped here for a plain instant swap rather
            // than risk a Behavior animating the wrong property type.
        }
    }
}
