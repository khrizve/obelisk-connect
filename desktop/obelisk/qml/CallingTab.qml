import QtQuick
import QtQuick.Controls
import Obelisk

// Port of lib/widgets/calling_tab.dart. Shows a pulsing banner for an
// active incoming call and a running history list, each call's card
// updating in place (ringing -> offhook -> idle) rather than stacking
// duplicates, same as the Dart _history logic keyed by number.
Column {
    id: root
    spacing: 0

    property var current: null // {state, number, name, displayName, atMs} or null
    ListModel { id: historyModel }

    Connections {
        target: towerServer
        function onCallReceived(state, number, name, atEpochMs) {
            const displayName = name.length > 0 ? name : (number.length === 0 ? "Unknown number" : number);
            const entry = { state: state, number: number, name: name, displayName: displayName, atMs: atEpochMs };

            root.current = state === "idle" ? null : entry;

            // Group by number when we have one; unknown-number calls share
            // a single bucket, mirroring the Dart key fallback — this is
            // what makes a call's card update through its states in place
            // instead of stacking a new one per state change.
            const key = number.length > 0 ? number : "unknown";
            for (let i = 0; i < historyModel.count; ++i) {
                const existingKey = historyModel.get(i).number.length > 0 ? historyModel.get(i).number : "unknown";
                if (existingKey === key) {
                    historyModel.remove(i);
                    break;
                }
            }
            historyModel.insert(0, entry);
            if (historyModel.count > 50)
                historyModel.remove(50, historyModel.count - 50);
        }
    }

    function iconFor(state) {
        if (state === "ringing") return "\u260E"; // phone_in_talk stand-in
        if (state === "offhook") return "\u2706"; // call stand-in
        return "\u2612"; // call_end stand-in
    }
    function colorFor(state) {
        if (state === "ringing") return Theme.blood;
        if (state === "offhook") return Theme.success;
        return Theme.textMuted;
    }
    function labelFor(state) {
        if (state === "ringing") return "Ringing";
        if (state === "offhook") return "In call";
        return "Ended";
    }

    // --- Header bar ---------------------------------------------------
    Rectangle {
        width: root.width
        height: 56
        color: Theme.abyss

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
                Text { anchors.centerIn: parent; text: "\u260E"; color: Theme.gold; font.pixelSize: 14 }
            }

            Text {
                anchors.verticalCenter: parent.verticalCenter
                text: "Calling"
                color: Theme.textPrimary
                font.bold: true
                font.pixelSize: 16
                font.family: Theme.bodyFont
            }
        }
    }

    // --- Ringing banner -------------------------------------------------
    Rectangle {
        id: ringingBanner
        visible: root.current !== null && root.current.state === "ringing"
        width: root.width - 32
        anchors.horizontalCenter: parent.horizontalCenter
        height: visible ? bannerRow.implicitHeight + 32 : 0
        radius: 14
        color: Qt.rgba(Theme.blood.r, Theme.blood.g, Theme.blood.b, 0.15)
        border.width: 1
        border.color: Qt.rgba(Theme.blood.r, Theme.blood.g, Theme.blood.b, 0.5 + pulseValue * 0.3)

        // Mirrors the 900ms repeat-reverse AnimationController driving the
        // border pulse in _ringingBanner.
        property real pulseValue: 0.0
        SequentialAnimation on pulseValue {
            running: ringingBanner.visible
            loops: Animation.Infinite
            NumberAnimation { from: 0.0; to: 1.0; duration: 900; easing.type: Easing.InOutSine }
            NumberAnimation { from: 1.0; to: 0.0; duration: 900; easing.type: Easing.InOutSine }
        }

        Row {
            id: bannerRow
            anchors.centerIn: parent
            spacing: 12
            Text { text: "\u260E"; color: Theme.blood; font.pixelSize: 20; anchors.verticalCenter: parent.verticalCenter }
            Text {
                text: root.current !== null ? "Incoming: " + root.current.displayName : ""
                color: Theme.textPrimary
                font.bold: true
                font.pixelSize: 16
                anchors.verticalCenter: parent.verticalCenter
            }
        }
    }

    Item { width: 1; height: ringingBanner.visible ? 16 : 0 }

    // --- History / empty state ------------------------------------------
    Item {
        width: root.width
        height: root.height - 56 - (ringingBanner.visible ? ringingBanner.height + 16 : 0)

        Column {
            anchors.centerIn: parent
            spacing: 12
            visible: historyModel.count === 0

            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                text: "\u260E\u2298"
                font.pixelSize: 34
                color: Theme.textMuted
            }
            Text {
                text: "No calls have echoed through the obelisk yet."
                font.italic: true
                font.family: Theme.bodyFont
                color: Theme.textMuted
            }
        }

        ListView {
            anchors.fill: parent
            anchors.margins: 12
            visible: historyModel.count > 0
            model: historyModel
            spacing: 8
            clip: true

            delegate: Rectangle {
                width: ListView.view.width
                height: 62
                radius: 12
                color: Theme.panel
                border.width: 1
                border.color: Qt.rgba(Theme.panelBorder.r, Theme.panelBorder.g, Theme.panelBorder.b, 0.5)

                Row {
                    anchors.fill: parent
                    anchors.margins: 12
                    spacing: 12

                    Rectangle {
                        width: 38; height: 38; radius: 19
                        anchors.verticalCenter: parent.verticalCenter
                        color: Qt.rgba(root.colorFor(model.state).r, root.colorFor(model.state).g,
                                       root.colorFor(model.state).b, 0.15)
                        Text {
                            anchors.centerIn: parent
                            text: root.iconFor(model.state)
                            color: root.colorFor(model.state)
                            font.pixelSize: 15
                        }
                    }

                    Column {
                        anchors.verticalCenter: parent.verticalCenter
                        width: parent.width - 38 - 90 - 24
                        spacing: 2
                        Text {
                            text: model.displayName
                            color: Theme.textPrimary
                            font.weight: Font.DemiBold
                            font.family: Theme.bodyFont
                            elide: Text.ElideRight
                            width: parent.width
                        }
                        Text {
                            text: (model.name.length > 0 && model.number.length > 0)
                                  ? model.number + " \u00B7 " + root.labelFor(model.state)
                                  : root.labelFor(model.state)
                            color: Theme.textMuted
                            font.pixelSize: 12
                            font.family: Theme.bodyFont
                            elide: Text.ElideRight
                            width: parent.width
                        }
                    }

                    Text {
                        anchors.verticalCenter: parent.verticalCenter
                        width: 90
                        horizontalAlignment: Text.AlignRight
                        text: Qt.formatTime(new Date(model.atMs), "hh:mm:ss")
                        color: Theme.textMuted
                        font.pixelSize: 12
                        font.family: Theme.bodyFont
                    }
                }
            }
        }
    }
}
