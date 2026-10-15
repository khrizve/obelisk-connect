#include <QGuiApplication>
#include <QApplication>
#include <QQmlApplicationEngine>
#include <QQmlContext>
#include <QFontDatabase>
#include <QDirIterator>
#include <QIcon>
#include <QQuickWindow>

#include "towerserver.h"
#include "frameimageprovider.h"
#include "desktopnotifier.h"
#include "filemanagercontroller.h"

int main(int argc, char *argv[])
{
    // QSystemTrayIcon (used by DesktopNotifier) lives in QtWidgets and
    // needs a QApplication, not just QGuiApplication — that's the only
    // reason this is QApplication despite the UI being pure QML/Quick.
    QApplication app(argc, argv);
    app.setApplicationName(QStringLiteral("Obelisk"));
    app.setOrganizationName(QStringLiteral("Obelisk"));
    app.setQuitOnLastWindowClosed(true);

    // Bundle the same font pairing app_theme.dart used (Cinzel for display
    // text, Manrope for body) rather than depending on google_fonts at
    // runtime. Loaded from whatever .ttf files CMake found under
    // assets/fonts/ (see the comment there) — if none were bundled, this
    // is a no-op and QML's Theme.qml font.family falls back to the system
    // default instead of failing.
    QDirIterator fontIt(QStringLiteral(":/Obelisk/assets/fonts"), {"*.ttf"}, QDir::Files);
    while (fontIt.hasNext())
        QFontDatabase::addApplicationFont(fontIt.next());

    TowerServer towerServer;
    FileManagerController fileManager(&towerServer);

    QQmlApplicationEngine engine;

    // The provider is owned by the engine after addImageProvider(), so we
    // keep a raw pointer only to wire the signal below.
    auto *frameProvider = new FrameImageProvider();
    engine.addImageProvider(QStringLiteral("frames"), frameProvider);
    QObject::connect(&towerServer, &TowerServer::frameReceived, frameProvider,
                      [frameProvider](const QByteArray &jpegBytes, int, int) {
                          frameProvider->setBytes(jpegBytes);
                      });

    engine.rootContext()->setContextProperty(QStringLiteral("towerServer"), &towerServer);
    engine.rootContext()->setContextProperty(QStringLiteral("fileManager"), &fileManager);

    // loadFromModule() (Qt 6.5+) would be the tidier call here; loading the
    // qrc path directly keeps this buildable against the Qt 6.4 baseline
    // most distro packages still ship. Confirmed against the generated
    // qmlcache loader (resourcePathToCachedUnit) rather than assumed.
    engine.load(QUrl(QStringLiteral("qrc:/Obelisk/qml/main.qml")));

    if (engine.rootObjects().isEmpty())
        return -1;

    // --- Desktop popups (port of desktop_notifier.dart) -------------------
    DesktopNotifier desktopNotifier;
    if (auto *rootWindow = qobject_cast<QQuickWindow *>(engine.rootObjects().first()))
        desktopNotifier.attachWindow(rootWindow);

    QObject::connect(&towerServer, &TowerServer::callReceived, &desktopNotifier,
                      [&desktopNotifier](const QString &state, const QString &number,
                                         const QString &name, qint64) {
                          if (state != QLatin1String("ringing"))
                              return;
                          const QString displayName = !name.isEmpty() ? name
                                                       : (number.isEmpty() ? QStringLiteral("Unknown number") : number);
                          desktopNotifier.notify(QStringLiteral("Incoming call"), displayName);
                      });

    QObject::connect(&towerServer, &TowerServer::notificationReceived, &desktopNotifier,
                      [&desktopNotifier](const QString &, const QString &app, const QString &title,
                                         const QString &text, qint64) {
                          desktopNotifier.notify(app, !title.isEmpty() ? title : text);
                      });

    // Open the WebSocket server and start rotating the pairing sigil once
    // the UI exists, so QrPanel.qml is already listening for qrPayloadChanged.
    towerServer.start();

    return app.exec();
}
