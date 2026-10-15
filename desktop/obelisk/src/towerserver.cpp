#include "towerserver.h"

#include <QJsonDocument>
#include <QJsonObject>
#include <QJsonArray>
#include <QNetworkInterface>
#include <QUuid>
#include <QDateTime>
#include <QDebug>

TowerServer::TowerServer(QObject *parent)
    : QObject(parent)
    , m_server(new QWebSocketServer(QStringLiteral("Obelisk"),
                                     QWebSocketServer::NonSecureMode, this))
    , m_tokenTimer(new QTimer(this))
{
    connect(m_server, &QWebSocketServer::newConnection, this, &TowerServer::onNewConnection);

    m_tokenTimer->setInterval(kTokenLifetimeMs);
    connect(m_tokenTimer, &QTimer::timeout, this, [this] {
        if (!isBound())
            rotateToken();
    });
}

TowerServer::~TowerServer()
{
    if (m_server->isListening())
        m_server->close();
}

void TowerServer::start()
{
    m_localIp = resolveLocalIp();

    if (!m_server->listen(QHostAddress::AnyIPv4, kPort)) {
        emit errorOccurred(QStringLiteral("Could not open the obelisk's gate on port %1: %2")
                                .arg(kPort)
                                .arg(m_server->errorString()));
        return;
    }

    setStatus(Waiting);
    startTokenRotation();
}

QString TowerServer::resolveLocalIp()
{
    const auto interfaces = QNetworkInterface::allAddresses();
    for (const QHostAddress &addr : interfaces) {
        if (addr.protocol() == QAbstractSocket::IPv4Protocol && !addr.isLoopback())
            return addr.toString();
    }
    return QStringLiteral("127.0.0.1");
}

void TowerServer::startTokenRotation()
{
    rotateToken();
    m_tokenTimer->start();
}

void TowerServer::rotateToken()
{
    m_currentToken = QUuid::createUuid().toString(QUuid::WithoutBraces);

    const QJsonObject payload{
        {"v", 1},
        {"ip", m_localIp},
        {"port", static_cast<int>(kPort)},
        {"token", m_currentToken},
        {"name", QStringLiteral("Obelisk")},
    };

    m_qrPayload = QString::fromUtf8(QJsonDocument(payload).toJson(QJsonDocument::Compact));
    emit qrPayloadChanged();
}

void TowerServer::onNewConnection()
{
    QWebSocket *socket = m_server->nextPendingConnection();
    if (!socket)
        return;

    connect(socket, &QWebSocket::textMessageReceived, this, &TowerServer::onTextMessageReceived);
    connect(socket, &QWebSocket::disconnected, this, &TowerServer::onSocketDisconnected);
}

void TowerServer::onTextMessageReceived(const QString &message)
{
    auto *socket = qobject_cast<QWebSocket *>(sender());
    if (!socket)
        return;

    const QJsonDocument doc = QJsonDocument::fromJson(message.toUtf8());
    if (!doc.isObject())
        return;

    handleMessage(socket, doc.object());
}

void TowerServer::handleMessage(QWebSocket *socket, const QJsonObject &json)
{
    const QString type = json.value("type").toString();

    if (type == QLatin1String("auth")) {
        const QString token = json.value("token").toString();
        const QString device = json.value("device").toString(QStringLiteral("Unknown device"));

        if (token == m_currentToken && m_activeSocket == nullptr) {
            m_activeSocket = socket;
            m_tokenTimer->stop();

            socket->sendTextMessage(QString::fromUtf8(
                QJsonDocument(QJsonObject{{"type", "auth_ok"}}).toJson(QJsonDocument::Compact)));

            setStatus(Connected);
            m_deviceName = device;
            emit deviceNameChanged();
        } else {
            socket->sendTextMessage(QString::fromUtf8(QJsonDocument(QJsonObject{
                {"type", "auth_fail"},
                {"reason", "The sigil has faded. Reopen the obelisk to conjure a new one."},
            }).toJson(QJsonDocument::Compact)));
        }
        return;
    }

    if (socket != m_activeSocket)
        return; // ignore unauthenticated traffic, same as the Dart guard

    if (type == QLatin1String("frame")) {
        const QByteArray bytes = QByteArray::fromBase64(json.value("data").toString().toUtf8());
        emit frameReceived(bytes, json.value("w").toInt(0), json.value("h").toInt(0));
    } else if (type == QLatin1String("call")) {
        emit callReceived(json.value("state").toString(QStringLiteral("idle")),
                           json.value("number").toString(),
                           json.value("name").toString(),
                           QDateTime::currentMSecsSinceEpoch());
    } else if (type == QLatin1String("notification")) {
        emit notificationReceived(json.value("key").toString(),
                                   json.value("app").toString(QStringLiteral("Unknown")),
                                   json.value("title").toString(),
                                   json.value("text").toString(),
                                   json.value("postTime").toVariant().toLongLong());
    } else if (type == QLatin1String("notification_removed")) {
        emit notificationRemoved(json.value("key").toString());
    } else if (type == QLatin1String("dir_listing")) {
        QVariantList entries;
        const QJsonArray arr = json.value("entries").toArray();
        for (const QJsonValue &v : arr) {
            const QJsonObject e = v.toObject();
            QVariantMap m;
            m["name"] = e.value("name").toString();
            m["isDir"] = e.value("isDir").toBool(false);
            m["size"] = e.value("size").toVariant().toLongLong();
            m["modified"] = e.value("modified").toVariant().toLongLong();
            entries.append(m);
        }
        emit dirListingReceived(json.value("path").toString(QStringLiteral("/")), entries);
    } else if (type == QLatin1String("file_meta")) {
        const QString path = json.value("path").toString();
        const qint64 size = json.value("size").toVariant().toLongLong();
        InFlightTransfer transfer;
        transfer.name = json.value("name").toString();
        transfer.totalSize = size;
        m_transfers.insert(path, transfer);
        emit fileProgress(path, 0, size);
    } else if (type == QLatin1String("file_chunk")) {
        const QString path = json.value("path").toString();
        auto it = m_transfers.find(path);
        if (it == m_transfers.end())
            return; // meta never arrived; drop stray chunk, same as the Dart guard
        const QByteArray bytes = QByteArray::fromBase64(json.value("data").toString().toUtf8());
        it->buffer.append(bytes);
        it->received += bytes.size();
        emit fileProgress(path, it->received, it->totalSize);
    } else if (type == QLatin1String("file_complete")) {
        const QString path = json.value("path").toString();
        auto it = m_transfers.find(path);
        if (it == m_transfers.end())
            return;
        emit fileDataReceived(path, it->name, it->totalSize, it->buffer);
        m_transfers.erase(it);
    } else if (type == QLatin1String("thumbnail_data")) {
        emit thumbnailReceived(json.value("path").toString(),
                                QByteArray::fromBase64(json.value("data").toString().toUtf8()));
    } else if (type == QLatin1String("error")) {
        emit errorOccurred(json.value("message").toString(QStringLiteral("Unknown error")));
    }
}

void TowerServer::onSocketDisconnected()
{
    auto *socket = qobject_cast<QWebSocket *>(sender());
    if (!socket)
        return;

    if (socket == m_activeSocket) {
        m_activeSocket = nullptr;
        setStatus(Disconnected);
        startTokenRotation();
    }

    socket->deleteLater();
}

void TowerServer::send(const QJsonObject &message)
{
    if (m_activeSocket) {
        m_activeSocket->sendTextMessage(
            QString::fromUtf8(QJsonDocument(message).toJson(QJsonDocument::Compact)));
    }
}

void TowerServer::startScreen() { send({{"type", "start_screen"}}); }
void TowerServer::stopScreen() { send({{"type", "stop_screen"}}); }

void TowerServer::listDir(const QString &path) { send({{"type", "list_dir"}, {"path", path}}); }
void TowerServer::getFile(const QString &path) { send({{"type", "get_file"}, {"path", path}}); }
void TowerServer::getThumbnail(const QString &path) { send({{"type", "get_thumbnail"}, {"path", path}}); }

void TowerServer::sever()
{
    if (m_activeSocket) {
        m_activeSocket->close();
        m_activeSocket = nullptr;
    }
    setStatus(Disconnected);
    startTokenRotation();
}

void TowerServer::setStatus(Status status)
{
    if (m_status == status)
        return;
    m_status = status;
    emit statusChanged();
}
