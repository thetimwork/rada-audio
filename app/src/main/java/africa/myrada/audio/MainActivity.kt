package africa.myrada.audio

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import africa.myrada.audio.engine.EngineService
import africa.myrada.audio.engine.RadaEngine
import africa.myrada.audio.ui.DevicesScreen
import africa.myrada.audio.ui.EqScreen
import africa.myrada.audio.ui.HomeScreen
import africa.myrada.audio.ui.RadaColors
import africa.myrada.audio.ui.RadaTheme
import africa.myrada.audio.ui.SpaceScreen
import africa.myrada.audio.ui.StudioScreen

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val shizuku = isInstalled("moe.shizuku.privileged.api")
        setContent { RadaTheme { App(shizuku) } }
    }

    private fun isInstalled(pkg: String): Boolean = try {
        @Suppress("DEPRECATION")
        packageManager.getPackageInfo(pkg, 0); true
    } catch (e: PackageManager.NameNotFoundException) {
        false
    }

    @Composable
    private fun App(shizuku: Boolean) {
        val settings by RadaEngine.settings.collectAsState()
        val status by RadaEngine.status.collectAsState()
        var tab by rememberSaveable { mutableIntStateOf(0) }

        val notifPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
        LaunchedEffect(Unit) {
            if (Build.VERSION.SDK_INT >= 33 &&
                ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
            ) notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            EngineService.start(this@MainActivity) // engine on by default
        }

        val tabs = remember {
            listOf("Home" to Icons.Filled.Home, "EQ" to Icons.Filled.List, "Space" to Icons.Filled.Star,
                "Devices" to Icons.Filled.Phone, "Studio" to Icons.Filled.Build)
        }

        Scaffold(
            containerColor = RadaColors.Bg,
            topBar = {
                Row(Modifier.padding(start = 20.dp, top = 18.dp, bottom = 4.dp)) {
                    Text("RADA", fontSize = 26.sp, fontWeight = FontWeight.Black, color = RadaColors.Text)
                    Text(".", fontSize = 26.sp, fontWeight = FontWeight.Black, color = RadaColors.Red)
                    Text(" audio", fontSize = 18.sp, color = RadaColors.Copper, modifier = Modifier.padding(top = 7.dp))
                }
            },
            bottomBar = {
                NavigationBar(containerColor = androidx.compose.ui.graphics.Color(0xFF171615)) {
                    tabs.forEachIndexed { i, (label, icon) ->
                        NavigationBarItem(
                            selected = tab == i, onClick = { tab = i },
                            icon = { Icon(icon, contentDescription = label) },
                            label = { Text(label) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = RadaColors.Copper, selectedTextColor = RadaColors.Copper,
                                indicatorColor = androidx.compose.ui.graphics.Color(0xFF33271E),
                                unselectedIconColor = RadaColors.Muted, unselectedTextColor = RadaColors.Muted,
                            ),
                        )
                    }
                }
            },
        ) { padding ->
            Box(Modifier.padding(padding)) {
                when (tab) {
                    0 -> HomeScreen(settings, status) { on ->
                        if (on) EngineService.start(this@MainActivity) else EngineService.stop(this@MainActivity)
                    }
                    1 -> EqScreen(settings)
                    2 -> SpaceScreen(shizuku)
                    3 -> DevicesScreen(status)
                    else -> StudioScreen(settings, status, BuildConfig.VERSION_NAME)
                }
            }
        }
    }
}
