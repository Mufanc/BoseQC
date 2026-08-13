package xyz.mufanc.bqc

internal object Bmap {
    const val MAX_LEVEL = 10

    data class Settings(
        val level: Int,
        val autoCnc: Boolean,
        val spatialMode: Int,
        val wind: Boolean,
        val anc: Boolean,
    )

    fun getSettings() = byteArrayOf(0x1f, 0x0a, 0x01, 0)

    fun setSettings(settings: Settings) = byteArrayOf(
        0x1f,
        0x0a,
        0x02,
        0x05,
        settings.level.toByte(),
        settings.autoCnc.toByte(),
        settings.spatialMode.toByte(),
        settings.wind.toByte(),
        settings.anc.toByte(),
    )

    fun parseSettings(packet: ByteArray): Settings? {
        if (
            packet.size < 9 ||
            packet[0] != 0x1f.toByte() ||
            packet[1] != 0x0a.toByte() ||
            packet[3] != 0x05.toByte()
        ) {
            return null
        }
        return Settings(
            level = packet[4].toInt() and 0xff,
            autoCnc = packet[5] != 0.toByte(),
            spatialMode = packet[6].toInt() and 0xff,
            wind = packet[7] != 0.toByte(),
            anc = packet[8] != 0.toByte(),
        )
    }

    fun toUiLevel(protocolLevel: Int) = MAX_LEVEL - protocolLevel.coerceIn(0, MAX_LEVEL)

    fun toProtocolLevel(uiLevel: Int) = MAX_LEVEL - uiLevel.coerceIn(0, MAX_LEVEL)

    fun nextQuickLevel(level: Int) = when {
        level < 5 -> 5
        level < 10 -> 10
        else -> 0
    }

    fun ByteArray.hex() = joinToString(" ") { "%02X".format(it.toInt() and 0xff) }

    private fun Boolean.toByte() = if (this) 1.toByte() else 0.toByte()
}
