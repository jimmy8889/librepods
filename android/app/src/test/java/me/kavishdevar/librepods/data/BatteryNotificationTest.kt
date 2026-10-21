package me.kavishdevar.librepods.data

import org.junit.Assert.*
import org.junit.Test

class BatteryNotificationTest {
 private fun packet(caseLevel:Int=50,caseStatus:Int=2):ByteArray = byteArrayOf(4,0,4,0,4,0,3,4,0,80,2,0,2,0,60,2,0,8,0,caseLevel.toByte(),caseStatus.toByte(),0)
 @Test fun initialUnknownIsNotZero() { assertTrue(AirPodsNotifications.BatteryNotification().getBattery().all{it.level == -1}) }
 @Test fun disconnectedCasePreservesLastReading() {
  val n=AirPodsNotifications.BatteryNotification();n.setBattery(packet());n.setBattery(packet(0,4))
  val c=n.getBattery().last();assertEquals(50,c.level);assertEquals(BatteryStatus.DISCONNECTED,c.status)
 }
 @Test fun genuineZeroIsRetained() {val n=AirPodsNotifications.BatteryNotification();n.setBattery(packet(0));assertEquals(0,n.getBattery().last().level);assertEquals(2,n.getBattery().last().status)}
 @Test fun invalidUnsignedValueCannotOverwriteValid() {val n=AirPodsNotifications.BatteryNotification();n.setBattery(packet());n.setBattery(packet(255));assertEquals(50,n.getBattery().last().level);assertEquals(4,n.getBattery().last().status)}
 @Test fun missingBleDoesNotFabricateEmptyBattery() {val n=AirPodsNotifications.BatteryNotification();n.setBatteryDirect(null,false,80,false,null,false);assertEquals(-1,n.getBattery().first().level);assertEquals(-1,n.getBattery().last().level)}
 @Test fun bleFillsMissingCaseWithoutOverwritingPreciseBuds() {val n=AirPodsNotifications.BatteryNotification();n.setBattery(packet(0,4));n.setBatteryDirect(70,false,50,false,40,false,true);assertEquals(listOf(80,60,40),n.getBattery().map{it.level})}
 @Test fun bleCaseKeepsUpdatingAlongsidePreciseBuds() {val n=AirPodsNotifications.BatteryNotification();n.setBattery(packet(0,4));n.setBatteryDirect(70,false,50,false,40,false,true);n.setBatteryDirect(70,false,50,false,30,false,true);assertEquals(listOf(80,60,30),n.getBattery().map{it.level})}
 @Test fun identicalBleReadingsDoNotTriggerAnotherUiUpdate() {val n=AirPodsNotifications.BatteryNotification();assertTrue(n.setBatteryDirect(80,false,60,false,null,false));assertFalse(n.setBatteryDirect(80,false,60,false,null,false))}
 @Test fun coarseBleIsMarkedAndPrecisePacketClearsMarker() {val n=AirPodsNotifications.BatteryNotification();n.setBatteryDirect(80,false,60,false,50,false);assertTrue(n.getBattery().all{it.estimated});n.setBattery(packet());assertTrue(n.getBattery().none{it.estimated})}
 @Test fun componentOrderIsResolvedById() {val p=packet();p[7]=2;p[12]=4;val n=AirPodsNotifications.BatteryNotification();n.setBattery(p);assertEquals(listOf(60,80,50),n.getBattery().map{it.level})}
 @Test fun malformedPacketIsIgnored() {val n=AirPodsNotifications.BatteryNotification();val p=packet();p[0]=0;n.setBattery(p);assertTrue(n.getBattery().all{it.level == -1})}
}
