#include <QtTest>
#include "battery.hpp"
#include "eardetection.hpp"

Q_LOGGING_CATEGORY(librepods, "librepods.test")

class ProtocolTest : public QObject {
    Q_OBJECT
private slots:
    void rejectsTruncatedNotifications() {
        Battery battery;
        EarDetection ears;
        const auto b = QByteArray::fromHex("04000400040003040150020102016002010801700101");
        const auto e = QByteArray::fromHex("0400040006000001");
        for (int n = 0; n < b.size(); ++n) QVERIFY(!battery.parsePacket(b.left(n)));
        QVERIFY(battery.parsePacket(b));
        for (int n = 0; n < e.size(); ++n) QVERIFY(!ears.parseData(e.left(n)));
        QVERIFY(ears.parseData(e));
        QVERIFY(ears.isPrimaryInEar());
        QVERIFY(!ears.isSecondaryInEar());
    }
    void rejectsWrongControlAndInvalidBattery() {
        QVERIFY(!AirPodsPackets::ConversationalAwareness::parseState(
            AirPodsPackets::OneBudANCMode::ENABLED).has_value());
        QVERIFY(!ControlCommand::parseActive(QByteArray::fromHex("040004000900")).has_value());
        Battery battery;
        QVERIFY(battery.parsePacket(QByteArray::fromHex("040004000400010401500201")));
        QCOMPARE(battery.getLeftPodLevel(), quint8(80));
        QVERIFY(!battery.parsePacket(QByteArray::fromHex("040004000400010401ff0201")));
        QCOMPARE(battery.getLeftPodLevel(), quint8(80));
        QVERIFY(battery.parsePacket(QByteArray::fromHex("040004000400010401000401")));
        QVERIFY(!battery.isLeftPodAvailable());
    }
};
QTEST_GUILESS_MAIN(ProtocolTest)
#include "protocol.moc"
