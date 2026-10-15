#include "frameimageprovider.h"

FrameImageProvider::FrameImageProvider()
    : QQuickImageProvider(QQuickImageProvider::Image)
{
}

void FrameImageProvider::setBytes(const QByteArray &jpegBytes)
{
    QImage decoded;
    if (!decoded.loadFromData(jpegBytes, "JPEG"))
        return;

    QMutexLocker locker(&m_mutex);
    m_current = decoded;
}

QImage FrameImageProvider::requestImage(const QString &id, QSize *size, const QSize &requestedSize)
{
    Q_UNUSED(id);
    Q_UNUSED(requestedSize);

    QMutexLocker locker(&m_mutex);
    if (m_current.isNull()) {
        QImage placeholder(1, 1, QImage::Format_ARGB32);
        placeholder.fill(Qt::transparent);
        if (size)
            *size = placeholder.size();
        return placeholder;
    }
    if (size)
        *size = m_current.size();
    return m_current;
}
