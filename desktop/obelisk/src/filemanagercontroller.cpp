#include "filemanagercontroller.h"
#include "towerserver.h"

#include <QFileDialog>
#include <QFile>
#include <QDesktopServices>
#include <QUrl>
#include <QDir>
#include <QStringConverter>

namespace {
const QSet<QString> kTextExtensions = {
    "txt", "md", "json", "xml", "yaml", "yml", "csv", "log", "kt", "dart", "java",
    "py", "js", "ts", "html", "css", "c", "cpp", "h", "hpp", "gradle", "properties",
    "ini", "conf", "sh", "bat", "rs", "go", "rb", "php", "sql", "toml",
};
const QSet<QString> kImageExtensions = {"jpg", "jpeg", "png", "webp", "gif", "bmp", "heic", "heif"};
}

FileManagerController::FileManagerController(TowerServer *server, QObject *parent)
    : QObject(parent)
    , m_server(server)
    , m_model(new FileEntryModel(this))
{
    connect(m_server, &TowerServer::dirListingReceived, this, &FileManagerController::onDirListingReceived);
    connect(m_server, &TowerServer::fileProgress, this, &FileManagerController::onFileProgress);
    connect(m_server, &TowerServer::fileDataReceived, this, &FileManagerController::onFileDataReceived);
    connect(m_server, &TowerServer::thumbnailReceived, this, &FileManagerController::onThumbnailReceived);
    connect(m_server, &TowerServer::errorOccurred, this, &FileManagerController::onErrorOccurred);
}

void FileManagerController::start()
{
    navigate(QString());
}

void FileManagerController::navigate(const QString &path)
{
    setLoading(true);
    setStatusMessage(QString());
    m_server->listDir(path);
}

void FileManagerController::goUp()
{
    if (m_currentPath.isEmpty())
        return;
    QStringList segments = m_currentPath.split('/', Qt::SkipEmptyParts);
    if (segments.isEmpty())
        return;
    segments.removeLast();
    navigate(QLatin1Char('/') + segments.join('/'));
}

void FileManagerController::refresh()
{
    navigate(m_currentPath);
}

QString FileManagerController::fullPathFor(const QString &name) const
{
    return m_currentPath.endsWith('/') ? m_currentPath + name : m_currentPath + QLatin1Char('/') + name;
}

void FileManagerController::openRow(int row)
{
    const QString name = m_model->nameAt(row);
    if (name.isEmpty())
        return;
    if (m_model->isDirAt(row)) {
        navigate(fullPathFor(name));
    } else {
        preview(fullPathFor(name), name);
    }
}

void FileManagerController::preview(const QString &fullPath, const QString &name)
{
    m_pendingPreviewPath = fullPath;
    setStatusMessage(QStringLiteral("Fetching \"%1\" to preview\u2026").arg(name));
    m_server->getFile(fullPath);
}

void FileManagerController::downloadRow(int row)
{
    const QString name = m_model->nameAt(row);
    if (name.isEmpty() || m_model->isDirAt(row))
        return;
    m_pendingDownloadPath = fullPathFor(name);
    setStatusMessage(QStringLiteral("Requesting \"%1\" from the phone\u2026").arg(name));
    m_server->getFile(m_pendingDownloadPath);
}

void FileManagerController::requestThumbnailIfNeeded(int row)
{
    if (m_model->isDirAt(row))
        return;
    const QString name = m_model->nameAt(row);
    if (name.isEmpty() || !kImageExtensions.contains(extensionOf(name)))
        return;
    const QString path = fullPathFor(name);
    if (m_requestedThumbnails.contains(path))
        return;
    m_requestedThumbnails.insert(path);
    m_server->getThumbnail(path);
}

QString FileManagerController::extensionOf(const QString &name)
{
    const int dot = name.lastIndexOf('.');
    return dot >= 0 ? name.mid(dot + 1).toLower() : QString();
}

QString FileManagerController::formatSize(qint64 bytes)
{
    if (bytes <= 0)
        return QString();
    static const char *units[] = {"B", "KB", "MB", "GB"};
    double size = static_cast<double>(bytes);
    int unit = 0;
    while (size >= 1024.0 && unit < 3) {
        size /= 1024.0;
        ++unit;
    }
    return QStringLiteral("%1 %2").arg(size, 0, 'f', 1).arg(units[unit]);
}

void FileManagerController::onDirListingReceived(const QString &path, const QVariantList &entries)
{
    QVector<FileEntryModel::Entry> rows;
    rows.reserve(entries.size());
    for (const QVariant &v : entries) {
        const QVariantMap m = v.toMap();
        FileEntryModel::Entry e;
        e.name = m.value("name").toString();
        e.isDir = m.value("isDir").toBool();
        e.size = m.value("size").toLongLong();
        e.modified = m.value("modified").toLongLong();
        rows.append(e);
    }
    m_currentPath = path;
    emit currentPathChanged();
    m_model->setEntries(rows);
    setLoading(false);
}

void FileManagerController::onFileProgress(const QString &path, qint64 received, qint64 total)
{
    if (path != m_pendingDownloadPath && path != m_pendingPreviewPath)
        return;
    const int pct = total > 0 ? static_cast<int>((received * 100) / total) : 0;
    const double mb = total / (1024.0 * 1024.0);
    setStatusMessage(QStringLiteral("Receiving\u2026 %1% of %2 MB").arg(pct).arg(mb, 0, 'f', 1));
}

void FileManagerController::onFileDataReceived(const QString &path, const QString &name, qint64, const QByteArray &bytes)
{
    if (path == m_pendingPreviewPath) {
        m_pendingPreviewPath.clear();
        setStatusMessage(QString());
        showPreviewFor(name, bytes);
        return;
    }

    if (path != m_pendingDownloadPath)
        return;
    m_pendingDownloadPath.clear();

    // A non-blocking QFileDialog (via open() + fileSelected) rather than
    // the static getSaveFileName(), which nests its own event loop and
    // would otherwise freeze the rest of the UI (screen mirroring, call/
    // notification updates) for as long as the save dialog is open.
    // DontUseNativeDialog keeps behavior consistent across platforms and
    // is what makes this path automatable in a test (see
    // objectName-based lookup + selectFile()/accept() in the test suite);
    // native dialogs on some Linux desktops don't reliably respond to
    // being driven programmatically the way this in-process one does.
    auto *dialog = new QFileDialog(nullptr, QStringLiteral("Save file from the obelisk"));
    dialog->setObjectName(QStringLiteral("downloadSaveDialog"));
    dialog->setAcceptMode(QFileDialog::AcceptSave);
    dialog->setFileMode(QFileDialog::AnyFile);
    dialog->setOption(QFileDialog::DontUseNativeDialog, true);
    dialog->selectFile(name);
    dialog->setAttribute(Qt::WA_DeleteOnClose);

    connect(dialog, &QFileDialog::fileSelected, this, [this, name, bytes](const QString &savePath) {
        if (savePath.isEmpty())
            return;
        QFile out(savePath);
        if (out.open(QIODevice::WriteOnly)) {
            out.write(bytes);
            out.close();
            setStatusMessage(QStringLiteral("Saved \"%1\" to your machine.").arg(name));
        } else {
            setStatusMessage(QStringLiteral("Could not save \"%1\": %2").arg(name, out.errorString()));
        }
    });

    dialog->open();
}

void FileManagerController::showPreviewFor(const QString &name, const QByteArray &bytes)
{
    const QString ext = extensionOf(name);

    if (kImageExtensions.contains(ext)) {
        auto *temp = new QTemporaryFile(this);
        temp->setFileTemplate(QDir::temp().filePath(QStringLiteral("obelisk_preview_XXXXXX.") + ext));
        if (temp->open()) {
            temp->write(bytes);
            temp->close();
            m_previewTempFiles.append(temp);
            emit imagePreviewReady(name, QUrl::fromLocalFile(temp->fileName()).toString());
            return;
        }
        delete temp;
    }

    if (kTextExtensions.contains(ext)) {
        auto toUtf16 = QStringDecoder(QStringDecoder::Utf8);
        const QString text = toUtf16(bytes);
        if (toUtf16.hasError()) {
            openWithSystemDefault(name, bytes);
        } else {
            emit textPreviewReady(name, text);
        }
        return;
    }

    openWithSystemDefault(name, bytes);
}

void FileManagerController::openWithSystemDefault(const QString &name, const QByteArray &bytes)
{
    // QDesktopServices::openUrl already abstracts the xdg-open/open/start
    // dance the Dart version did by hand per-platform.
    const QString filePath = QDir::temp().filePath(name);
    QFile file(filePath);
    if (file.open(QIODevice::WriteOnly)) {
        file.write(bytes);
        file.close();
        if (QDesktopServices::openUrl(QUrl::fromLocalFile(filePath)))
            setStatusMessage(QStringLiteral("Opened \"%1\" with your system's default app.").arg(name));
        else
            setStatusMessage(QStringLiteral("Could not open \"%1\".").arg(name));
    } else {
        setStatusMessage(QStringLiteral("Could not open \"%1\": %2").arg(name, file.errorString()));
    }
}

void FileManagerController::onThumbnailReceived(const QString &path, const QByteArray &imageBytes)
{
    // A plain base64 data: URL — QML's Image element loads these natively,
    // no image provider or temp file needed for the (small) thumbnails.
    const QString dataUrl = QStringLiteral("data:image/png;base64,") + QString::fromLatin1(imageBytes.toBase64());
    const int slash = path.lastIndexOf('/');
    const QString name = slash >= 0 ? path.mid(slash + 1) : path;
    m_model->setThumbnail(name, dataUrl);
}

void FileManagerController::onErrorOccurred(const QString &message)
{
    setStatusMessage(message);
    setLoading(false);
}

void FileManagerController::setStatusMessage(const QString &message)
{
    if (m_statusMessage == message)
        return;
    m_statusMessage = message;
    emit statusMessageChanged();
}

void FileManagerController::setLoading(bool loading)
{
    if (m_loading == loading)
        return;
    m_loading = loading;
    emit loadingChanged();
}
