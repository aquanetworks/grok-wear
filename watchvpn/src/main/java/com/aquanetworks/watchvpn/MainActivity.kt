package com.aquanetworks.watchvpn

import android.app.Activity
import android.app.RemoteInput
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.view.inputmethod.EditorInfo
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.AutoCenteringParams
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.foundation.rotary.RotaryScrollableDefaults
import androidx.wear.compose.foundation.rotary.rotaryScrollable
import androidx.wear.compose.material.Chip
import androidx.wear.compose.material.ChipDefaults
import androidx.wear.compose.material.CompactChip
import androidx.wear.compose.material.ListHeader
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Switch
import androidx.wear.compose.material.Text
import androidx.wear.compose.material.ToggleChip
import androidx.wear.input.RemoteInputIntentHelper

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleImport(intent)
        setContent { MaterialTheme { VpnApp(this) } }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleImport(intent)
    }

    private fun handleImport(intent: Intent?) {
        if (intent == null) return
        val uri = intent.data
        if (uri != null && ImportLinks.isImportScheme(uri.scheme, uri.host)) {
            VpnRuntime.importIntent(intent)
            return
        }
        if (intent.getStringExtra("url") != null) VpnRuntime.importIntent(intent)
        if (intent.getBooleanExtra("toggle", false)) VpnRuntime.toggle()
    }
}

private const val SUBSCRIPTION_KEY = "subscription"

@Composable
private fun VpnApp(activity: ComponentActivity) {
    var snap by remember { mutableStateOf(VpnRuntime.snapshot()) }
    var screen by remember { mutableStateOf("home") }
    DisposableEffect(Unit) {
        val listener = VpnRuntime.Listener { snap = it }
        VpnRuntime.addListener(listener)
        onDispose { VpnRuntime.removeListener(listener) }
    }
    val prepareLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        AppLog.line("prepare result=" + result.resultCode)
        VpnRuntime.connect()
    }
    val inputLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val values = RemoteInput.getResultsFromIntent(result.data) ?: return@rememberLauncherForActivityResult
        val text = values.getCharSequence(SUBSCRIPTION_KEY)?.toString() ?: return@rememberLauncherForActivityResult
        VpnRuntime.importText(text)
    }

    fun requestConnect() {
        AppLog.line("Connect UI tap")
        val snap = VpnRuntime.snapshot()
        if (snap.tunMode && snap.vpnAvailable) {
            val prepare = VpnConsent.prepareIntent(activity)
            if (VpnConsent.canLaunchPrepare(activity, prepare) && prepare != null) {
                try {
                    AppLog.line("launching prepare activity")
                    prepareLauncher.launch(prepare)
                    return
                } catch (e: ActivityNotFoundException) {
                    AppLog.line("prepare activity missing")
                } catch (e: Exception) {
                    AppLog.line("prepare launch failed " + e.javaClass.simpleName)
                }
            }
        }
        VpnRuntime.connect()
    }

    fun typeSubscription() {
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        val field = RemoteInput.Builder(SUBSCRIPTION_KEY)
            .setLabel(activity.getString(R.string.hint_subscription))
            .setAllowFreeFormInput(true)
            .setEditChoicesBeforeSending(RemoteInput.EDIT_CHOICES_BEFORE_SENDING_ENABLED)
            .build()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(field))
        try {
            intent.putExtra("android.intent.extra.IME_ACTION_ID", EditorInfo.IME_ACTION_DONE)
            inputLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            VpnRuntime.onServicePhase("error", activity.getString(R.string.err_no_keyboard))
        }
    }

    when (screen) {
        "servers" -> ServersScreen(
            snap,
            onBack = { screen = "home" },
            onSelect = { VpnRuntime.select(it) },
            onTest = { VpnRuntime.testLatency(it) },
        )
        "help" -> HelpScreen(
            snap,
            onBack = { screen = "home" },
            onLog = { screen = "log" },
            onUa = { VpnRuntime.setUserAgent(it) },
            onTun = { VpnRuntime.setTunMode(it) },
        )
        "log" -> LogScreen(snap, onBack = { screen = "help" }, onRefresh = { VpnRuntime.refreshUi() })
        else -> HomeScreen(
            snap = snap,
            onToggle = {
                if (snap.connected() || snap.phase == "connecting") VpnRuntime.disconnect()
                else requestConnect()
            },
            onTypeUrl = { typeSubscription() },
            onUpdate = { VpnRuntime.refreshSubscription() },
            onServers = { screen = "servers" },
            onHelp = { screen = "help" },
            onLog = { screen = "log" },
        )
    }
}

@Composable
private fun HomeScreen(
    snap: Snapshot,
    onToggle: () -> Unit,
    onTypeUrl: () -> Unit,
    onUpdate: () -> Unit,
    onServers: () -> Unit,
    onHelp: () -> Unit,
    onLog: () -> Unit,
) {
    WatchList {
        item { ListHeader { Text("Watch VPN") } }
        item {
            Text(
                statusLine(snap),
                color = statusColor(snap),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.body2,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        }
        if (snap.profileTitle.isNotEmpty()) {
            item {
                Text(
                    snap.profileTitle,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption2,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
        }
        if (snap.userinfo.isNotEmpty()) {
            item {
                Text(
                    snap.userinfo,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption3,
                    modifier = Modifier.padding(horizontal = 10.dp),
                )
            }
        }
        item {
            Text(
                "Прокси ${snap.proxyEndpoint}" + when {
                    snap.connected() && snap.httpProxyOn -> " · http_proxy вкл"
                    snap.connected() && !snap.writeSecure -> " · нужен grant или SET"
                    else -> ""
                },
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption2,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
        }
        if (!snap.writeSecure) {
            item {
                Text(
                    "Сначала:\n${SystemProxy.GRANT_CMD}",
                    color = Color(0xFF8DB8FF),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption3,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        if (snap.error.contains(SystemProxy.CLEAR_CMD) || snap.error.contains(Health.CLEAR_BANNER.take(20))) {
            item {
                Text(
                    Health.CLEAR_BANNER,
                    color = Color(0xFFFF1744),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        item {
            Text(
                SystemProxy.MESSENGER_WARN,
                color = Color(0xFFFFD60A),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption3,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            )
        }
        if (snap.error.isNotEmpty()) {
            item {
                Text(
                    snap.error,
                    color = Color(0xFFFF8A80),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.caption2,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                )
            }
        }
        item {
            ToggleChip(
                checked = snap.connected() || snap.phase == "connecting",
                onCheckedChange = { onToggle() },
                label = { Text(if (snap.connected() || snap.phase == "connecting") "Отключить" else "Подключить") },
                toggleControl = {
                    Switch(checked = snap.connected() || snap.phase == "connecting")
                },
                enabled = true,
            )
        }
        item {
            Chip(
                onClick = onServers,
                label = { Text(if (snap.selected.isEmpty()) "Серверы" else "✓ ${snap.selected}") },
                secondaryLabel = {
                    Text(
                        when {
                            snap.servers.isEmpty() -> "сначала подписка"
                            snap.lastDelayMs > 0 -> "${snap.servers.size} · ${snap.lastDelayMs} мс"
                            else -> "${snap.servers.size} серверов"
                        }
                    )
                },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        item {
            Chip(
                onClick = onTypeUrl,
                label = { Text("Подписка") },
                secondaryLabel = { Text(if (snap.urlMasked.isEmpty()) "ввести HTTPS URL" else snap.urlMasked) },
                colors = ChipDefaults.secondaryChipColors(),
            )
        }
        item {
            CompactChip(onClick = onUpdate, label = { Text("Обновить подписку") }, enabled = snap.urlMasked.isNotEmpty())
        }
        item { CompactChip(onClick = onHelp, label = { Text("Справка / UA") }) }
        item { CompactChip(onClick = onLog, label = { Text("Журнал") }) }
    }
}

@Composable
private fun ServersScreen(snap: Snapshot, onBack: () -> Unit, onSelect: (String) -> Unit, onTest: (String) -> Unit) {
    WatchList {
        item { ListHeader { Text("Серверы") } }
        item { CompactChip(onClick = onBack, label = { Text("Назад") }) }
        if (snap.selected.isNotEmpty()) {
            item {
                Text(
                    "Выбран: ${snap.selected}",
                    textAlign = TextAlign.Center,
                    color = Color(0xFF30D158),
                    modifier = Modifier.padding(horizontal = 10.dp),
                    style = MaterialTheme.typography.caption2,
                )
            }
        }
        if (snap.servers.isEmpty()) {
            item { Text("Нет серверов. Введите ссылку подписки.", textAlign = TextAlign.Center, modifier = Modifier.padding(12.dp)) }
        }
        snap.servers.forEach { row ->
            item {
                Chip(
                    onClick = { onSelect(row.name) },
                    label = { Text(if (row.selected) "✓ ${row.name}" else row.name) },
                    secondaryLabel = {
                        val delay = if (row.delayMs > 0) "${row.delayMs} мс" else "нет замера"
                        Text("${row.type} · $delay")
                    },
                    colors = if (row.selected) ChipDefaults.primaryChipColors() else ChipDefaults.secondaryChipColors(),
                )
            }
            item {
                CompactChip(
                    onClick = { onTest(row.name) },
                    label = { Text("Пинг") },
                )
            }
        }
    }
}

@Composable
private fun HelpScreen(snap: Snapshot, onBack: () -> Unit, onLog: () -> Unit, onUa: (String) -> Unit, onTun: (Boolean) -> Unit) {
    WatchList {
        item { ListHeader { Text("Справка") } }
        item { CompactChip(onClick = onBack, label = { Text("Назад") }) }
        item {
            Text(
                "Системный HTTP-прокси ${snap.proxyEndpoint}. Без root и Shizuku. TUN только если VpnService есть (по умолчанию выкл).",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 12.dp),
                style = MaterialTheme.typography.caption2,
            )
        }
        item {
            Text(
                SystemProxy.MESSENGER_WARN,
                color = Color(0xFFFFD60A),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.caption3,
            )
        }
        item {
            Text(
                if (snap.writeSecure) "WRITE_SECURE_SETTINGS: есть" else "WRITE_SECURE_SETTINGS: нет",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption2,
            )
        }
        item {
            Text(
                SystemProxy.GRANT_CMD,
                textAlign = TextAlign.Center,
                color = Color(0xFF8DB8FF),
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.caption3,
            )
        }
        item {
            Text(
                SystemProxy.SET_CMD,
                textAlign = TextAlign.Center,
                color = Color(0xFF8DB8FF),
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.caption3,
            )
        }
        item {
            Text(
                SystemProxy.CLEAR_CMD,
                textAlign = TextAlign.Center,
                color = Color(0xFF8DB8FF),
                modifier = Modifier.padding(horizontal = 8.dp),
                style = MaterialTheme.typography.caption3,
            )
        }
        item {
            Text(
                snap.consent,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                style = MaterialTheme.typography.caption3,
            )
        }
        item {
            Text(
                if (snap.vpnAvailable) "VpnService: есть" else "VpnService: нет (IVpnManager=null)",
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.caption2,
            )
        }
        if (snap.vpnAvailable) {
            item {
                ToggleChip(
                    checked = snap.tunMode,
                    onCheckedChange = { onTun(it) },
                    label = { Text(if (snap.tunMode) "TUN вкл" else "TUN выкл") },
                    toggleControl = { Switch(checked = snap.tunMode) },
                )
            }
        }
        item {
            Text(
                "Движок in-process (libmihomo-jni). Exec-потомок Android убивает (phantom process).",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp),
                style = MaterialTheme.typography.caption3,
            )
        }
        item {
            Text(
                "Опционально батарея:\n${SystemProxy.WHITELIST_CMD}",
                textAlign = TextAlign.Center,
                color = Color(0xFF8DB8FF),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                style = MaterialTheme.typography.caption3,
            )
        }
        item {
            Text(
                "Клиент UA (сейчас ${UserAgents.byId(snap.userAgentId).label}). При заглушках панели пробуем остальные.",
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                style = MaterialTheme.typography.caption2,
            )
        }
        UserAgents.ALL.forEach { preset ->
            item {
                CompactChip(
                    onClick = { onUa(preset.id) },
                    label = { Text(if (preset.id == snap.userAgentId) "✓ ${preset.label}" else preset.label) },
                )
            }
        }
        item { CompactChip(onClick = onLog, label = { Text("Журнал") }) }
    }
}

@Composable
private fun LogScreen(snap: Snapshot, onBack: () -> Unit, onRefresh: () -> Unit) {
    val lines = snap.logText.ifEmpty { AppLog.text() }.split('\n').filter { it.isNotEmpty() }
    WatchList {
        item { ListHeader { Text("Журнал") } }
        item { CompactChip(onClick = onBack, label = { Text("Назад") }) }
        item { CompactChip(onClick = onRefresh, label = { Text("Обновить") }) }
        if (lines.isEmpty()) {
            item { Text("Пока пусто. Нажмите Подключить или Обновить подписку.", textAlign = TextAlign.Center, modifier = Modifier.padding(10.dp)) }
        }
        lines.takeLast(50).forEach { line ->
            item {
                Text(
                    line,
                    style = MaterialTheme.typography.caption3,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
    }
}

@Composable
private fun WatchList(content: androidx.wear.compose.foundation.lazy.ScalingLazyListScope.() -> Unit) {
    val state = rememberScalingLazyListState()
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    ScalingLazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focus)
            .rotaryScrollable(RotaryScrollableDefaults.behavior(scrollableState = state), focusRequester = focus),
        state = state,
        autoCentering = AutoCenteringParams(itemIndex = 0),
        content = content,
    )
}

private fun statusLine(snap: Snapshot): String {
    return when (snap.phase) {
        "connected" -> "Прокси ${snap.proxyEndpoint}" + if (snap.selected.isEmpty()) "" else " · ${snap.selected}"
        "connecting" -> "Подключение…"
        "stopping" -> "Отключение…"
        "updating" -> "Обновление подписки…"
        "testing" -> "Замер задержки…"
        "error" -> "Ошибка"
        else -> if (snap.servers.isEmpty()) "Нет подписки" else "Отключено"
    }
}

private fun statusColor(snap: Snapshot): Color {
    return when (snap.phase) {
        "connected" -> Color(0xFF30D158)
        "error" -> Color(0xFFFF453A)
        "connecting", "updating", "testing" -> Color(0xFFFFD60A)
        else -> Color(0xFF8E8E93)
    }
}
