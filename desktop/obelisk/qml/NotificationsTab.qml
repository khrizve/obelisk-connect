import QtQuick
import QtQuick.Controls
import Obelisk

// Port of lib/widgets/notifications_tab.dart. A live feed of relayed
// phone notifications, newest first, removable by key (a removal comes
// through when the phone's own notification is dismissed/cleared).
Column {
    id: root
    spacing: 0

    ListModel { id: itemsModel }

    Connections {
        target: towerServer
        function onNotificationReceived(key, app, title, text, postTimeMs) {
            itemsModel.insert(0, { key: key, app: app, title: title, text: text, postTimeMs: postTimeMs });
        }
        function onNotificationRemoved(key) {
            for (let i = 0; i < itemsModel.count; ++i) {
                if (itemsModel.get(i).key === key) {
                    itemsModel.remove(i);
                    break;
                }
            }
        }
    }

    // --- Header bar, with a live count badge -----------------------------
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
                Text { anchors.centerIn: parent; text: "\uD83D\uDD14"; color: Theme.gold; font.pixelSize: 14 }
            }

            Text {
                anchors.verticalCenter: parent.verticalCenter
                text: "Notifications"
                color: Theme.textPrimary
                font.bold: true
                font.pixelSize: 16
                font.family: Theme.bodyFont
            }

            Rectangle {
                visible: itemsModel.count > 0
                anchors.verticalCenter: parent.verticalCenter
                radius: 10
                color: Qt.rgba(Theme.arcane.r, Theme.arcane.g, Theme.arcane.b, 0.2)
                width: countText.implicitWidth + 20
                height: countText.implicitHeight + 6
                Text {
                    id: countText
                    anchors.centerIn: parent
                    text: itemsModel.count
                    color: Theme.arcaneBright
                    font.pixelSize: 12
                }
            }
        }
    }

    // --- List / empty state ------------------------------------------
    Item {
        width: root.width
        height: root.height - 56

        Column {
            anchors.centerIn: parent
            spacing: 12
            visible: itemsModel.count === 0

            Text {
                anchors.horizontalCenter: parent.horizontalCenter
                text: "\uD83D\uDD15"
                font.pixelSize: 34
                color: Theme.textMuted
            }
            Text {
                text: "The panel of whispers is silent."
                font.italic: true
                font.family: Theme.bodyFont
                color: Theme.textMuted
            }
        }

        ListView {
            anchors.fill: parent
            anchors.margins: 12
            visible: itemsModel.count > 0
            model: itemsModel
            spacing: 8
            clip: true

            delegate: Rectangle {
                width: ListView.view.width
                height: bodyColumn.implicitHeight + 24
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
                        anchors.top: parent.top
                        color: Qt.rgba(Theme.arcane.r, Theme.arcane.g, Theme.arcane.b, 0.2)
                        Text {
                            anchors.centerIn: parent
                            text: model.app.length > 0 ? model.app.charAt(0).toUpperCase() : "?"
                            color: Theme.arcaneBright
                            font.bold: true
                        }
                    }

                    Column {
                        id: bodyColumn
                        width: parent.width - 38 - 90 - 24
                        spacing: 3

                        Text {
                            width: parent.width
                            text: model.app
                            color: Theme.gold
                            font.bold: true
                            font.pixelSize: 12
                            elide: Text.ElideRight
                        }
                        Text {
                            visible: model.title.length > 0
                            width: parent.width
                            text: model.title
                            color: Theme.textPrimary
                            font.weight: Font.DemiBold
                            font.family: Theme.bodyFont
                            elide: Text.ElideRight
                        }
                        Text {
                            width: parent.width
                            text: model.text
                            color: Theme.textPrimary
                            font.family: Theme.bodyFont
                            wrapMode: Text.WordWrap
                            maximumLineCount: 2
                            elide: Text.ElideRight
                        }
                    }

                    Text {
                        anchors.top: parent.top
                        width: 90
                        horizontalAlignment: Text.AlignRight
                        text: model.postTimeMs > 0 ? Qt.formatTime(new Date(model.postTimeMs), "hh:mm") : ""
                        color: Theme.textMuted
                        font.pixelSize: 12
                        font.family: Theme.bodyFont
                    }
                }
            }
        }
    }
}
