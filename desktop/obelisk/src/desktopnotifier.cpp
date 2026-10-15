#include "desktopnotifier.h"

#include <QWindow>
#include <QPixmap>
#include <QPainter>

DesktopNotifier::DesktopNotifier(QObject *parent)
    : QObject(parent)
{
    if (QSystemTrayIcon::isSystemTrayAvailable()) {
        m_tray = new QSystemTrayIcon(buildTrayIcon(), this);
        m_tray->setToolTip(QStringLiteral("Obelisk"));
        m_tray->setVisible(true);
    }
    // If no tray is available (some minimal Linux setups), notify() below
    // just becomes a no-op — same graceful-degradation spirit as the Dart
    // version silently doing nothing if local_notifier's setup() failed.
}

QIcon DesktopNotifier::buildTrayIcon()
{
    // A small painted glyph rather than a bundled asset, so this keeps
    // working without shipping an .ico/.png alongside the binary.
    QPixmap pixmap(32, 32);
    pixmap.fill(Qt::transparent);
    QPainter painter(&pixmap);
    painter.setRenderHint(QPainter::Antialiasing);
    painter.setPen(Qt::NoPen);
    painter.setBrush(QColor("#D4AF37")); // Theme.gold
    painter.drawEllipse(2, 2, 28, 28);
    painter.setPen(QColor("#0A0912")); // Theme.voidColor
    QFont font = painter.font();
    font.setBold(true);
    font.setPixelSize(16);
    painter.setFont(font);
    painter.drawText(pixmap.rect(), Qt::AlignCenter, QStringLiteral("R"));
    painter.end();
    return QIcon(pixmap);
}

void DesktopNotifier::attachWindow(QWindow *window)
{
    m_window = window;
    // No signals to wire up beyond the QPointer itself — shouldPop() reads
    // m_window->isActive()/windowState() live, rather than caching a
    // separate focused/minimized bool pair the way WindowListener did.
}

bool DesktopNotifier::shouldPop() const
{
    if (!m_window)
        return true; // no window reference yet — err toward showing
    return !m_window->isActive() || m_window->windowState() == Qt::WindowMinimized;
}

void DesktopNotifier::notify(const QString &title, const QString &body)
{
    if (!shouldPop())
        return;
    if (m_tray && m_tray->isVisible())
        m_tray->showMessage(title, body, QSystemTrayIcon::Information, 5000);
}
