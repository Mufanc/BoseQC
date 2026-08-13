package xyz.mufanc.bqc

import android.Manifest
import android.app.StatusBarManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Icon
import android.os.Bundle
import android.service.quicksettings.TileService
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt
import xyz.mufanc.bqc.ui.theme.BoseQCTheme

class MainActivity : ComponentActivity() {
    private var session: BoseSession? = null
    private var showTileSetup by mutableStateOf(true)
    private var uiState by mutableStateOf(
        ControlUiState(
            deviceName = "Bose QC Ultra 2",
            settings = null,
        ),
    )

    private val permission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) connect() else {
            uiState = uiState.copy(
                phase = BoseSession.Phase.Error,
                message = "允许“附近设备”后才能控制耳机",
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        showTileSetup = intent.action != TileService.ACTION_QS_TILE_PREFERENCES
        uiState = uiState.copy(
            deviceName = BoseSession.cachedDeviceName(this) ?: uiState.deviceName,
            settings = BoseSession.cachedSettings(this),
        )
        enableEdgeToEdge()
        setContent {
            BoseQCTheme {
                ControlScreen(
                    state = uiState,
                    onToggle = ::setAnc,
                    onLevelChange = { uiState = uiState.copy(levelPreview = it) },
                    onLevelCommit = ::setLevel,
                    onRetry = ::ensurePermissionAndConnect,
                    onAddTile = ::requestTile,
                    showTileSetup = showTileSetup,
                )
            }
        }
        ensurePermissionAndConnect()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        showTileSetup = intent.action != TileService.ACTION_QS_TILE_PREFERENCES
    }

    override fun onStart() {
        super.onStart()
        if (
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            connect()
        }
    }

    override fun onStop() {
        session?.close()
        session = null
        super.onStop()
    }

    private fun ensurePermissionAndConnect() {
        if (
            checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            connect()
        } else {
            permission.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun connect() {
        if (session != null) return
        uiState = uiState.copy(
            phase = BoseSession.Phase.Connecting,
            message = "正在连接",
        )
        session = BoseSession(
            context = this,
            onStatus = { status ->
                uiState = uiState.copy(
                    phase = status.phase,
                    message = status.message,
                    deviceName = status.deviceName ?: uiState.deviceName,
                )
                if (status.phase == BoseSession.Phase.Error) {
                    session?.close()
                    session = null
                }
            },
            onSettings = { settings ->
                uiState = uiState.copy(
                    phase = BoseSession.Phase.Connected,
                    message = "已连接",
                    settings = settings,
                    levelPreview = null,
                )
            },
        ).also { it.connect() }
    }

    private fun setAnc(enabled: Boolean) {
        val settings = uiState.settings ?: return
        session?.update(settings.copy(anc = enabled))
    }

    private fun setLevel(uiLevel: Int) {
        val settings = uiState.settings ?: return
        session?.update(settings.copy(level = Bmap.toProtocolLevel(uiLevel)))
    }

    private fun requestTile() {
        val manager = getSystemService(StatusBarManager::class.java)
        manager.requestAddTileService(
            ComponentName(this, NoiseTileService::class.java),
            getString(R.string.tile_name),
            Icon.createWithResource(this, R.drawable.ic_tile),
            mainExecutor,
        ) { result ->
            val text = if (
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ADDED ||
                result == StatusBarManager.TILE_ADD_REQUEST_RESULT_TILE_ALREADY_ADDED
            ) {
                "快捷开关已就绪"
            } else {
                "没有添加快捷开关"
            }
            Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
        }
    }
}

private data class ControlUiState(
    val phase: BoseSession.Phase = BoseSession.Phase.Idle,
    val message: String = "等待连接",
    val deviceName: String,
    val settings: Bmap.Settings?,
    val levelPreview: Int? = null,
) {
    val uiLevel: Int
        get() = levelPreview ?: settings?.let { Bmap.toUiLevel(it.level) } ?: Bmap.MAX_LEVEL
}

@Composable
private fun ControlScreen(
    state: ControlUiState,
    onToggle: (Boolean) -> Unit,
    onLevelChange: (Int) -> Unit,
    onLevelCommit: (Int) -> Unit,
    onRetry: () -> Unit,
    onAddTile: () -> Unit,
    showTileSetup: Boolean = true,
) {
    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .verticalScroll(rememberScrollState())
                .systemBarsPadding()
                .padding(padding)
                .padding(horizontal = 24.dp),
        ) {
            Spacer(Modifier.height(22.dp))
            DeviceHeader(
                logo = painterResource(R.drawable.ic_app_icon),
                name = state.deviceName,
                phase = state.phase,
                message = state.message,
            )
            Spacer(Modifier.height(22.dp))
            QuietField(
                level = state.uiLevel,
                enabled = state.settings?.anc == true,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(328.dp),
            )
            NoiseControls(
                state = state,
                onToggle = onToggle,
                onLevelChange = onLevelChange,
                onLevelCommit = onLevelCommit,
            )
            if (state.phase == BoseSession.Phase.Error) {
                Spacer(Modifier.height(16.dp))
                ErrorBanner(message = state.message, onRetry = onRetry)
            }
            if (showTileSetup) {
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = onAddTile,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(18.dp),
                ) {
                    Text("添加到快捷设置", fontWeight = FontWeight.SemiBold)
                }
                Text(
                    text = "添加后，下拉通知栏即可一键开启或关闭降噪",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 28.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.Center,
                )
            } else {
                Spacer(Modifier.height(28.dp))
            }
        }
    }
}

@Composable
private fun DeviceHeader(
    logo: Painter,
    name: String,
    phase: BoseSession.Phase,
    message: String,
) {
    val statusColor by animateColorAsState(
        targetValue = when (phase) {
            BoseSession.Phase.Connected -> MaterialTheme.colorScheme.primary
            BoseSession.Phase.Error -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.outline
        },
        label = "connection color",
    )
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.foundation.Image(
            painter = logo,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(14.dp)),
        )
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        Box(
            modifier = Modifier
                .size(9.dp)
                .clip(CircleShape)
                .background(statusColor),
        )
    }
}

@Composable
private fun QuietField(level: Int, enabled: Boolean, modifier: Modifier = Modifier) {
    val animatedLevel by animateFloatAsState(
        targetValue = if (enabled) level.toFloat() else 0f,
        label = "quiet field",
    )
    val progress = animatedLevel / Bmap.MAX_LEVEL
    val primary = MaterialTheme.colorScheme.primary
    val field = MaterialTheme.colorScheme.primaryContainer
    val surface = MaterialTheme.colorScheme.surface

    Box(
        modifier = modifier.semantics {
            contentDescription = if (enabled) "降噪等级 $level" else "降噪已关闭"
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(304.dp)) {
            val center = Offset(size.width / 2, size.height / 2)
            val coreRadius = size.minDimension * 0.27f
            drawCircle(
                color = field.copy(alpha = if (enabled) 0.62f else 0.28f),
                radius = coreRadius,
                center = center,
            )
            drawCircle(
                color = surface,
                radius = coreRadius - 10.dp.toPx(),
                center = center,
            )
            repeat(3) { index ->
                val quietPull = progress * 20.dp.toPx()
                val radius = coreRadius + (index + 1) * 24.dp.toPx() - quietPull
                val topLeft = Offset(center.x - radius, center.y - radius)
                val diameter = radius * 2
                drawArc(
                    color = primary.copy(
                        alpha = if (enabled) 0.32f - index * 0.075f else 0.10f,
                    ),
                    startAngle = -64f + index * 7f,
                    sweepAngle = 308f - index * 14f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = Size(diameter, diameter),
                    style = Stroke(
                        width = (3 - index * 0.55f).dp.toPx(),
                        cap = StrokeCap.Round,
                    ),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (enabled) level.toString() else "—",
                color = MaterialTheme.colorScheme.onSurface,
                fontFamily = FontFamily.SansSerif,
                fontSize = 76.sp,
                fontWeight = FontWeight.Light,
                letterSpacing = (-3).sp,
            )
            Text(
                text = if (enabled) "降噪等级" else "噪声控制关闭",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

@Composable
private fun NoiseControls(
    state: ControlUiState,
    onToggle: (Boolean) -> Unit,
    onLevelChange: (Int) -> Unit,
    onLevelCommit: (Int) -> Unit,
) {
    val available = state.settings != null && state.phase == BoseSession.Phase.Connected
    val enabled = state.settings?.anc == true
    var pendingLevel = state.uiLevel
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(28.dp),
        tonalElevation = 1.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 22.dp, vertical = 20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "噪声控制",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = if (enabled) "正在削弱外界声音" else "保留自然环境声",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = onToggle,
                    enabled = available,
                    colors = SwitchDefaults.colors(
                        checkedTrackColor = MaterialTheme.colorScheme.primary,
                        checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                    ),
                )
            }
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 18.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Text(
                    text = "强度",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = state.uiLevel.toString().padStart(2, '0'),
                    color = MaterialTheme.colorScheme.primary,
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
            Slider(
                value = state.uiLevel.toFloat(),
                onValueChange = {
                    pendingLevel = it.roundToInt()
                    onLevelChange(pendingLevel)
                },
                onValueChangeFinished = { onLevelCommit(pendingLevel) },
                enabled = available && enabled,
                valueRange = 0f..Bmap.MAX_LEVEL.toFloat(),
                steps = Bmap.MAX_LEVEL - 1,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                    activeTickColor = MaterialTheme.colorScheme.onPrimary,
                ),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("低", style = MaterialTheme.typography.labelSmall)
                Text("高", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onRetry: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(20.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = message,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onErrorContainer,
                style = MaterialTheme.typography.bodyMedium,
            )
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.onErrorContainer,
                    contentColor = MaterialTheme.colorScheme.errorContainer,
                ),
            ) {
                Text("重试")
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun ControlPreview() {
    BoseQCTheme {
        ControlScreen(
            state = ControlUiState(
                phase = BoseSession.Phase.Connected,
                message = "已连接",
                deviceName = "Bose QC Ultra 2",
                settings = Bmap.Settings(3, false, 0, false, true),
            ),
            onToggle = {},
            onLevelChange = {},
            onLevelCommit = {},
            onRetry = {},
            onAddTile = {},
        )
    }
}
