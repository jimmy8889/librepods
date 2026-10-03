#include <QCoreApplication>
#include <QLocalSocket>
#include <QTextStream>
#include <QJsonDocument>
#include <QJsonObject>
#include "ipc.hpp"

int main(int argc, char *argv[]) {
    QCoreApplication app(argc, argv);
    const QStringList commands = {"status", "watch", "reopen", "noise:off", "noise:anc",
        "noise:transparency", "noise:adaptive", "conversation:on", "conversation:off",
        "one-bud-anc:on", "one-bud-anc:off"};
    if (argc != 2 || !commands.contains(QString::fromUtf8(argv[1]))) {
        QTextStream(stderr) << "Usage: librepods-ctl <command>\nCommands: " << commands.join(", ") << '\n';
        return 1;
    }
    QLocalSocket socket;
    socket.connectToServer(librePodsSocketName());
    if (!socket.waitForConnected(1000)) {
        QTextStream(stderr) << "Could not connect to LibrePods (is it running?)\n";
        return 1;
    }
    const bool watch = QString::fromUtf8(argv[1]) == "watch";
    socket.write(QByteArray(argv[1]) + '\n');
    if (!socket.waitForBytesWritten(1000)) return 1;
    if (watch) {
        QObject::connect(&socket, &QLocalSocket::readyRead, [&]() {
            while (socket.canReadLine()) QTextStream(stdout) << socket.readLine() << Qt::flush;
        });
        QObject::connect(&socket, &QLocalSocket::disconnected, &app, [&]() { app.exit(1); });
        return app.exec();
    }
    while (!socket.canReadLine()) {
        if (!socket.waitForReadyRead(3000)) {
            QTextStream(stderr) << "No complete response from LibrePods\n";
            return 1;
        }
    }
    const QByteArray response = socket.readLine();
    QTextStream(stdout) << response;
    const auto obj = QJsonDocument::fromJson(response).object();
    return obj.value("ok").isBool() && !obj.value("ok").toBool() ? 1 : 0;
}
