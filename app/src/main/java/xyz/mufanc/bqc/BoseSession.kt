package xyz.mufanc.bqc

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import java.io.Closeable
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.Executors

internal class BoseSession(
    context: Context,
    private val onStatus: (Status) -> Unit,
    private val onSettings: (Bmap.Settings) -> Unit,
) : Closeable {
    enum class Phase { Idle, Connecting, Connected, Error }

    data class Status(
        val phase: Phase,
        val message: String,
        val deviceName: String? = null,
    )

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val writer = Executors.newSingleThreadExecutor()

    @Volatile private var closed = false
    @Volatile private var output: OutputStream? = null
    @Volatile private var socket: android.bluetooth.BluetoothSocket? = null

    @SuppressLint("MissingPermission")
    fun connect() {
        if (
            appContext.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            publish(Status(Phase.Error, "需要附近设备权限"))
            return
        }
        Thread({
            val manager = appContext.getSystemService(BluetoothManager::class.java)
            val adapter = manager.adapter
            if (adapter == null || !adapter.isEnabled) {
                publish(Status(Phase.Error, "请先打开蓝牙"))
                return@Thread
            }
            val device = findDevice(appContext, adapter.bondedDevices)
            if (device == null) {
                publish(Status(Phase.Error, "没有找到已配对的 Bose 耳机"))
                return@Thread
            }
            publish(Status(Phase.Connecting, "正在连接", device.name))
            try {
                val current = device.createRfcommSocketToServiceRecord(SPP_UUID)
                socket = current
                current.connect()
                output = current.outputStream
                saveDevice(appContext, device)
                publish(Status(Phase.Connected, "已连接", device.name))
                requestSettings()
                readLoop(current.inputStream)
            } catch (error: IOException) {
                if (!closed) publish(Status(Phase.Error, friendlyError(error), device.name))
            } catch (error: SecurityException) {
                if (!closed) publish(Status(Phase.Error, "蓝牙权限不可用", device.name))
            } finally {
                close()
            }
        }, "bose-spp-reader").start()
    }

    fun requestSettings() = write(Bmap.getSettings())

    fun update(settings: Bmap.Settings) = write(Bmap.setSettings(settings))

    private fun write(packet: ByteArray) {
        writer.execute {
            val stream = output ?: return@execute
            try {
                synchronized(stream) {
                    stream.write(packet)
                    stream.flush()
                }
            } catch (error: IOException) {
                if (!closed) publish(Status(Phase.Error, "写入耳机失败"))
            }
        }
    }

    private fun readLoop(input: InputStream) {
        while (!closed) {
            val header = ByteArray(4)
            readFully(input, header, 0, header.size)
            val payloadLength = header[3].toInt() and 0xff
            val packet = ByteArray(4 + payloadLength)
            header.copyInto(packet)
            readFully(input, packet, 4, payloadLength)
            Bmap.parseSettings(packet)?.let {
                saveSettings(appContext, it)
                main.post { if (!closed) onSettings(it) }
            }
        }
    }

    private fun publish(status: Status) {
        main.post { onStatus(status) }
    }

    override fun close() {
        if (closed) return
        closed = true
        output = null
        writer.shutdownNow()
        runCatching { socket?.close() }
        socket = null
    }

    companion object {
        private val SPP_UUID: UUID =
            UUID.fromString("00001101-0000-1000-8000-00805f9b34fb")
        private const val PREFS = "bose_control"
        private const val DEVICE_ADDRESS = "device_address"

        private fun readFully(input: InputStream, target: ByteArray, start: Int, count: Int) {
            var offset = start
            var remaining = count
            while (remaining > 0) {
                val read = input.read(target, offset, remaining)
                if (read < 0) throw IOException("SPP disconnected")
                offset += read
                remaining -= read
            }
        }

        @SuppressLint("MissingPermission")
        private fun findDevice(
            context: Context,
            bondedDevices: Set<BluetoothDevice>,
        ): BluetoothDevice? {
            val boseDevices = bondedDevices.filter {
                it.name?.contains("bose", ignoreCase = true) == true
            }
            val savedAddress = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(DEVICE_ADDRESS, null)
            return boseDevices.firstOrNull { it.address == savedAddress }
                ?: boseDevices.firstOrNull {
                    it.name?.contains("QC Ultra 2", ignoreCase = true) == true
                }
                ?: boseDevices.firstOrNull()
        }

        @SuppressLint("MissingPermission")
        private fun saveDevice(context: Context, device: BluetoothDevice) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(DEVICE_ADDRESS, device.address)
                .putString("device_name", device.name)
                .apply()
        }

        fun saveSettings(context: Context, settings: Bmap.Settings) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putInt("level", settings.level)
                .putBoolean("auto_cnc", settings.autoCnc)
                .putInt("spatial_mode", settings.spatialMode)
                .putBoolean("wind", settings.wind)
                .putBoolean("anc", settings.anc)
                .putBoolean("has_settings", true)
                .apply()
        }

        fun cachedSettings(context: Context): Bmap.Settings? {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            if (!prefs.getBoolean("has_settings", false)) return null
            return Bmap.Settings(
                level = prefs.getInt("level", Bmap.MAX_LEVEL),
                autoCnc = prefs.getBoolean("auto_cnc", false),
                spatialMode = prefs.getInt("spatial_mode", 0),
                wind = prefs.getBoolean("wind", false),
                anc = prefs.getBoolean("anc", true),
            )
        }

        fun cachedDeviceName(context: Context): String? =
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString("device_name", null)

        private fun friendlyError(error: IOException): String {
            val message = error.message.orEmpty()
            return if (
                message.contains("socket", ignoreCase = true) ||
                message.contains("connect", ignoreCase = true)
            ) {
                "连接失败，请先退出 Bose App"
            } else {
                "耳机连接已断开"
            }
        }
    }
}
