#pragma once

#include <QQuickImageProvider>
#include <QImage>
#include <QMutex>

/// Bridges TowerServer::frameReceived (raw JPEG bytes) to QML's
/// `image://frames/current` URL scheme used by ScreenTab.qml's Image
/// element. Equivalent in spirit to Flutter's Image.memory(_frame.bytes,
/// gaplessPlayback: true) — each new frame replaces the previous one
/// in place rather than the Image widget being torn down and rebuilt.
///
/// QQuickImageProvider is itself QObject-derived (via QQmlImageProviderBase),
/// so this inherits it directly rather than adding a second QObject base.
class FrameImageProvider : public QQuickImageProvider
{
    Q_OBJECT

public:
    FrameImageProvider();

    // Called from QML (exposed as the "frameProvider" context property)
    // each time TowerServer::frameReceived fires.
    Q_INVOKABLE void setBytes(const QByteArray &jpegBytes);

    QImage requestImage(const QString &id, QSize *size, const QSize &requestedSize) override;

private:
    QImage m_current;
    QMutex m_mutex;
};
