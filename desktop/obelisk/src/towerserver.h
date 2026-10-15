#pragma once

#include <QObject>
#include <QQmlEngine>
#include <QWebSocketServer>
#include <QWebSocket>
#include <QTimer>
#include <QJsonObject>

/// The PC side is always the WebSocket SERVER. The phone dials in after
/// scanning a rotating QR "sigil" containing {ip, port, token}. Only one
/// phone is meant to be bound at a time, matching the personal-use design.
///
/// This is a straight port of lib/services/tower_server.dart's connection,
/// auth, and screen-mirroring logic. The other message types the Dart
/// version handles (call, notification, dir_listing, file_*, thumbnail_data)
/// are intentionally left as a single TODO switch case below — wire them
/// up chamber by chamber the same way `frame` is done here.
class TowerServer : public QObject
{
    Q_OBJECT
    QML_ELEMENT
    QML_UNCREATABLE("Created once in main.cpp and exposed as the towerServer context property.")
    // Exposed to QML as a context property ("towerServer") set up in
    // main.cpp, rather than QML_SINGLETON — that way main.cpp keeps a
    // live handle to call start() once the engine is ready. QML_ELEMENT
    // is kept only so the Status enum is visible as TowerServer.Connected
    // etc. from QML; QML_UNCREATABLE blocks `TowerServer {}` in QML itself.

    Q_PROPERTY(QString qrPayload READ qrPayload NOTIFY qrPayloadChanged)
    Q_PROPERTY(Status status READ status NOTIFY statusChanged)
    Q_PROPERTY(QString deviceName READ deviceName NOTIFY deviceNameChanged)
    Q_PROPERTY(bool isBound READ isBound NOTIFY statusChanged)

public:
    enum Status { Waiting, Connected, Disconnected };
    Q_ENUM(Status)

    explicit TowerServer(QObject *parent = nullptr);
    ~TowerServer() override;

    QString qrPayload() const { return m_qrPayload; }
    Status status() const { return m_status; }
    QString deviceName() const { return m_deviceName; }
    bool isBound() const { return m_activeSocket != nullptr; }

    static constexpr quint16 kPort = 53211;
    static constexpr int kTokenLifetimeMs = 20000; // matches tokenLifetime in tower_server.dart

public slots:
    /// Opens the WebSocket server and starts the rotating-sigil timer.
    /// Call once, e.g. from main.cpp after the QML engine is up.
    void start();

    /// Screen chamber controls — sends {"type":"start_screen"} / stop_screen.
    void startScreen();
    void stopScreen();

    /// File Manager chamber controls — see tower_server.dart's listDir/
    /// getFile/getThumbnail. Responses come back asynchronously via the
    /// dirListingReceived/fileProgress/fileDataReceived/thumbnailReceived
    /// signals below, matched up by `path`.
    void listDir(const QString &path);
    void getFile(const QString &path);
    void getThumbnail(const QString &path);

    /// Forcibly drops the current phone binding and resumes rotating a
    /// fresh sigil, same as the Dart sever().
    void sever();

signals:
    void qrPayloadChanged();
    void statusChanged();
    void deviceNameChanged();

    /// One JPEG frame of the mirrored screen. width/height are the
    /// reported source-display dimensions (for aspect-ratio bookkeeping;
    /// the JPEG itself is already sized to match).
    void frameReceived(const QByteArray &jpegBytes, int width, int height);

    /// A call state transition: state is "ringing" | "offhook" | "idle".
    /// atEpochMs is stamped on receipt here (QDateTime::currentMSecsSinceEpoch()),
    /// matching CallEvent.fromJson's `at: DateTime.now()` in the Dart model —
    /// the phone doesn't send a timestamp, so this is local receive time.
    void callReceived(const QString &state, const QString &number, const QString &name, qint64 atEpochMs);

    /// postTimeMs is the phone's own posted-at timestamp (unlike callReceived's
    /// atEpochMs), matching NotificationItem.postTime straight from the JSON.
    void notificationReceived(const QString &key, const QString &app, const QString &title,
                               const QString &text, qint64 postTimeMs);
    void notificationRemoved(const QString &key);

    /// entries: list of QVariantMap{name, isDir, size, modified}, straight
    /// from FileEntry.fromJson's fields — kept as QVariantList here since
    /// FileManagerController is what turns this into FileEntryModel rows.
    void dirListingReceived(const QString &path, const QVariantList &entries);

    /// Fires once per file_meta (received=0) and once per file_chunk.
    void fileProgress(const QString &path, qint64 received, qint64 total);

    /// Fires once the matching file_complete arrives, with the fully
    /// reassembled bytes — see the m_transfers reassembly buffer.
    void fileDataReceived(const QString &path, const QString &name, qint64 size, const QByteArray &bytes);

    void thumbnailReceived(const QString &path, const QByteArray &imageBytes);

    void errorOccurred(const QString &message);

private slots:
    void onNewConnection();
    void onTextMessageReceived(const QString &message);
    void onSocketDisconnected();

private:
    void startTokenRotation();
    void rotateToken();
    void handleMessage(QWebSocket *socket, const QJsonObject &json);
    void send(const QJsonObject &message);
    void setStatus(Status status);
    static QString resolveLocalIp();

    QWebSocketServer *m_server = nullptr;
    QWebSocket *m_activeSocket = nullptr;
    QTimer *m_tokenTimer = nullptr;

    QString m_currentToken;
    QString m_localIp;
    QString m_qrPayload;
    QString m_deviceName;
    Status m_status = Waiting;

    /// Tracks one chunked file transfer in progress, keyed by remote path —
    /// direct port of _InFlightTransfer in tower_server.dart.
    struct InFlightTransfer {
        QString name;
        qint64 totalSize = 0;
        QByteArray buffer;
        qint64 received = 0;
    };
    QHash<QString, InFlightTransfer> m_transfers;
};
