#pragma once

#include <QQuickPaintedItem>
#include <QQmlEngine>
#include <QColor>

/// Renders a QR code for `text` directly with QPainter, using libqrencode
/// for the module matrix. Equivalent to qr_flutter's QrImageView in
/// qr_panel.dart, including the square eye/module styling used there.
class QrCodeItem : public QQuickPaintedItem
{
    Q_OBJECT
    QML_ELEMENT

    Q_PROPERTY(QString text READ text WRITE setText NOTIFY textChanged)
    Q_PROPERTY(QColor moduleColor READ moduleColor WRITE setModuleColor NOTIFY moduleColorChanged)
    Q_PROPERTY(QColor backgroundColor READ backgroundColor WRITE setBackgroundColor NOTIFY backgroundColorChanged)

public:
    explicit QrCodeItem(QQuickItem *parent = nullptr);

    QString text() const { return m_text; }
    void setText(const QString &text);

    QColor moduleColor() const { return m_moduleColor; }
    void setModuleColor(const QColor &color);

    QColor backgroundColor() const { return m_backgroundColor; }
    void setBackgroundColor(const QColor &color);

    void paint(QPainter *painter) override;

signals:
    void textChanged();
    void moduleColorChanged();
    void backgroundColorChanged();

private:
    QString m_text;
    QColor m_moduleColor{Qt::black};
    QColor m_backgroundColor{Qt::white};
};
