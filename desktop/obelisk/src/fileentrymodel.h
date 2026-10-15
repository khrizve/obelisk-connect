#pragma once

#include <QAbstractListModel>
#include <QVector>

/// Backs the File Manager's grid. A proper list model (rather than a plain
/// QVariantList property) because thumbnails arrive asynchronously per
/// row after the initial listing — dataChanged() on just that row avoids
/// re-rendering the whole grid each time one thumbnail comes in, unlike
/// re-assigning a whole QVariantList would.
class FileEntryModel : public QAbstractListModel
{
    Q_OBJECT

public:
    struct Entry {
        QString name;
        bool isDir = false;
        qint64 size = 0;
        qint64 modified = 0;
        QString thumbnailUrl; // "data:image/...;base64,..." once loaded, else ""
    };

    enum Roles {
        NameRole = Qt::UserRole + 1,
        IsDirRole,
        SizeRole,
        ModifiedRole,
        ThumbnailUrlRole,
    };

    explicit FileEntryModel(QObject *parent = nullptr);

    int rowCount(const QModelIndex &parent = QModelIndex()) const override;
    QVariant data(const QModelIndex &index, int role) const override;
    QHash<int, QByteArray> roleNames() const override;

    /// Replaces the whole listing, e.g. after a dir_listing message.
    void setEntries(const QVector<Entry> &entries);

    /// Sets one row's thumbnail by name, if that row still exists.
    void setThumbnail(const QString &name, const QString &dataUrl);

    /// name -> full server-side path, for building requests from QML
    /// without duplicating the "join current dir + name" logic there.
    Q_INVOKABLE QString nameAt(int row) const;
    Q_INVOKABLE bool isDirAt(int row) const;

private:
    QVector<Entry> m_entries;
};
