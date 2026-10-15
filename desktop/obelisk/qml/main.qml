import QtQuick
import QtQuick.Controls
import QtQuick.Window
import Obelisk

// Port of lib/screens/home_screen.dart. All four chambers (Pairing Sigil,
// Screen, Calling, Notifications, File Manager) are wired up — see the
// README for known simplifications versus the Flutter original.
ApplicationWindow {
    id: window
    width: 1180
    height: 760
    minimumWidth: 760
    minimumHeight: 480
    visible: true
    title: "Obelisk"
    color: Theme.voidColor

    property bool navOpen: false
    // "pairing" | "screen" | "calling" (future: "notifications" | "files")
    property string activeChamber: "pairing"

    header: Rectangle {
        height: 56
        color: Theme.abyss
        border.width: 0

        Rectangle {
            anchors.bottom: parent.bottom
            width: parent.width
            height: 1
            color: Qt.rgba(Theme.arcane.r, Theme.arcane.g, Theme.arcane.b, 0.25)
        }

        Row {
            anchors.verticalCenter: parent.verticalCenter
            anchors.left: parent.left
            anchors.leftMargin: 16
            spacing: 14

            Text {
                text: "Obelisk"
                color: Theme.goldBright
                font.family: Theme.displayFont
                font.pixelSize: 20
                font.weight: Font.DemiBold
            }

            Rectangle {
                visible: towerServer.deviceName.length > 0
                anchors.verticalCenter: parent.verticalCenter
                radius: 14
                color: Qt.rgba(Theme.success.r, Theme.success.g, Theme.success.b, 0.15)
                border.color: Qt.rgba(Theme.success.r, Theme.success.g, Theme.success.b, 0.4)
                border.width: 1
                width: deviceRow.implicitWidth + 24
                height: deviceRow.implicitHeight + 10

                Row {
                    id: deviceRow
                    anchors.centerIn: parent
                    spacing: 6
                    Text { text: "\uD83D\uDCF1"; font.pixelSize: 12; anchors.verticalCenter: parent.verticalCenter }
                    Text {
                        text: towerServer.deviceName
                        color: Theme.success
                        font.pixelSize: 12
                        font.weight: Font.DemiBold
                        anchors.verticalCenter: parent.verticalCenter
                    }
                }
            }
        }

        ToolButton {
            anchors.verticalCenter: parent.verticalCenter
            anchors.right: parent.right
            anchors.rightMargin: 12
            text: window.navOpen ? "\u2715" : "\u2630"
            onClicked: window.navOpen = !window.navOpen

            contentItem: Text {
                text: parent.text
                color: Theme.goldBright
                font.pixelSize: 18
                horizontalAlignment: Text.AlignHCenter
                verticalAlignment: Text.AlignVCenter
            }
            background: Item {}
        }
    }

    Rectangle {
        anchors.fill: parent
        gradient: Theme.backgroundGradient

        Row {
            anchors.fill: parent

            Item {
                width: parent.width - navPanel.width
                height: parent.height

                QrPanel {
                    anchors.fill: parent
                    visible: window.activeChamber === "pairing"
                }

                Column {
                    anchors.fill: parent
                    visible: window.activeChamber === "screen"
                    spacing: 0

                    ChamberTabBar {
                        width: parent.width
                        glyph: "\uD83D\uDCF1"
                        label: "Screen"
                        onCloseRequested: window.activeChamber = "pairing"
                    }
                    ScreenTab {
                        width: parent.width
                        height: parent.height - 58
                    }
                }

                Column {
                    anchors.fill: parent
                    visible: window.activeChamber === "calling"
                    spacing: 0

                    ChamberTabBar {
                        width: parent.width
                        glyph: "\u260E"
                        label: "Calling"
                        onCloseRequested: window.activeChamber = "pairing"
                    }
                    CallingTab {
                        width: parent.width
                        height: parent.height - 58
                    }
                }

                Column {
                    anchors.fill: parent
                    visible: window.activeChamber === "notifications"
                    spacing: 0

                    ChamberTabBar {
                        width: parent.width
                        glyph: "\uD83D\uDD14"
                        label: "Notifications"
                        onCloseRequested: window.activeChamber = "pairing"
                    }
                    NotificationsTab {
                        width: parent.width
                        height: parent.height - 58
                    }
                }

                Column {
                    anchors.fill: parent
                    visible: window.activeChamber === "files"
                    spacing: 0

                    ChamberTabBar {
                        width: parent.width
                        glyph: "\uD83D\uDCC1"
                        label: "File Manager"
                        onCloseRequested: window.activeChamber = "pairing"
                    }
                    FileManagerTab {
                        width: parent.width
                        height: parent.height - 58
                    }
                }
            }

            // --- Nav panel ("Chambers") ---------------------------------
            Rectangle {
                id: navPanel
                width: window.navOpen ? 250 : 0
                height: parent.height
                color: Theme.abyss
                clip: true

                Behavior on width { NumberAnimation { duration: 220; easing.type: Easing.OutCubic } }

                Rectangle {
                    anchors.left: parent.left
                    width: 1
                    height: parent.height
                    color: Qt.rgba(Theme.panelBorder.r, Theme.panelBorder.g, Theme.panelBorder.b, 0.5)
                }

                Column {
                    width: parent.width
                    anchors.top: parent.top
                    anchors.topMargin: 16
                    spacing: 3

                    Text {
                        anchors.left: parent.left
                        anchors.leftMargin: 20
                        text: "CHAMBERS"
                        color: Theme.textMuted
                        font.letterSpacing: 2.5
                        font.pixelSize: 11
                    }

                    Item { width: 1; height: 8 }

                    NavButton {
                        width: navPanel.width
                        glyph: "\uD83D\uDCF1"
                        label: "Screen"
                        active: window.activeChamber === "screen"
                        onClicked: window.activeChamber = "screen"
                    }
                    NavButton {
                        width: navPanel.width
                        glyph: "\u260E"
                        label: "Calling"
                        active: window.activeChamber === "calling"
                        onClicked: window.activeChamber = "calling"
                    }
                    NavButton {
                        width: navPanel.width
                        glyph: "\uD83D\uDD14"
                        label: "Notifications"
                        active: window.activeChamber === "notifications"
                        onClicked: window.activeChamber = "notifications"
                    }
                    NavButton {
                        width: navPanel.width
                        glyph: "\uD83D\uDCC1"
                        label: "File Manager"
                        active: window.activeChamber === "files"
                        onClicked: window.activeChamber = "files"
                    }

                    Item { width: 1; height: 16 }
                    Rectangle {
                        anchors.left: parent.left
                        anchors.leftMargin: 20
                        anchors.right: parent.right
                        anchors.rightMargin: 20
                        height: 1
                        color: Qt.rgba(Theme.panelBorder.r, Theme.panelBorder.g, Theme.panelBorder.b, 0.5)
                    }
                    Item { width: 1; height: 16 }

                    NavButton {
                        width: navPanel.width
                        glyph: "\u25A3"
                        label: "Pairing Sigil"
                        active: window.activeChamber === "pairing"
                        onClicked: window.activeChamber = "pairing"
                    }
                }
            }
        }
    }

    // Shared tab-strip header used above each open chamber (Screen,
    // Calling, ...), matching home_screen.dart's single-tab bar styling.
    component ChamberTabBar: Rectangle {
        id: tabBar
        property string glyph: ""
        property string label: ""
        signal closeRequested()

        height: 58
        color: Theme.abyss

        Rectangle {
            anchors.bottom: parent.bottom
            width: parent.width
            height: 1
            color: Qt.rgba(Theme.panelBorder.r, Theme.panelBorder.g, Theme.panelBorder.b, 0.5)
        }

        Row {
            anchors.verticalCenter: parent.verticalCenter
            anchors.left: parent.left
            anchors.leftMargin: 12
            spacing: 8

            Rectangle {
                radius: 10
                color: Theme.arcane
                width: tabRow.implicitWidth + 28
                height: 38

                Row {
                    id: tabRow
                    anchors.centerIn: parent
                    spacing: 8
                    Text { text: tabBar.glyph; font.pixelSize: 14; color: "white"; anchors.verticalCenter: parent.verticalCenter }
                    Text { text: tabBar.label; color: "white"; font.weight: Font.DemiBold; font.pixelSize: 14; anchors.verticalCenter: parent.verticalCenter }
                    Text {
                        text: "\u2715"
                        color: "white"
                        font.pixelSize: 12
                        anchors.verticalCenter: parent.verticalCenter
                        MouseArea {
                            anchors.fill: parent
                            anchors.margins: -6
                            onClicked: tabBar.closeRequested()
                        }
                    }
                }
            }
        }
    }

    component NavButton: Rectangle {
        id: navBtn
        property string glyph: ""
        property string label: ""
        property bool active: false
        property bool enabled: true
        signal clicked()

        height: 54
        color: active ? Theme.panel : "transparent"
        radius: 10

        Row {
            anchors.verticalCenter: parent.verticalCenter
            anchors.left: parent.left
            anchors.leftMargin: 10
            spacing: 12
            opacity: navBtn.enabled ? 1.0 : 0.4

            Rectangle {
                width: 34; height: 34; radius: 9
                color: navBtn.active ? Theme.arcane : Theme.panelAlt
                Text {
                    anchors.centerIn: parent
                    text: navBtn.glyph
                    font.pixelSize: 15
                    color: navBtn.active ? "white" : Theme.gold
                }
            }

            Text {
                text: navBtn.label
                color: navBtn.active ? Theme.goldBright : Theme.textPrimary
                font.weight: navBtn.active ? Font.DemiBold : Font.Normal
                font.family: Theme.bodyFont
                anchors.verticalCenter: parent.verticalCenter
            }
        }

        MouseArea {
            anchors.fill: parent
            enabled: navBtn.enabled
            onClicked: navBtn.clicked()
        }
    }
}
