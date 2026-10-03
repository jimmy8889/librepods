#pragma once
#include <QDir>
#include <QStandardPaths>
#include <QString>

inline QString librePodsSocketName()
{
    return QDir(QStandardPaths::writableLocation(QStandardPaths::RuntimeLocation))
        .filePath("librepods.sock");
}
