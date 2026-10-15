#include "fileentrymodel.h"

FileEntryModel::FileEntryModel(QObject *parent)
    : QAbstractListModel(parent)
{
}

int FileEntryModel::rowCount(const QModelIndex &parent) const
{
    if (parent.isValid())
        return 0;
    return m_entries.size();
}

QVariant FileEntryModel::data(const QModelIndex &index, int role) const
{
    if (!index.isValid() || index.row() < 0 || index.row() >= m_entries.size())
        return {};

    const Entry &e = m_entries.at(index.row());
    switch (role) {
    case NameRole: return e.name;
    case IsDirRole: return e.isDir;
    case SizeRole: return e.size;
    case ModifiedRole: return e.modified;
    case ThumbnailUrlRole: return e.thumbnailUrl;
    default: return {};
    }
}

QHash<int, QByteArray> FileEntryModel::roleNames() const
{
    return {
        {NameRole, "name"},
        {IsDirRole, "isDir"},
        {SizeRole, "size"},
        {ModifiedRole, "modified"},
        {ThumbnailUrlRole, "thumbnailUrl"},
    };
}

void FileEntryModel::setEntries(const QVector<Entry> &entries)
{
    beginResetModel();
    m_entries = entries;
    endResetModel();
}

void FileEntryModel::setThumbnail(const QString &name, const QString &dataUrl)
{
    for (int i = 0; i < m_entries.size(); ++i) {
        if (m_entries.at(i).name == name) {
            m_entries[i].thumbnailUrl = dataUrl;
            const QModelIndex idx = index(i);
            emit dataChanged(idx, idx, {ThumbnailUrlRole});
            return;
        }
    }
}

QString FileEntryModel::nameAt(int row) const
{
    return (row >= 0 && row < m_entries.size()) ? m_entries.at(row).name : QString();
}

bool FileEntryModel::isDirAt(int row) const
{
    return (row >= 0 && row < m_entries.size()) ? m_entries.at(row).isDir : false;
}
