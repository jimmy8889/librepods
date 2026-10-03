package me.kavishdevar.librepods.finding

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Base64
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import me.kavishdevar.librepods.utils.BluetoothCryptography

data class BleInspectionState(
    val active: Boolean = false,
    val status: String = "Inspect the paired AirPods BLE interface to investigate case support.",
    val metadata: List<String> = emptyList()
)

/** Bounded service discovery only. Never probes unassociated nearby Apple devices. */
@SuppressLint("MissingPermission")
class PairedBleInspector(private val context: Context) {
    private val handler = Handler(Looper.getMainLooper())
    private val mutable = MutableStateFlow(BleInspectionState())
    val state: StateFlow<BleInspectionState> = mutable
    private var scanner: BluetoothLeScanner? = null
    private var scanCallback: ScanCallback? = null
    private var gatt: BluetoothGatt? = null
    private var irk: ByteArray? = null
    private var generation = 0L
    private var associatedSeen = false
    private val timeout = Runnable {
        finish(when {
            gatt != null -> "The associated BLE peer did not complete service discovery before timeout."
            associatedSeen -> "Associated AirPods broadcasts were found, but no connectable BLE interface was found before timeout."
            else -> "No connectable BLE peer resolved with the saved AirPods identity key before timeout. This does not establish whether the case is nearby."
        })
    }

    fun start() {
        close()
        associatedSeen = false
        if (listOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT).any {
                context.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED
            }) {
            mutable.value = BleInspectionState(status = "Grant LibrePods Nearby devices permission first.")
            return
        }
        irk = runCatching {
            val encoded = context.getSharedPreferences("settings", Context.MODE_PRIVATE).getString("IRK", null)
                ?: return@runCatching null
            val decoded = Base64.decode(encoded, Base64.DEFAULT)
            if (decoded.size == 16) decoded else { decoded.fill(0); null }
        }.getOrNull()
        if (irk == null) {
            mutable.value = BleInspectionState(status = "No valid paired AirPods identity key is available. Connect your AirPods in LibrePods first; nearby devices will not be probed.")
            return
        }
        scanner = context.getSystemService(BluetoothManager::class.java)?.adapter?.bluetoothLeScanner
        if (scanner == null) {
            finish("Bluetooth scanning is unavailable. Check Bluetooth settings.")
            return
        }
        val token = generation
        mutable.value = BleInspectionState(active = true, status = "Scanning for a connectable BLE peer resolved with your paired AirPods identity key…")
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                handler.post {
                    if (token != generation || scanCallback !== this) return@post
                    val key = irk ?: return@post
                    val address = result.device.address
                    if (((address.substringBefore(":").toIntOrNull(16) ?: return@post) and 0xC0) != 0x40) return@post
                    // Identity resolution associates a peer with the saved key; it is not
                    // proof that this peer is the charging case or Apple owner authentication.
                    if (!runCatching { BluetoothCryptography.verifyRPA(address, key) }.getOrDefault(false)) return@post
                    val firstMatch = !associatedSeen
                    associatedSeen = true
                    if (!result.isConnectable) {
                        if (firstMatch) mutable.value = mutable.value.copy(status = "Associated AirPods broadcasts found, but this peer is not connectable. Still scanning…")
                        return@post
                    }
                    stopScan()
                    irk?.fill(0); irk = null
                    mutable.value = mutable.value.copy(status = "Associated BLE peer found. Discovering service metadata; this does not identify or authenticate the charging case.")
                    connect(result.device, token)
                }
            }
            override fun onScanFailed(errorCode: Int) {
                handler.post { if (token == generation && scanCallback === this) finish("BLE scan failed (code $errorCode).") }
            }
        }
        scanCallback = callback
        handler.postDelayed(timeout, 25_000)
        runCatching {
            // Apple manufacturer filter only; no advertisement contents or addresses
            // are logged, retained or exported. The saved IRK gates any connection.
            scanner?.startScan(listOf(ScanFilter.Builder().setManufacturerData(76, byteArrayOf()).build()),
                ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build(), callback)
        }.onFailure { finish("Could not start paired BLE inspection.") }
    }

    private fun connect(device: BluetoothDevice, token: Long) {
        val callback = object : BluetoothGattCallback() {
            override fun onConnectionStateChange(peer: BluetoothGatt, status: Int, newState: Int) {
                if (token != generation || peer !== gatt) { peer.close(); return }
                if (status != BluetoothGatt.GATT_SUCCESS || newState == BluetoothProfile.STATE_DISCONNECTED) {
                    finish("The associated BLE peer disconnected (status $status). No case commands were sent.")
                } else if (newState == BluetoothProfile.STATE_CONNECTED) {
                    if (!runCatching { peer.discoverServices() }.getOrDefault(false)) finish("Service discovery could not start.")
                }
            }
            override fun onServicesDiscovered(peer: BluetoothGatt, status: Int) {
                if (token != generation || peer !== gatt) { peer.close(); return }
                if (status != BluetoothGatt.GATT_SUCCESS) { finish("Service discovery failed (status $status)."); return }
                val rows = runCatching { peer.services.take(32).flatMap { service ->
                    listOf("Service ${service.uuid}") + service.characteristics.take(128).map {
                        "  Characteristic ${it.uuid}; properties 0x${it.properties.toString(16)}"
                    }
                }.take(256) }.getOrElse { finish("Could not inspect service metadata. Check Nearby devices permission."); return }
                finish("Service discovery complete. These UUIDs are metadata, not UWB parameters or proof of case support.", rows)
            }
        }
        runCatching {
            gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE,
                BluetoothDevice.PHY_LE_1M_MASK, handler)
            if (gatt == null) finish("Could not connect to the associated BLE peer.")
        }.onFailure { finish("Could not connect to the associated BLE peer.") }
    }

    private fun stopScan() {
        val callback = scanCallback
        scanCallback = null
        if (callback != null) runCatching { scanner?.stopScan(callback) }
        scanner = null
    }
    private fun release() {
        generation++
        handler.removeCallbacks(timeout)
        stopScan()
        irk?.fill(0); irk = null
        val peer = gatt
        gatt = null
        if (peer != null) { runCatching { peer.disconnect() }; runCatching { peer.close() } }
    }
    private fun finish(status: String, metadata: List<String> = emptyList()) {
        release()
        mutable.value = BleInspectionState(status = status, metadata = metadata)
    }
    fun close() { release(); mutable.value = BleInspectionState() }
}
