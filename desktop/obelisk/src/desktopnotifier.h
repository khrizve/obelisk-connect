#pragma once

#include <QObject>
#include <QSystemTrayIcon>
#include <QPointer>

class QWindow;

/// Port of lib/services/desktop_notifier.dart. Fires a native OS popup for
/// calls/notifications, but only when the Obelisk window isn't already the
/// thing in front of the user — i.e. it's minimized or unfocused — so an
/// already-open Notifications/Calling tab doesn't get double-announced.
///
/// Qt's cross-platform equivalent of local_notifier's native popups is
/// QSystemTrayIcon::showMessage, which lives in QtWidgets — that's the
/// one reason this project links Widgets alongside pure QML/Quick UI.
class DesktopNotifier : public QObject
{
    Q_OBJECT

public:
    explicit DesktopNotifier(QObject *parent = nullptr);

    /// Call once, after the QML engine has created its root window, so
    /// focus/minimize state can be tracked the same way WindowListener
    /// did via window_manager in the Dart version.
    void attachWindow(QWindow *window);

public slots:
    void notify(const QString &title, const QString &body);

private:
    bool shouldPop() const;
    static QIcon buildTrayIcon();

    QSystemTrayIcon *m_tray = nullptr;
    QPointer<QWindow> m_window;
};
