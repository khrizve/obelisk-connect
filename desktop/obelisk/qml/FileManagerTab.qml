import QtQuick
import QtQuick.Controls
import QtQuick.Layouts
import Obelisk

// Port of lib/widgets/file_manager_tab.dart. The grid uses QtQuick's
// GridView with fixed cell sizing rather than flutter_staggered_grid_view's
// true masonry layout (variable tile height per content) — QtQuick has no
// built-in masonry positioner, and hand-rolling one was out of scope here.
// Visually this means every tile is the same height instead of hugging a
// two-line filename more tightly than a one-line one.
Column {
    id: root
    spacing: 0

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
                Text { anchors.centerIn: parent; text: "\uD83D\uDCC1"; color: Theme.gold; font.pixelSize: 14 }
            }
            Text {
                anchors.verticalCenter: parent.verticalCenter
                text: "File Manager"
                color: Theme.textPrimary
                font.bold: true
                font.pixelSize: 16
                font.family: Theme.bodyFont
            }
        }

        Row {
            anchors.verticalCenter: parent.verticalCenter
            anchors.right: parent.right
            anchors.rightMargin: 12
            spacing: 4

            ToolButton {
                text: "\u2191"
                ToolTip.visible: hovered
                ToolTip.text: "Up a level"
                onClicked: fileManager.goUp()
                contentItem: Text { text: parent.text; color: Theme.gold; font.pixelSize: 16; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                background: Item {}
            }
            ToolButton {
                text: "\u21BB"
                ToolTip.visible: hovered
                ToolTip.text: "Refresh"
                onClicked: fileManager.refresh()
                contentItem: Text { text: parent.text; color: Theme.gold; font.pixelSize: 16; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
                background: Item {}
            }
        }
    }

    // --- Breadcrumb path bar --------------------------------------------
    Rectangle {
        width: root.width
        height: 32
        color: Qt.rgba(Theme.abyss.r, Theme.abyss.g, Theme.abyss.b, 0.5)

        Row {
            anchors.verticalCenter: parent.verticalCenter
            anchors.left: parent.left
            anchors.right: parent.right
            anchors.margins: 16
            spacing: 4

            Text { text: "\u203A"; color: Theme.textMuted; font.pixelSize: 12; anchors.verticalCenter: parent.verticalCenter }
            Text {
                width: parent.width - 20
                text: fileManager.currentPath.length === 0 ? "/" : fileManager.currentPath
                color: Theme.textMuted
                font.family: "monospace"
                font.pixelSize: 12
                elide: Text.ElideRight
            }
        }
    }

    // --- Status line ------------------------------------------------
    Text {
        visible: fileManager.statusMessage.length > 0
        width: root.width
        leftPadding: 16; rightPadding: 16; topPadding: 6; bottomPadding: 6
        text: fileManager.statusMessage
        color: Theme.gold
        font.pixelSize: 12
        font.family: Theme.bodyFont
        wrapMode: Text.WordWrap
    }

    // --- Grid / loading / empty state ------------------------------------
    Item {
        width: root.width
        height: root.height - 56 - 32 - (fileManager.statusMessage.length > 0 ? statusHeightHint.implicitHeight + 12 : 0)

        Text { id: statusHeightHint; visible: false; text: fileManager.statusMessage; wrapMode: Text.WordWrap; width: root.width - 32 }

        BusyIndicator {
            anchors.centerIn: parent
            running: fileManager.loading
            visible: running
        }

        Column {
            anchors.centerIn: parent
            spacing: 12
            visible: !fileManager.loading && rep.count === 0

            Text { anchors.horizontalCenter: parent.horizontalCenter; text: "\uD83D\uDCC2"; font.pixelSize: 34; color: Theme.textMuted }
            Text { text: "This chamber is empty."; font.italic: true; font.family: Theme.bodyFont; color: Theme.textMuted }
        }

        // True masonry (variable tile height per content, fixed column
        // width), unlike a plain GridView's uniform cells — matches
        // flutter_staggered_grid_view's SliverSimpleGridDelegateWithMax-
        // CrossAxisExtent(130) behavior: a two-line filename gets a
        // taller tile than a one-line one, and tiles pack into whichever
        // column is currently shortest.
        Flickable {
            id: gridScroll
            anchors.fill: parent
            anchors.margins: 16
            visible: !fileManager.loading && rep.count > 0
            clip: true
            contentWidth: width
            contentHeight: masonry.height
            boundsBehavior: Flickable.StopAtBounds
            ScrollBar.vertical: ScrollBar {}

            Item {
                id: masonry
                width: gridScroll.width
                height: 0 // set by relayout() below, once real tile heights are known

                readonly property int tileWidth: 120
                readonly property int spacing: 10

                function relayout() {
                    const columns = Math.max(1, Math.floor((width + spacing) / (tileWidth + spacing)));
                    const colHeights = new Array(columns).fill(0);
                    for (let i = 0; i < rep.count; ++i) {
                        const item = rep.itemAt(i);
                        if (!item) continue;
                        let col = 0;
                        for (let c = 1; c < columns; ++c)
                            if (colHeights[c] < colHeights[col]) col = c;
                        item.x = col * (tileWidth + spacing);
                        item.y = colHeights[col];
                        colHeights[col] += item.height + spacing;
                    }
                    masonry.height = columns > 0 ? colHeights.reduce((a, b) => Math.max(a, b), 0) : 0;
                }

                onWidthChanged: relayoutTimer.restart()

                Timer {
                    id: relayoutTimer
                    interval: 0
                    onTriggered: masonry.relayout()
                }

                Connections {
                    target: fileManager
                    function onCurrentPathChanged() { relayoutTimer.restart() }
                }

                Repeater {
                    id: rep
                    model: fileManager.model

                    delegate: Item {
                        id: tile
                        width: masonry.tileWidth
                        // Height comes from the real wrapped-text content
                        // below (bodyColumn.implicitHeight), not a guess —
                        // that's what makes this genuinely content-sized
                        // rather than an estimate.
                        height: bodyColumn.implicitHeight + 16
                        onHeightChanged: relayoutTimer.restart()

                        property bool hovering: false
                        Component.onCompleted: {
                            fileManager.requestThumbnailIfNeeded(index);
                            relayoutTimer.restart();
                        }

                        Rectangle {
                            anchors.fill: parent
                            radius: 12
                            color: tile.hovering ? Theme.panelAlt : Theme.panel
                            border.width: 1
                            border.color: tile.hovering ? Qt.rgba(Theme.arcane.r, Theme.arcane.g, Theme.arcane.b, 0.5) : "transparent"

                            Behavior on color { ColorAnimation { duration: 150 } }

                            MouseArea {
                                anchors.fill: parent
                                hoverEnabled: true
                                onEntered: tile.hovering = true
                                onExited: tile.hovering = false
                                onClicked: fileManager.openRow(index)
                            }

                            Column {
                                id: bodyColumn
                                anchors.left: parent.left
                                anchors.right: parent.right
                                anchors.top: parent.top
                                anchors.margins: 8
                                spacing: 5

                                Item {
                                    width: parent.width
                                    height: 36

                                    Image {
                                        anchors.centerIn: parent
                                        width: 36; height: 36
                                        visible: model.thumbnailUrl.length > 0
                                        source: model.thumbnailUrl
                                        fillMode: Image.PreserveAspectCrop
                                        cache: false
                                        layer.enabled: true
                                    }
                                    Text {
                                        anchors.centerIn: parent
                                        visible: model.thumbnailUrl.length === 0
                                        text: root.glyphFor(model.name, model.isDir)
                                        color: root.colorFor(model.name, model.isDir)
                                        font.pixelSize: 30
                                    }
                                }

                                Text {
                                    width: parent.width
                                    horizontalAlignment: Text.AlignHCenter
                                    text: model.name
                                    wrapMode: Text.WordWrap
                                    maximumLineCount: 2
                                    elide: Text.ElideRight
                                    font.pixelSize: 12
                                    color: Theme.textPrimary
                                    onLineLaidOut: relayoutTimer.restart()
                                }

                                Text {
                                    width: parent.width
                                    horizontalAlignment: Text.AlignHCenter
                                    visible: !model.isDir && text.length > 0
                                    text: root.formatSize(model.size)
                                    font.pixelSize: 10
                                    color: Theme.textMuted
                                }
                            }

                            // Floating download badge, top-right, fades in on hover —
                            // matches _GridTile's Positioned+AnimatedOpacity badge.
                            Rectangle {
                                visible: !model.isDir
                                opacity: tile.hovering ? 1 : 0
                                Behavior on opacity { NumberAnimation { duration: 150 } }
                                anchors.top: parent.top
                                anchors.right: parent.right
                                anchors.margins: 2
                                width: 24; height: 24; radius: 12
                                color: Qt.rgba(Theme.voidColor.r, Theme.voidColor.g, Theme.voidColor.b, 0.85)

                                Text {
                                    anchors.centerIn: parent
                                    text: "\u2913"
                                    color: Theme.goldBright
                                    font.pixelSize: 13
                                }
                                MouseArea {
                                    anchors.fill: parent
                                    onClicked: fileManager.downloadRow(index)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- Preview dialogs --------------------------------------------
    Popup {
        id: imagePreviewPopup
        modal: true
        anchors.centerIn: Overlay.overlay
        width: Math.min(root.width * 0.8, 900)
        height: Math.min(root.height * 0.8, 700)
        property string previewName: ""
        property string previewUrl: ""

        Column {
            anchors.fill: parent
            spacing: 0

            Row {
                width: parent.width
                height: 40
                Text {
                    width: parent.width - 40
                    anchors.verticalCenter: parent.verticalCenter
                    leftPadding: 12
                    text: imagePreviewPopup.previewName
                    color: Theme.goldBright
                    font.bold: true
                    elide: Text.ElideRight
                }
                ToolButton {
                    text: "\u2715"
                    onClicked: imagePreviewPopup.close()
                }
            }
            Image {
                width: parent.width
                height: parent.height - 40
                source: imagePreviewPopup.previewUrl
                fillMode: Image.PreserveAspectFit
                cache: false
                asynchronous: true
            }
        }
    }

    Popup {
        id: textPreviewPopup
        modal: true
        anchors.centerIn: Overlay.overlay
        width: Math.min(root.width * 0.8, 900)
        height: Math.min(root.height * 0.8, 700)
        property string previewName: ""
        property string previewText: ""

        Column {
            anchors.fill: parent
            spacing: 0

            Row {
                width: parent.width
                height: 40
                Text {
                    width: parent.width - 40
                    anchors.verticalCenter: parent.verticalCenter
                    leftPadding: 12
                    text: textPreviewPopup.previewName
                    color: Theme.goldBright
                    font.bold: true
                    elide: Text.ElideRight
                }
                ToolButton {
                    text: "\u2715"
                    onClicked: textPreviewPopup.close()
                }
            }
            ScrollView {
                width: parent.width
                height: parent.height - 40
                TextArea {
                    readOnly: true
                    selectByMouse: true
                    wrapMode: TextArea.NoWrap
                    text: textPreviewPopup.previewText
                    font.family: "monospace"
                    font.pixelSize: 13
                    color: Theme.textPrimary
                }
            }
        }
    }

    Connections {
        target: fileManager
        function onImagePreviewReady(name, fileUrl) {
            imagePreviewPopup.previewName = name;
            imagePreviewPopup.previewUrl = fileUrl;
            imagePreviewPopup.open();
        }
        function onTextPreviewReady(name, text) {
            textPreviewPopup.previewName = name;
            textPreviewPopup.previewText = text;
            textPreviewPopup.open();
        }
    }

    // --- Icon/color classification, mirroring _iconFor in the Dart file --
    function extOf(name) {
        const dot = name.lastIndexOf(".");
        return dot >= 0 ? name.slice(dot + 1).toLowerCase() : "";
    }
    readonly property var imageExts: ["jpg","jpeg","png","webp","gif","bmp","heic","heif"]
    readonly property var videoExts: ["mp4","mkv","avi","mov","webm","3gp"]
    readonly property var audioExts: ["mp3","wav","ogg","m4a","flac","aac"]
    readonly property var archiveExts: ["zip","rar","7z","tar","gz"]
    readonly property var textExts: ["txt","md","json","xml","yaml","yml","csv","log","kt","dart","java","py","js","ts","html","css","c","cpp","h","hpp","gradle","properties","ini","conf","sh","bat","rs","go","rb","php","sql","toml"]
    readonly property var docExts: ["doc","docx"]
    readonly property var sheetExts: ["xls","xlsx","csv"]
    readonly property var slideExts: ["ppt","pptx"]

    function glyphFor(name, isDir) {
        if (isDir) return "\uD83D\uDCC1";
        const ext = root.extOf(name);
        if (root.imageExts.includes(ext)) return "\uD83D\uDDBC";
        if (root.videoExts.includes(ext)) return "\uD83C\uDFAC";
        if (root.audioExts.includes(ext)) return "\uD83C\uDFB5";
        if (root.archiveExts.includes(ext)) return "\uD83D\uDDDC";
        if (ext === "pdf") return "\uD83D\uDCD5";
        if (ext === "apk") return "\uD83E\uDD16";
        if (root.docExts.includes(ext)) return "\uD83D\uDCC4";
        if (root.sheetExts.includes(ext)) return "\uD83D\uDCCA";
        if (root.slideExts.includes(ext)) return "\uD83D\uDCFD";
        if (root.textExts.includes(ext)) return "\uD83D\uDCC3";
        return "\uD83D\uDCC4";
    }
    function colorFor(name, isDir) {
        if (isDir) return Theme.gold;
        const ext = root.extOf(name);
        if (root.imageExts.includes(ext)) return Theme.arcane;
        if (root.videoExts.includes(ext)) return Theme.ember;
        if (root.audioExts.includes(ext)) return Theme.success;
        if (root.archiveExts.includes(ext)) return Theme.gold;
        if (ext === "pdf") return Theme.blood;
        if (ext === "apk") return Theme.success;
        if (root.textExts.includes(ext)) return Theme.textMuted;
        return Theme.textMuted;
    }
    function formatSize(bytes) {
        if (bytes <= 0) return "";
        const units = ["B", "KB", "MB", "GB"];
        let size = bytes;
        let unit = 0;
        while (size >= 1024 && unit < units.length - 1) {
            size /= 1024;
            unit++;
        }
        return size.toFixed(1) + " " + units[unit];
    }

    Connections {
        target: towerServer
        function onStatusChanged() {
            // Chambers are pre-instantiated in main.qml rather than created
            // on first tab-open (unlike the Dart version's per-tab
            // lifecycle), so Component.onCompleted below can fire before
            // any phone is paired. Re-list whenever a phone actually binds.
            if (towerServer.status === TowerServer.Connected)
                fileManager.start();
        }
    }

    Component.onCompleted: fileManager.start()
}
