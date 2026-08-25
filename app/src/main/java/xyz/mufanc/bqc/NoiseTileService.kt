package xyz.mufanc.bqc

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

internal object ForegroundControl {
    var cycleLevel: (() -> Unit)? = null
}

class BluetoothConnectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        TileService.requestListeningState(
            context,
            ComponentName(context, NoiseTileService::class.java),
        )
    }
}

class NoiseTileService : TileService() {
    private val main = Handler(Looper.getMainLooper())
    private var session: BoseSession? = null
    private var sentUpdate = false

    override fun onStartListening() {
        super.onStartListening()
        updateTile(BoseSession.cachedSettings(this), null)
    }

    override fun onClick() {
        super.onClick()
        if (BoseSession.cachedSettings(this)?.anc != true) return
        ForegroundControl.cycleLevel?.let {
            it()
            return
        }
        if (
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            updateTile(null, "打开 App 完成授权")
            return
        }

        closeSession()
        sentUpdate = false
        qsTile?.apply {
            state = Tile.STATE_ACTIVE
            subtitle = "正在连接"
            stateDescription = "正在切换降噪等级"
            updateTile()
        }
        session = BoseSession(
            context = this,
            onStatus = { status ->
                if (status.phase == BoseSession.Phase.Error) {
                    updateTile(BoseSession.cachedSettings(this), status.message)
                    closeSession()
                }
            },
            onSettings = { settings ->
                if (!settings.anc) {
                    updateTile(settings, null)
                    closeSession()
                } else if (!sentUpdate) {
                    sentUpdate = true
                    val level = Bmap.nextQuickLevel(Bmap.toUiLevel(settings.level))
                    session?.update(settings.copy(level = Bmap.toProtocolLevel(level)))
                } else {
                    updateTile(settings, null)
                    closeSession()
                }
            },
        ).also { it.connect() }

        main.postDelayed({
            if (session != null) {
                updateTile(BoseSession.cachedSettings(this), "操作超时")
                closeSession()
            }
        }, 6_000)
    }

    private fun updateTile(settings: Bmap.Settings?, error: String?) {
        qsTile?.apply {
            icon = Icon.createWithResource(this@NoiseTileService, R.drawable.ic_tile)
            label = getString(R.string.tile_name)
            when {
                !isHeadphonesConnected() -> {
                    state = Tile.STATE_UNAVAILABLE
                    subtitle = error ?: "未连接"
                    stateDescription = error ?: "耳机未连接"
                }

                settings?.anc == true -> {
                    val level = Bmap.toUiLevel(settings.level)
                    state = Tile.STATE_ACTIVE
                    subtitle = error ?: "$level 级"
                    stateDescription = error ?: "降噪 $level 级"
                }

                else -> {
                    state = Tile.STATE_UNAVAILABLE
                    subtitle = "关闭"
                    stateDescription = "降噪关闭"
                }
            }
            updateTile()
        }
    }

    @SuppressLint("MissingPermission")
    private fun isHeadphonesConnected() =
        checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED &&
            getSystemService(BluetoothManager::class.java).adapter
                ?.getProfileConnectionState(BluetoothProfile.A2DP) ==
            BluetoothProfile.STATE_CONNECTED

    private fun closeSession() {
        session?.close()
        session = null
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        closeSession()
        super.onDestroy()
    }
}
