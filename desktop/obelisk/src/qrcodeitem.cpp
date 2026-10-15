#include "qrcodeitem.h"

#include <QPainter>
#include <qrencode.h>

QrCodeItem::QrCodeItem(QQuickItem *parent)
    : QQuickPaintedItem(parent)
{
    setAntialiasing(false); // QR modules must stay crisp, hard-edged rectangles
}

void QrCodeItem::setText(const QString &text)
{
    if (m_text == text)
        return;
    m_text = text;
    emit textChanged();
    update();
}

void QrCodeItem::setModuleColor(const QColor &color)
{
    if (m_moduleColor == color)
        return;
    m_moduleColor = color;
    emit moduleColorChanged();
    update();
}

void QrCodeItem::setBackgroundColor(const QColor &color)
{
    if (m_backgroundColor == color)
        return;
    m_backgroundColor = color;
    emit backgroundColorChanged();
    update();
}

void QrCodeItem::paint(QPainter *painter)
{
    painter->fillRect(boundingRect(), m_backgroundColor);

    if (m_text.isEmpty())
        return;

    const QByteArray utf8 = m_text.toUtf8();
    QRcode *code = QRcode_encodeString(utf8.constData(), 0, QR_ECLEVEL_M, QR_MODE_8, 1);
    if (!code)
        return;

    const int moduleCount = code->width;
    const qreal side = qMin(width(), height());
    const qreal moduleSize = side / static_cast<qreal>(moduleCount);
    const qreal xOffset = (width() - moduleSize * moduleCount) / 2.0;
    const qreal yOffset = (height() - moduleSize * moduleCount) / 2.0;

    painter->setPen(Qt::NoPen);
    painter->setBrush(m_moduleColor);

    const unsigned char *data = code->data;
    for (int y = 0; y < moduleCount; ++y) {
        for (int x = 0; x < moduleCount; ++x) {
            if (data[y * moduleCount + x] & 0x1) {
                painter->drawRect(QRectF(xOffset + x * moduleSize,
                                          yOffset + y * moduleSize,
                                          moduleSize + 0.5, // slight overlap avoids hairline gaps
                                          moduleSize + 0.5));
            }
        }
    }

    QRcode_free(code);
}
