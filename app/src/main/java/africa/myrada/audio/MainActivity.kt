package africa.myrada.audio

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import africa.myrada.audio.engine.EngineService
import africa.myrada.audio.engine.RadaEngine
import africa.myrada.audio.engine.ShizukuBridge
import africa.myrada.audio.ui.AboutScreen
import africa.myrada.audio.ui.DevicesScreen
import africa.myrada.audio.ui.EqScreen
import africa.myrada.audio.ui.HomeScreen
import africa.myrada.audio.ui.Pal
import africa.myrada.audio.ui.RadaSheet
import africa.myrada.audio.ui.RadaTheme
import africa.myrada.audio.ui.Red
import africa.myrada.audio.ui.SheetKind
import africa.myrada.audio.ui.SpaceScreen
import africa.myrada.audio.ui.StrokeIcon
import africa.myrada.audio.ui.StudioScreen
import africa.myrada.audio.ui.TabIcons
import africa.myrada.audio.ui.ThemeMode
import africa.myrada.audio.ui.ThemePrefs
import africa.myrada.audio.engine.Outputs
import android.media.AudioManager
import android.media.projection.MediaProjectionManager
import android.app.Activity

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ThemePrefs.init(applicationContext)
        ShizukuBridge.start(applicationContext)
        setContent { RadaTheme { App() } }
    }

    private data class Tab(val label: String, val icon: String)

    @Composable
    private fun App() {
        val settings by RadaEngine.settings.collectAsState()
        val status by RadaEngine.status.collectAsState()
        val shizuku by ShizukuBridge.info.collectAsState()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var sheet by remember { mutableStateOf<SheetKind?>(null) }

        // System bars follow the theme.
        val pal = Pal
        SideEffect {
            window.statusBarColor = pal.bg.toArgb()
            window.navigationBarColor = pal.bg.toArgb()
            WindowCompat.getInsetsController(window, window.decorView).apply {
                isAppearanceLightStatusBars = !pal.dark
                isAppearanceLightNavigationBars = !pal.dark
            }
        }

        fun granted(p: String) = ContextCompat.checkSelfPermission(this@MainActivity, p) == PackageManager.PERMISSION_GRANTED
        val refreshOutput = {
            val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
            RadaEngine.onOutputChanged(Outputs.current(this@MainActivity, am))
        }
        // Notifications (engine status) and Bluetooth (the real name of your headphones), asked together.
        val startPermissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refreshOutput() }
        LaunchedEffect(Unit) {
            val ask = buildList {
                if (Build.VERSION.SDK_INT >= 33 && !granted(Manifest.permission.POST_NOTIFICATIONS)) add(Manifest.permission.POST_NOTIFICATIONS)
                if (Build.VERSION.SDK_INT >= 31 && !granted(Manifest.permission.BLUETOOTH_CONNECT)) add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ask.isNotEmpty()) startPermissions.launch(ask.toTypedArray())
            EngineService.start(this@MainActivity)
        }

        // Advanced: Android hands over playback capture through a media-projection grant. With the
        // PROJECT_MEDIA app-op set by Shizuku, Android approves it without showing its prompt.
        val projection = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
            if (r.resultCode == Activity.RESULT_OK && r.data != null) EngineService.startCapture(this@MainActivity, r.resultCode, r.data!!)
            else RadaEngine.onCaptureChanged(false, emptyList(), "Audio capture was not allowed, so Advanced is waiting. Turn it off and on to try again.")
        }
        val recordPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
            if (ok) projection.launch((getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent())
            else RadaEngine.onCaptureChanged(false, emptyList(), "Advanced needs the record permission to receive other apps' sound.")
        }
        var asked by remember { mutableStateOf(false) }
        LaunchedEffect(status.advanced, status.capture, status.running) {
            when {
                status.advanced && !status.capture && status.running && !asked -> {
                    asked = true
                    if (Build.VERSION.SDK_INT < 29) RadaEngine.onCaptureChanged(false, emptyList(), "Advanced needs Android 10 or later.")
                    else if (!granted(Manifest.permission.RECORD_AUDIO)) recordPermission.launch(Manifest.permission.RECORD_AUDIO)
                    else projection.launch((getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager).createScreenCaptureIntent())
                }
                !status.advanced -> { asked = false; if (status.capture) EngineService.stopCapture(this@MainActivity) }
            }
        }
        // Coming back from Shizuku: re-check its state.
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) ShizukuBridge.refresh(applicationContext) }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }

        val tabs = remember {
            listOf(Tab("Home", TabIcons.HOME), Tab("EQ", TabIcons.EQ), Tab("Space", TabIcons.SPACE),
                Tab("Devices", TabIcons.DEVICES), Tab("Studio", TabIcons.STUDIO), Tab("About", TabIcons.ABOUT))
        }
        val onEngine: (String) -> Unit = { pick ->
            if (pick == "Advanced" && !status.advanced) sheet = SheetKind.Advanced
            else if (pick == "Standard" && status.advanced) RadaEngine.setAdvanced(false)
        }

        BoxWithConstraints(Modifier.fillMaxSize().background(Pal.bg)) {
            // Phone < 600dp · foldable inner screen 600–999dp · tablet 1000dp+
            val columns = when { maxWidth < 600.dp -> 1; maxWidth < 1000.dp -> 2; else -> 3 }
            val sideNav = columns == 3

            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                TopBar(status.running, wide = columns > 1, compact = maxWidth < 380.dp, onInfo = { sheet = SheetKind.Engine }) { on ->
                    if (on) EngineService.start(this@MainActivity) else EngineService.stop(this@MainActivity)
                }
                Row(Modifier.weight(1f)) {
                    if (sideNav) SideNav(tabs, tab) { tab = it }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        val setup = { sheet = SheetKind.Advanced }
                        when (tab) {
                            0 -> HomeScreen(columns, settings, status, onEngine, onApps = { sheet = SheetKind.Apps }, openEq = { tab = 1 },
                                onEngineTap = { sheet = if (status.capture) SheetKind.Apps else SheetKind.Advanced },
                                onReachTap = { sheet = SheetKind.Engine })
                            1 -> EqScreen(columns, settings)
                            2 -> SpaceScreen(columns, settings, status, setup)
                            3 -> DevicesScreen(columns, settings, status)
                            4 -> StudioScreen(columns, settings, status, BuildConfig.VERSION_NAME, setup) { copyProfile() }
                            else -> AboutScreen(columns, BuildConfig.VERSION_NAME)
                        }
                    }
                }
                if (!sideNav) BottomNav(tabs, tab, wide = columns == 2) { tab = it }
            }
        }
        sheet?.let { RadaSheet(it, status, shizuku) { sheet = null } }
    }

    private fun copyProfile() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("Rada Audio settings", RadaEngine.settings.value.toJson()))
        Toast.makeText(this, "Settings copied", Toast.LENGTH_SHORT).show()
    }

    /** The Rada wordmark: RADA in Figtree Black, closed with the red dot. Product named alongside. */
    @Composable
    private fun Wordmark(showProduct: Boolean = true) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.semantics { contentDescription = "Rada Audio" }) {
            Row(verticalAlignment = Alignment.Top) {
                Text("RADA", fontSize = 27.sp, fontWeight = FontWeight.Black, color = Pal.black, letterSpacing = (-1.2).sp, lineHeight = 27.sp)
                Box(Modifier.padding(top = 15.dp, start = 1.5.dp).size(5.dp).clip(CircleShape).background(Red))
            }
            if (showProduct) Spacer(Modifier.width(10.dp))
            if (showProduct) Text("AUDIO", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.sp, color = Pal.muted2,
                modifier = Modifier.clip(RoundedCornerShape(50)).border(1.dp, Pal.line, RoundedCornerShape(50)).padding(horizontal = 10.dp, vertical = 5.dp))
        }
    }

    @Composable
    private fun TopBar(on: Boolean, wide: Boolean, compact: Boolean, onInfo: () -> Unit, onPower: (Boolean) -> Unit) {
        val dark = Pal.dark
        Row(
            Modifier.fillMaxWidth().padding(horizontal = if (wide) 28.dp else 20.dp).padding(top = 14.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wordmark(showProduct = !compact)
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Pal.card).border(1.dp, Pal.line, CircleShape).clickable { onInfo() }
                    .semantics { contentDescription = "Engine details" },
                contentAlignment = Alignment.Center,
            ) { StrokeIcon(TabIcons.ABOUT, Pal.black, 18.dp) }
            Spacer(Modifier.width(6.dp))
            // Light or dark, next to the power switch
            Box(
                Modifier.size(42.dp).clip(CircleShape).background(Pal.card).border(1.dp, Pal.line, CircleShape)
                    .clickable { ThemePrefs.set(applicationContext, if (dark) ThemeMode.Light else ThemeMode.Dark) }
                    .semantics { contentDescription = if (dark) "Switch to light mode" else "Switch to dark mode" },
                contentAlignment = Alignment.Center,
            ) {
                StrokeIcon(
                    if (dark) "M12 8a4 4 0 1 0 0 8a4 4 0 1 0 0-8M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4"
                    else "M20 14.5A8 8 0 1 1 9.5 4a6.5 6.5 0 0 0 10.5 10.5z",
                    Pal.black, 18.dp,
                )
            }
            Spacer(Modifier.width(6.dp))
            Row(
                Modifier.height(42.dp).clip(RoundedCornerShape(50))
                    .background(if (on) Pal.pri else Pal.card)
                    .border(1.dp, if (on) Pal.pri else Pal.line, RoundedCornerShape(50))
                    .clickable { onPower(!on) }
                    .semantics { contentDescription = if (on) "Sound engine on" else "Sound engine off" }
                    .padding(start = 14.dp, end = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(if (on) 16.dp else 8.dp), contentAlignment = Alignment.Center) {
                    if (on) Box(Modifier.size(16.dp).clip(CircleShape).background(Red.copy(alpha = 0.22f)))
                    Box(Modifier.size(8.dp).clip(CircleShape).background(if (on) Red else Pal.powder))
                }
                Spacer(Modifier.width(8.dp))
                Text(if (on) "ON" else "OFF", color = if (on) Pal.onPri else Pal.muted2, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.7.sp)
            }
        }
    }

    @Composable
    private fun BottomNav(tabs: List<Tab>, current: Int, wide: Boolean, onPick: (Int) -> Unit) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(Pal.line2))
        Row(
            Modifier.fillMaxWidth().background(Pal.bg).padding(horizontal = if (wide) 96.dp else 6.dp).padding(top = 8.dp, bottom = 10.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            tabs.forEachIndexed { i, t ->
                val on = i == current
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(18.dp)).clickable { onPick(i) }.padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(width = 42.dp, height = 30.dp).clip(RoundedCornerShape(50))
                        .background(if (on) Pal.pri else Color.Transparent), contentAlignment = Alignment.Center) {
                        StrokeIcon(t.icon, if (on) Pal.onPri else Pal.muted, 19.dp)
                    }
                    Text(t.label, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = if (on) Pal.black else Pal.muted, modifier = Modifier.padding(top = 3.dp))
                }
            }
        }
    }

    @Composable
    private fun SideNav(tabs: List<Tab>, current: Int, onPick: (Int) -> Unit) {
        Column(Modifier.width(232.dp).fillMaxHeight().padding(horizontal = 16.dp, vertical = 18.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            tabs.forEachIndexed { i, t ->
                val on = i == current
                Row(
                    Modifier.fillMaxWidth().height(50.dp)
                        .then(if (on) Modifier.shadow(6.dp, RoundedCornerShape(50), ambientColor = Color(0x30010A10), spotColor = Color(0x30010A10)) else Modifier)
                        .clip(RoundedCornerShape(50))
                        .background(if (on) Pal.card else Color.Transparent)
                        .clickable { onPick(i) }.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(if (on) Pal.pri else Color.Transparent), contentAlignment = Alignment.Center) {
                        StrokeIcon(t.icon, if (on) Pal.onPri else Pal.muted, 18.dp)
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(t.label, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = if (on) Pal.black else Pal.muted)
                }
            }
        }
    }
}
