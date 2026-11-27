package africa.myrada.audio.ui

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.myrada.audio.engine.RadaEngine
import africa.myrada.audio.engine.ShizukuBridge
import kotlinx.coroutines.launch

enum class SheetKind { Advanced, Apps, Engine }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadaSheet(kind: SheetKind, st: RadaEngine.Status, sh: ShizukuBridge.Info, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Pal.bg,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            when (kind) {
                SheetKind.Advanced -> AdvancedSetup(st, sh, onDismiss)
                SheetKind.Apps -> AppsList(st)
                SheetKind.Engine -> EngineDetails(st, sh)
            }
        }
    }
}

@Composable
private fun Step(done: Boolean, n: Int, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Box(
            Modifier.size(32.dp).clip(CircleShape).background(if (done) Pal.pri else Color.Transparent)
                .border(1.5.dp, Pal.pri, CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(if (done) "✓" else "$n", color = if (done) Pal.onPri else Pal.black, fontWeight = FontWeight.Bold, fontSize = 13.sp) }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Pal.black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(body, color = Pal.muted2, fontSize = 12.5.sp, lineHeight = 18.sp)
        }
    }
}

@Composable
private fun AdvancedSetup(st: RadaEngine.Status, sh: ShizukuBridge.Info, onDismiss: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    val installed = sh.state != ShizukuBridge.State.NOT_INSTALLED
    val running = installed && sh.state != ShizukuBridge.State.NOT_RUNNING
    val permitted = running && sh.state != ShizukuBridge.State.NEEDS_PERMISSION
    val ready = sh.state == ShizukuBridge.State.READY

    Eyebrow("No root needed", Red)
    Text("Advanced mode", color = Pal.black, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp, modifier = Modifier.padding(top = 4.dp))
    Note("Runs other apps' sound through Rada's own engine, so crossfeed, HRTF spatial, convolution and true mono reach them too.", Pal.muted2)
    Step(running, 1, "Install and start Shizuku", "From Google Play or GitHub, started with Wireless debugging. Start it again after a reboot.")
    Step(permitted, 2, "Allow Rada in Shizuku", "Shizuku asks you once.")
    Step(ready, 3, "Grant capture permissions", "Rada uses Shizuku to let itself see which apps are playing and capture their sound without asking each time.")
    Step(false, 4, "Android 15 and later", "Turn on \"Disable screen share protection\" in Developer options so notifications stay visible.")
    Box(Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(TileRadius)).background(Pal.card)
        .border(1.dp, Pal.line, RoundedCornerShape(TileRadius)).padding(16.dp)) {
        Text("Good to know: Spotify, Chrome and SoundCloud block capture, so they stay on Standard with full EQ. Advanced adds delay, so video apps stay on Standard.",
            color = Pal.muted2, fontSize = 12.5.sp, lineHeight = 18.sp)
    }
    sh.error?.let { Note(it, Red) }
    Spacer(Modifier.height(12.dp))
    when {
        !installed -> CtaButton("Get Shizuku") { openShizukuStore(ctx) }
        !running -> CtaButton("Open Shizuku") {
            ctx.packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")?.let { ctx.startActivity(it) }
        }
        !permitted -> CtaButton("Allow Rada in Shizuku") { ShizukuBridge.requestPermission(ctx) }
        !ready -> CtaButton(if (busy) "Granting…" else "Grant capture permissions") {
            if (!busy) { busy = true; scope.launch { ShizukuBridge.grant(ctx); busy = false } }
        }
        st.advanced -> CtaButton("Turn off Advanced") { RadaEngine.setAdvanced(false); onDismiss() }
        else -> CtaButton("Turn on Advanced") { RadaEngine.setAdvanced(true); onDismiss() }
    }
    GhostButton("Check again", Modifier.padding(top = 10.dp)) { ShizukuBridge.refresh(ctx) }
}

private fun openShizukuStore(ctx: Context) {
    val market = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=moe.shizuku.privileged.api"))
    runCatching { ctx.startActivity(market) }.onFailure {
        ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/RikkaApps/Shizuku/releases")))
    }
}

private val BLOCKED = setOf("com.spotify.music", "com.android.chrome", "com.soundcloud.android")
private val VIDEO = setOf("com.google.android.youtube", "com.netflix.mediaclient", "com.amazon.avod.thirdpartyclient", "com.disney.disneyplus")

@Composable
private fun AppsList(st: RadaEngine.Status) {
    val pm = LocalContext.current.packageManager
    Text("Apps and engines", color = Pal.black, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp)
    Note("Fills in as apps announce their audio. Each app gets the best engine it allows.")
    if (st.apps.isEmpty()) Note("Nothing yet. Play something in a music app.")
    st.apps.forEach { pkg ->
        val label = runCatching { pm.getApplicationLabel(pm.getApplicationInfo(pkg, PackageManager.GET_META_DATA)).toString() }.getOrDefault(pkg)
        val (engine, kind) = when {
            pkg in BLOCKED -> "Standard · blocks capture" to TagKind.Limit
            pkg in VIDEO -> "Standard · video" to TagKind.Limit
            st.advanced -> "Advanced" to TagKind.Advanced
            else -> "Standard" to TagKind.Normal
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(if (kind == TagKind.Advanced) Pal.pri else Pal.powder2),
                contentAlignment = Alignment.Center) { Text(label.take(1).uppercase(), color = if (kind == TagKind.Advanced) Pal.onPri else Pal.black, fontWeight = FontWeight.ExtraBold) }
            Spacer(Modifier.width(12.dp))
            Text(label, color = Pal.black, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, modifier = Modifier.weight(1f))
            Tag(engine, kind)
        }
    }
}

@Composable
private fun EngineDetails(st: RadaEngine.Status, sh: ShizukuBridge.Info) {
    Text("Engine details", color = Pal.black, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp)
    Spacer(Modifier.height(14.dp))
    val path = listOf("Any app" to "Spotify, YouTube, games", "Android mixer" to "All app sound is combined",
        "Rada effect" to "Preamp · EQ · compressor · limiter", "Codec encode" to "Bluetooth only", "Your headphones" to "Decode and DAC")
    path.forEachIndexed { i, (t, d) ->
        Row {
            Column(Modifier.width(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Box(Modifier.size(12.dp).clip(CircleShape).background(if (i == 2) Red else Color.Transparent).border(2.dp, if (i == 2) Red else Pal.black, CircleShape))
                if (i < path.lastIndex) Box(Modifier.width(1.5.dp).height(30.dp).background(Pal.line))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.padding(bottom = 10.dp)) {
                Text(t, color = Pal.black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(d, color = Pal.muted2, fontSize = 12.5.sp)
            }
        }
    }
    val rows = listOf(
        "System-wide effect (session 0)" to when (st.reach) { RadaEngine.Reach.SYSTEM_WIDE -> "Accepted"; RadaEngine.Reach.PER_APP -> "Refused · per app"; else -> "Engine off" },
        "Added delay, Standard" to "≈0 ms",
        "Virtual surround" to when (st.virtualizerAccepted) { true -> "Accepted"; false -> "Ignored"; null -> "Off" },
        "Apps with reverb" to "${st.reverbApps}",
        "Shizuku" to when (sh.state) {
            ShizukuBridge.State.READY -> "Ready"; ShizukuBridge.State.NEEDS_GRANTS -> "Needs grants"; ShizukuBridge.State.NEEDS_PERMISSION -> "Needs permission"
            ShizukuBridge.State.NOT_RUNNING -> "Not running"; ShizukuBridge.State.NOT_INSTALLED -> "Not installed"
        },
    )
    rows.forEach { (k, v) ->
        Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(k, color = Pal.muted2, fontSize = 13.sp, modifier = Modifier.weight(1f))
            Text(v, color = Pal.black, fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }
    }
}
