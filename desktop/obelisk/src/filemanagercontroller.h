#pragma once

#include <QObject>
#include <QSet>
#include <QHash>
#include <QTemporaryFile>

#include "fileentrymodel.h"

class TowerServer;

/// Port of the business logic inside file_manager_tab.dart's State class —
/// path navigation, tracking whether a getFile() reply is for a preview or
/// a download, extension-based preview routing, and thumbnail caching.
/// Kept separate from TowerServer (which stays a thin protocol layer) and
/// from FileManagerTab.qml (which stays presentation-only), the same
/// split used for the desktop notifier.
class FileManagerController : public QObject
{
    Q_OBJECT

    Q_PROPERTY(QString currentPath READ currentPath NOTIFY currentPathChanged)
    Q_PROPERTY(bool loading READ loading NOTIFY loadingChanged)
    Q_PROPERTY(QString statusMessage READ statusMessage NOTIFY statusMessageChanged)
    Q_PROPERTY(FileEntryModel *model READ model CONSTANT)

public:
    explicit FileManagerController(TowerServer *server, QObject *parent = nullptr);

    QString currentPath() const { return m_currentPath; }
    bool loading() const { return m_loading; }
    QString statusMessage() const { return m_statusMessage; }
    FileEntryModel *model() const { return m_model; }

public slots:
    /// Kicks off the initial listing — call once after construction.
    void start();

    void navigate(const QString &path);
    void goUp();
    void refresh();

    /// row is an index into `model`; dispatches to navigate() for
    /// directories or preview() for files, same as _GridTile's onOpen.
    void openRow(int row);
    void downloadRow(int row);

    /// Called from the grid delegate (mirrors _maybeRequestThumbnail) —
    /// safe to call repeatedly, guarded by m_requestedThumbnails.
    void requestThumbnailIfNeeded(int row);

signals:
    void currentPathChanged();
    void loadingChanged();
    void statusMessageChanged();

    /// Opens an image preview dialog in QML with this file:// URL.
    void imagePreviewReady(const QString &name, const QString &fileUrl);
    /// Opens a text preview dialog in QML with this decoded content.
    void textPreviewReady(const QString &name, const QString &text);

private slots:
    void onDirListingReceived(const QString &path, const QVariantList &entries);
    void onFileProgress(const QString &path, qint64 received, qint64 total);
    void onFileDataReceived(const QString &path, const QString &name, qint64 size, const QByteArray &bytes);
    void onThumbnailReceived(const QString &path, const QByteArray &imageBytes);
    void onErrorOccurred(const QString &message);

private:
    QString fullPathFor(const QString &name) const;
    void preview(const QString &fullPath, const QString &name);
    void setStatusMessage(const QString &message);
    void setLoading(bool loading);
    static QString extensionOf(const QString &name);
    static QString formatSize(qint64 bytes);
    void showPreviewFor(const QString &name, const QByteArray &bytes);
    void openWithSystemDefault(const QString &name, const QByteArray &bytes);

    TowerServer *m_server = nullptr;
    FileEntryModel *m_model = nullptr;

    QString m_currentPath;
    bool m_loading = true;
    QString m_statusMessage;

    QString m_pendingPreviewPath;
    QString m_pendingDownloadPath;
    QSet<QString> m_requestedThumbnails;

    // Kept alive for the process lifetime, same as the Dart version writing
    // into the OS temp dir without cleanup — an external viewer/OS default
    // app may still be reading the file after this call returns.
    QList<QTemporaryFile *> m_previewTempFiles;
};
