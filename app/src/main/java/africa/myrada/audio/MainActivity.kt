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
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.runtime.DisposableEffect
import africa.myrada.audio.engine.EngineService
import africa.myrada.audio.engine.RadaEngine
import africa.myrada.audio.engine.ShizukuBridge
import africa.myrada.audio.ui.CopperBrush
import africa.myrada.audio.ui.DevicesScreen
import africa.myrada.audio.ui.EqScreen
import africa.myrada.audio.ui.HomeScreen
import africa.myrada.audio.ui.RadaColors
import africa.myrada.audio.ui.RadaSheet
import africa.myrada.audio.ui.RadaTheme
import africa.myrada.audio.ui.SheetKind
import africa.myrada.audio.ui.SpaceScreen
import africa.myrada.audio.ui.StudioScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        ShizukuBridge.start(applicationContext)
        setContent { RadaTheme { App() } }
    }

    private data class Tab(val label: String, val icon: ImageVector)

    @Composable
    private fun App() {
        val settings by RadaEngine.settings.collectAsState()
        val status by RadaEngine.status.collectAsState()
        val shizuku by ShizukuBridge.info.collectAsState()
        var tab by rememberSaveable { mutableIntStateOf(0) }
        var sheet by remember { mutableStateOf<SheetKind?>(null) }

        val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            EngineService.start(this@MainActivity)
        }
        // Coming back from Shizuku: re-check its state.
        val owner = LocalLifecycleOwner.current
        DisposableEffect(owner) {
            val obs = LifecycleEventObserver { _, e -> if (e == Lifecycle.Event.ON_RESUME) ShizukuBridge.refresh(applicationContext) }
            owner.lifecycle.addObserver(obs)
            onDispose { owner.lifecycle.removeObserver(obs) }
        }

        val tabs = remember {
            listOf(Tab("Home", Icons.Filled.Home), Tab("EQ", Icons.Filled.Menu), Tab("Space", Icons.Filled.Star),
                Tab("Devices", Icons.Filled.Phone), Tab("Studio", Icons.Filled.Build))
        }
        val onEngine: (String) -> Unit = { pick ->
            if (pick == "Advanced" && !status.advanced) sheet = SheetKind.Advanced
            else if (pick == "Standard" && status.advanced) RadaEngine.setAdvanced(false)
        }

        BoxWithConstraints(Modifier.fillMaxSize().background(RadaColors.Bg)) {
            // Phone < 600dp · foldable inner screen 600–999dp · tablet 1000dp+
            val columns = when { maxWidth < 600.dp -> 1; maxWidth < 1000.dp -> 2; else -> 3 }
            val sideNav = columns == 3

            Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding()) {
                TopBar(status.running, onInfo = { sheet = SheetKind.Engine }) { on ->
                    if (on) EngineService.start(this@MainActivity) else EngineService.stop(this@MainActivity)
                }
                Row(Modifier.weight(1f)) {
                    if (sideNav) SideNav(tabs, tab) { tab = it }
                    Box(Modifier.weight(1f).fillMaxHeight()) {
                        val setup = { sheet = SheetKind.Advanced }
                        when (tab) {
                            0 -> HomeScreen(columns, settings, status, onEngine, onApps = { sheet = SheetKind.Apps }, openEq = { tab = 1 })
                            1 -> EqScreen(columns, settings)
                            2 -> SpaceScreen(columns, settings, status, setup)
                            3 -> DevicesScreen(columns, settings, status)
                            else -> StudioScreen(columns, settings, status, BuildConfig.VERSION_NAME, setup) { copyProfile() }
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
        cm.setPrimaryClip(ClipData.newPlainText("RADA profile", RadaEngine.settings.value.toJson()))
        Toast.makeText(this, "Profile copied", Toast.LENGTH_SHORT).show()
    }

    @Composable
    private fun TopBar(on: Boolean, onInfo: () -> Unit, onPower: (Boolean) -> Unit) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(painterResource(R.drawable.rada_logo), contentDescription = "RADA audio", modifier = Modifier.height(46.dp))
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.size(40.dp).clip(CircleShape).border(1.dp, RadaColors.Line, CircleShape).clickable { onInfo() },
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Info, contentDescription = "Engine details", tint = RadaColors.Muted, modifier = Modifier.size(18.dp)) }
            Spacer(Modifier.width(8.dp))
            Row(
                Modifier.height(40.dp).clip(RoundedCornerShape(50))
                    .background(if (on) CopperBrush else androidx.compose.ui.graphics.SolidColor(RadaColors.Panel2))
                    .border(1.dp, if (on) Color(0xFFE9AB76) else RadaColors.Line, RoundedCornerShape(50))
                    .clickable { onPower(!on) }
                    .padding(start = 14.dp, end = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(if (on) "ON" else "OFF", color = if (on) RadaColors.OnCopper else RadaColors.Muted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(Modifier.width(8.dp))
                Box(Modifier.size(width = 30.dp, height = 22.dp).clip(RoundedCornerShape(50))
                    .background(if (on) Color(0x552A211A) else Color(0xFF393430)).padding(3.dp),
                    contentAlignment = if (on) Alignment.CenterEnd else Alignment.CenterStart) {
                    Box(Modifier.size(16.dp).clip(CircleShape).background(if (on) Color(0xFF2A211A) else Color(0xFFAAA29A)))
                }
            }
        }
        Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFF2A2622)))
    }

    @Composable
    private fun BottomNav(tabs: List<Tab>, current: Int, wide: Boolean, onPick: (Int) -> Unit) {
        Box(Modifier.fillMaxWidth().height(1.dp).background(RadaColors.Line))
        Row(
            Modifier.fillMaxWidth().background(Color(0xFF171615))
                .padding(horizontal = if (wide) 80.dp else 2.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            tabs.forEachIndexed { i, t ->
                val on = i == current
                Column(
                    Modifier.weight(1f).clip(RoundedCornerShape(12.dp)).clickable { onPick(i) }.padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(Modifier.size(width = 56.dp, height = 30.dp).clip(RoundedCornerShape(15.dp))
                        .background(if (on) Color(0xFF33271E) else Color.Transparent), contentAlignment = Alignment.Center) {
                        Icon(t.icon, contentDescription = null, tint = if (on) RadaColors.Copper else RadaColors.Muted, modifier = Modifier.size(20.dp))
                    }
                    Text(t.label, fontSize = 11.sp, color = if (on) RadaColors.Copper else RadaColors.Muted)
                }
            }
        }
    }

    @Composable
    private fun SideNav(tabs: List<Tab>, current: Int, onPick: (Int) -> Unit) {
        Row(Modifier.fillMaxHeight()) {
            Column(Modifier.width(220.dp).fillMaxHeight().padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                tabs.forEachIndexed { i, t ->
                    val on = i == current
                    Row(
                        Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(14.dp))
                            .background(if (on) Color(0xFF33271E) else Color.Transparent)
                            .clickable { onPick(i) }.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(t.icon, contentDescription = null, tint = if (on) RadaColors.Copper else RadaColors.Muted, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(12.dp))
                        Text(t.label, fontSize = 14.sp, color = if (on) RadaColors.Copper else RadaColors.Muted)
                    }
                }
            }
            Box(Modifier.width(1.dp).fillMaxHeight().background(Color(0xFF2A2622)))
        }
    }
}
