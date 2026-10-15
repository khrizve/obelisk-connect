import QtQuick
import QtQuick.Controls
import Obelisk

// Port of lib/widgets/screen_tab.dart. Toggles the phone's screen mirror
// on/off and renders incoming JPEG frames from towerServer.frameReceived.
Column {
    id: root
    spacing: 0

    property bool mirroring: false
    property string errorText: ""

    property int frameRevision: 0

    Connections {
        target: towerServer
        function onFrameReceived(jpegBytes, w, h) {
            // FrameImageProvider's backing QImage is already updated in
            // C++ (main.cpp connects this same signal). Here we just bump
            // the query string so the Image element re-fetches from
            // image://frames/current instead of serving its cached copy.
            root.errorText = "";
            root.frameRevision += 1;
        }
        function onErrorOccurred(message) {
            root.errorText = message;
            root.mirroring = false;
            root.frameRevision = 0;
        }
    }

    function toggle() {
        root.mirroring = !root.mirroring;
        root.errorText = "";
        if (root.mirroring) {
            towerServer.startScreen();
        } else {
            towerServer.stopScreen();
            root.frameRevision = 0;
        }
    }

    Component.onDestruction: {
        if (root.mirroring)
            towerServer.stopScreen();
    }

    // --- Header bar ------------------------------------------------
    Rectangle {
        width: root.width
        height: 56
        color: Theme.abyss
        border.width: 0

        Rectangle {
            anchors.bottom: parent.bottom
            width: parent.width
            height: 1
            color: Qt.rgba(Theme.panelBorder.r, Theme.panelBorder.g, Theme.panelBorder.b, 0.4)
        }

        Row {
            anchors.verticalCenter: parent.verticalCenter
            anchors.left: parent.left
            anchors.leftMargin: 16
            spacing: 10

            Rectangle {
                width: 32; height: 32; radius: 8
                color: Theme.panelAlt
                anchors.verticalCenter: parent.verticalCenter
                Text {
                    anchors.centerIn: parent
                    text: "\uD83D\uDCF1" // smartphone glyph, stand-in for Icons.smartphone
                    color: Theme.gold
                    font.pixelSize: 15
                }
            }

            Text {
                anchors.verticalCenter: parent.verticalCenter
                text: "Screen"
                color: Theme.textPrimary
                font.bold: true
                font.pixelSize: 16
                font.family: Theme.bodyFont
            }

            Rectangle {
                visible: root.mirroring && root.frameRevision > 0
                anchors.verticalCenter: parent.verticalCenter
                radius: 10
                color: Qt.rgba(Theme.blood.r, Theme.blood.g, Theme.blood.b, 0.15)
                border.color: Qt.rgba(Theme.blood.r, Theme.blood.g, Theme.blood.b, 0.5)
                border.width: 1
                width: liveRow.implicitWidth + 16
                height: liveRow.implicitHeight + 6

                Row {
                    id: liveRow
                    anchors.centerIn: parent
                    spacing: 4
                    Rectangle { width: 8; height: 8; radius: 4; color: Theme.blood; anchors.verticalCenter: parent.verticalCenter }
                    Text { text: "LIVE"; color: Theme.blood; font.pixelSize: 10; font.bold: true }
                }
            }
        }

        Button {
            anchors.verticalCenter: parent.verticalCenter
            anchors.right: parent.right
            anchors.rightMargin: 16
            text: root.mirroring ? "\u25A0 Stop Mirroring" : "\u25B6 Start Mirroring"
            onClicked: root.toggle()

            background: Rectangle {
                radius: 12
                color: root.mirroring ? Theme.blood : Theme.arcane
            }
            contentItem: Text {
                text: parent.text
                color: "white"
                font.weight: Font.DemiBold
                horizontalAlignment: Text.AlignHCenter
                verticalAlignment: Text.AlignVCenter
            }
        }
    }

    // --- Screen surface ---------------------------------------------
    Rectangle {
        width: root.width
        height: root.height - 56
        color: "black"
        radius: 16
        clip: true
        border.width: 1.5
        border.color: root.mirroring && root.frameRevision > 0
                      ? Qt.rgba(Theme.arcane.r, Theme.arcane.g, Theme.arcane.b, 0.5)
                      : Qt.rgba(Theme.panelBorder.r, Theme.panelBorder.g, Theme.panelBorder.b, 0.4)

        Image {
            id: frameImage
            anchors.fill: parent
            anchors.margins: 16
            fillMode: Image.PreserveAspectFit
            asynchronous: true
            cache: false
            source: root.frameRevision > 0 ? "image://frames/current?r=" + root.frameRevision : ""
            visible: root.mirroring && root.frameRevision > 0
        }

        BusyIndicator {
            anchors.centerIn: parent
            running: root.mirroring && root.frameRevision === 0 && root.errorText.length === 0
            visible: running
        }

        Column {
            anchors.centerIn: parent
            spacing: 14
            width: parent.width - 64
            visible: !root.mirroring || (root.errorText.length > 0 && root.frameRevision === 0)

            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                text: root.errorText.length > 0 ? "\u26A0" : "\uD83D\uDC41"
                font.pixelSize: 34
                color: root.errorText.length > 0 ? Theme.blood : Theme.textMuted
            }

            Text {
                width: parent.width
                horizontalAlignment: Text.AlignHCenter
                wrapMode: Text.WordWrap
                text: root.errorText.length > 0
                      ? root.errorText
                      : "The far-seeing rests. Press \u201cStart Mirroring\u201d to gaze upon the device."
                font.italic: true
                font.family: Theme.bodyFont
                color: root.errorText.length > 0 ? Theme.blood : Theme.textMuted
            }
        }
    }
}
