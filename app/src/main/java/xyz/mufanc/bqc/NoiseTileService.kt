package xyz.mufanc.bqc

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Handler
import android.os.Looper
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

class NoiseTileService : TileService() {
    private val main = Handler(Looper.getMainLooper())
    private var session: BoseSession? = null
    private var sentToggle = false

    override fun onStartListening() {
        super.onStartListening()
        updateTile(BoseSession.cachedSettings(this), null)
    }

    override fun onClick() {
        super.onClick()
        if (
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            updateTile(null, "打开 App 完成授权")
            return
        }

        closeSession()
        sentToggle = false
        qsTile?.apply {
            state = Tile.STATE_UNAVAILABLE
            subtitle = "正在连接"
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
                if (!sentToggle) {
                    sentToggle = true
                    session?.update(settings.copy(anc = !settings.anc))
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
                error != null -> {
                    state = Tile.STATE_UNAVAILABLE
                    subtitle = error
                    stateDescription = error
                }

                settings?.anc == true -> {
                    val level = Bmap.toUiLevel(settings.level)
                    state = Tile.STATE_ACTIVE
                    subtitle = "$level 级"
                    stateDescription = "降噪 $level 级"
                }

                else -> {
                    state = Tile.STATE_INACTIVE
                    subtitle = "关闭"
                    stateDescription = "降噪关闭"
                }
            }
            updateTile()
        }
    }

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
