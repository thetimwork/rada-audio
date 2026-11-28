package africa.myrada.audio.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.myrada.audio.engine.DeviceTypes
import africa.myrada.audio.engine.OutputInfo
import africa.myrada.audio.engine.Presets
import africa.myrada.audio.engine.RadaEngine
import africa.myrada.audio.engine.Settings
import kotlin.math.abs
import kotlin.math.roundToInt

// ---------- layout ---------------------------------------------------------------------------

/** Space between cards: wider on the foldable so nothing sits on the hinge. */
fun gapFor(columns: Int): Dp = if (columns == 2) 48.dp else 16.dp
val RowGap = 14.dp

/** A screen: heading, then the screen's own layout for 1, 2 or 3 columns. */
@Composable
fun Page(columns: Int, eyebrow: String, title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        Modifier.verticalScroll(rememberScrollState())
            .padding(horizontal = if (columns == 1) 18.dp else 28.dp)
            .padding(top = 8.dp, bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(RowGap),
    ) {
        Heading(eyebrow, title, big = columns > 1)
        content()
    }
}

/** A row of cards that all take the height of the tallest. */
@Composable
fun EqualRow(columns: Int, content: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit) {
    Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(gapFor(columns)), content = content)
}

/** A column of cards inside an equal row; the last card stretches to fill. */
@Composable
fun androidx.compose.foundation.layout.RowScope.Stack(weight: Float = 1f, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.weight(weight).fillMaxHeight(), verticalArrangement = Arrangement.spacedBy(RowGap), content = content)
}

/** Which picture and which effects: "spk", "head", "bud", "dac" or "phone" (worked out in DeviceTypes). */
fun artKind(o: OutputInfo): String = o.kind

private fun db(v: Float) = (if (v > 0) "+" else "") + (if (v % 1f == 0f) v.roundToInt().toString() else "%.1f".format(v)) + " dB"

// ---------- shared cards ---------------------------------------------------------------------

/** A status on the device card that opens where you change it. */
@Composable
private fun SpecChip(icon: String, text: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(50)
    Row(
        Modifier.clip(shape).border(1.dp, Pal.line, shape).clickable { onClick() }.padding(start = 10.dp, end = 12.dp, top = 7.dp, bottom = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StrokeIcon(icon, Pal.muted2, 14.dp)
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.5.sp, color = Pal.black, fontWeight = FontWeight.SemiBold, maxLines = 1)
        Spacer(Modifier.width(4.dp))
        StrokeIcon("M9 6l6 6-6 6", Pal.muted, 12.dp)
    }
}

@Composable
private fun Spec(icon: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        StrokeIcon(icon, Pal.muted2, 15.dp)
        Spacer(Modifier.width(6.dp))
        Text(text, fontSize = 12.5.sp, color = Pal.muted2, maxLines = 1)
    }
}

const val IC_ENGINE = "M4 7h10M18 7h2M4 17h4M12 17h8M14 7a2 2 0 1 0 4 0a2 2 0 1 0-4 0M8 17a2 2 0 1 0 4 0a2 2 0 1 0-4 0"
const val IC_APPS = "M6 4h2a2 2 0 0 1 2 2v2a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM16 4h2a2 2 0 0 1 2 2v2a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zM6 14h2a2 2 0 0 1 2 2v2a2 2 0 0 1-2 2H6a2 2 0 0 1-2-2v-2a2 2 0 0 1 2-2zM16 14h2a2 2 0 0 1 2 2v2a2 2 0 0 1-2 2h-2a2 2 0 0 1-2-2v-2a2 2 0 0 1 2-2z"
const val IC_BT = "M7 7l10 10-5 4V3l5 4L7 17"
const val IC_WIRE = "M12 3v8M8 11h8v4a4 4 0 0 1-8 0zM12 19v2"
const val IC_ARROW = "M5 12h14M13 6l6 6-6 6"

/** The device card: black in light mode, warm white in dark. */
/** Label for the type pill on the device stage. */
private fun typeLabel(o: OutputInfo) = when (o.kind) {
    "spk" -> "Speaker"; "bud" -> "Earbuds"; "dac" -> "USB DAC"; "phone" -> "This phone"; else -> "Headphones"
}

@Composable
private fun DeviceHero(modifier: Modifier, eyebrow: String, tag: String, o: OutputInfo, specs: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit, footer: @Composable ColumnScope.() -> Unit) {
    // The contrast card: black in light mode, warm white in dark mode. The device gets its own stage on top.
    Inverse {
        val shape = RoundedCornerShape(CardRadius)
        Column(modifier.fillMaxWidth().clip(shape).background(Pal.bg).padding(10.dp)) {
            DeviceStage(artKind(o), o.name, typeLabel(o), tag, o.id ?: o.key)
            Column(Modifier.weight(1f).padding(start = 10.dp, end = 10.dp, top = 14.dp, bottom = 10.dp)) {
                Eyebrow(eyebrow, Pal.muted)
                Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.Bottom) {
                    Text(o.name, fontSize = 28.sp, fontWeight = FontWeight.ExtraBold, color = Pal.black, letterSpacing = (-1).sp, lineHeight = 30.sp,
                        modifier = Modifier.weight(1f))
                    if (o.id != null) Text("Change", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Pal.black,
                        textDecoration = androidx.compose.ui.text.style.TextDecoration.Underline,
                        modifier = Modifier.padding(start = 12.dp, bottom = 4.dp).clickable { DeviceEditor.open = true })
                }
                val sub = when {
                    o.bluetooth && o.btName != null && o.btName != o.name -> "Bluetooth name: ${o.btName}"
                    o.bluetooth -> "Bluetooth"
                    o.kind == "phone" -> "Playing from the phone"
                    o.kind == "dac" -> "USB"
                    else -> "Wired"
                }
                Text(sub, fontSize = 13.sp, color = Pal.muted, modifier = Modifier.padding(top = 4.dp))
                Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) { specs() }
                footer()
            }
        }
    }
}

// ---------- Home ------------------------------------------------------------------------------

@Composable
fun HomeScreen(columns: Int, s: Settings, st: RadaEngine.Status, onEngine: (String) -> Unit, onApps: () -> Unit, openEq: () -> Unit,
               onEngineTap: () -> Unit = {}, onReachTap: () -> Unit = {}) {
    val hero: @Composable (Modifier) -> Unit = { m ->
        DeviceHero(m, "Now shaping", st.output.mode, st.output, specs = {
            // Engine in use (tap: set up or see Advanced) and how far the EQ reaches (tap: details)
            SpecChip(IC_ENGINE, when { st.capture -> "Advanced"; st.advanced -> "Advanced · paused"; else -> "Standard" }) { onEngineTap() }
            SpecChip(IC_APPS, when (st.reach) { RadaEngine.Reach.SYSTEM_WIDE -> "EQ: all apps"; RadaEngine.Reach.PER_APP -> "EQ: some apps"; else -> "Off" }) { onReachTap() }
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.Bottom) {
                Text("≈0", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Pal.black, letterSpacing = (-0.6).sp)
                Text(" ms delay", fontSize = 12.sp, color = Pal.black, modifier = Modifier.padding(bottom = 3.dp))
            }
        }) {
            Spacer(Modifier.weight(1f).height(18.dp))
            CompareButton(st.bypassed) { RadaEngine.setBypass(it) }
            st.message?.let { Note(it, Pal.muted2) }
        }
    }
    val engine: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "Engine") {
            Segmented(listOf("Standard", "Advanced"), if (st.advanced) "Advanced" else "Standard") { onEngine(it) }
            Note(when {
                st.capture -> "Advanced is processing " + (st.capturedApps.takeIf { it.isNotEmpty() }?.joinToString() ?: "apps as they play") + ". Apps that block capture stay on Standard."
                st.advanced -> st.captureMessage ?: "Advanced is on. It starts when Android hands over audio capture."
                else -> "Works on every phone. EQ, correction and dynamics through Android's own effect chain. Rooms and surround reach every app only in Advanced."
            })
            if (st.advanced) GhostButton("See apps", Modifier.padding(top = 12.dp)) { onApps() }
        }
    }
    val sig: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "Sound signature") { Chips(Presets.names, s.preset) { n -> RadaEngine.update { Presets.apply(n, it) } } }
    }
    val quick: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "Quick controls") {
            Spacer(Modifier.weight(1f, fill = false))
            DialRow {
                Dial("Bass", s.bass) { v -> RadaEngine.update { it.copy(bass = v.roundToInt().toFloat(), preset = "Custom") } }
                Dial("Clarity", s.clarity) { v -> RadaEngine.update { it.copy(clarity = v.roundToInt().toFloat(), preset = "Custom") } }
                Dial("Width", s.width) { v -> RadaEngine.update { it.copy(width = v.roundToInt().toFloat()) } }
            }
            Spacer(Modifier.weight(1f, fill = false))
            Note(if (st.advanced) "Drag a dial up or down. Width is full in Advanced mode." else "Drag a dial up or down. Width is deliberately subtle in Standard mode.")
        }
    }
    val profiles: @Composable (Modifier) -> Unit = { m -> ProfilesPanel(s, st, compact = true, modifier = m) }
    val eq: @Composable () -> Unit = {
        Panel(title = "Equaliser", trailing = { Tag(s.preset) }) {
            EqChart(List(10) { s.bandGain(it) }, Settings.LABELS, if (columns == 3) 190.dp else 170.dp)
            Row(Modifier.fillMaxWidth().padding(top = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Preamp ${db(s.preamp)} · limiter ${if (s.limiter) "on" else "off"}", fontSize = 12.5.sp, color = Pal.muted2, modifier = Modifier.weight(1f))
                GhostButton("Open equaliser") { openEq() }
            }
        }
    }
    Page(columns, "Rada Sense", "Your sound") {
        when (columns) {
            1 -> { hero(Modifier); engine(Modifier); sig(Modifier); profiles(Modifier); quick(Modifier); eq() }
            2 -> {
                EqualRow(columns) {
                    hero(Modifier.weight(1f).fillMaxHeight())
                    Stack { engine(Modifier); sig(Modifier.weight(1f)) }
                }
                EqualRow(columns) { quick(Modifier.weight(1f).fillMaxHeight()); profiles(Modifier.weight(1f).fillMaxHeight()) }
                eq()
            }
            else -> {
                EqualRow(columns) {
                    hero(Modifier.weight(1f).fillMaxHeight())
                    Stack { engine(Modifier); sig(Modifier.weight(1f)) }
                    Stack { quick(Modifier); profiles(Modifier.weight(1f)) }
                }
                eq()
            }
        }
    }
}

// ---------- EQ --------------------------------------------------------------------------------

@Composable
fun EqScreen(columns: Int, s: Settings) {
    val graphic: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "Graphic EQ", trailing = { Tag("10 bands") }) {
            FaderBank(s.bands, Settings.LABELS) { i, v ->
                RadaEngine.update { cur -> cur.copy(bands = cur.bands.toMutableList().also { it[i] = v }, preset = "Custom") }
            }
            Spacer(Modifier.height(20.dp)); Divider(); Spacer(Modifier.height(18.dp))
            Chips(Presets.names, s.preset) { n -> RadaEngine.update { Presets.apply(n, it) } }
        }
    }
    val tone: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "Tone and gain") {
            SliderRow("Preamp", s.preamp, -18f..6f, db(s.preamp), 23) { v -> RadaEngine.update { it.copy(preamp = v.roundToInt().toFloat()) } }
            SliderRow("Treble", s.treble, -12f..12f, db(s.treble), 23) { v -> RadaEngine.update { it.copy(treble = v.roundToInt().toFloat(), preset = "Custom") } }
            val bal = s.balance.roundToInt()
            SliderRow("Balance", s.balance, -100f..100f, if (bal == 0) "Centre" else (if (bal < 0) "L " else "R ") + abs(bal)) { v -> RadaEngine.update { it.copy(balance = v.roundToInt().toFloat()) } }
            Note("Preamp lowers the level before boosts, so the limiter rarely has to work.")
        }
    }
    val headroom: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "Headroom") {
            SettingRow("Peak limiter", "Stops clipping when the EQ adds gain", s.limiter, tag = "All apps", tagKind = TagKind.Normal) { RadaEngine.update { it.copy(limiter = !it.limiter) } }
            GhostButton("Flatten EQ", Modifier.padding(top = 6.dp)) { RadaEngine.update { Presets.apply("Reference", it) } }
        }
    }
    Page(columns, "Precision", "Equaliser") {
        when (columns) {
            1 -> { graphic(Modifier); tone(Modifier); headroom(Modifier) }
            2 -> { graphic(Modifier); EqualRow(columns) { tone(Modifier.weight(1f).fillMaxHeight()); headroom(Modifier.weight(1f).fillMaxHeight()) } }
            else -> EqualRow(columns) {
                graphic(Modifier.weight(2f).fillMaxHeight())
                Stack { tone(Modifier); headroom(Modifier.weight(1f)) }
            }
        }
    }
}

// ---------- Space -----------------------------------------------------------------------------

@Composable
fun SpaceScreen(columns: Int, s: Settings, st: RadaEngine.Status, onSetup: () -> Unit) {
    val onSpeaker = DeviceTypes.isSpeaker(st.output.kind)
    val room: @Composable (Modifier) -> Unit = { m ->
        AlwaysLight {
            val reach = when {
                st.capture -> "All captured apps"
                st.reverbApps > 0 -> "${st.reverbApps} app" + (if (st.reverbApps > 1) "s" else "")
                else -> "No app yet"
            }
            Panel(m, title = "Room", trailing = { Tag(reach, if (st.capture || st.reverbApps > 0) TagKind.Live else TagKind.Limit) }) {
                VinylRadar(s.roomSize, s.reverb && st.running, s.room)
                Spacer(Modifier.height(8.dp))
                // Picking a room switches the reverb on: a room you can't hear is no use.
                Chips(Presets.rooms, s.room) { r -> RadaEngine.update { it.copy(room = r, reverb = true) } }
                SliderRow("Room size", s.roomSize, 0f..100f, "${s.roomSize.roundToInt()}%") { v -> RadaEngine.update { it.copy(roomSize = v.roundToInt().toFloat(), reverb = true) } }
                Note(when {
                    !s.reverb -> "Room reverb is off. Pick a room to turn it on."
                    st.capture -> "Rada's own reverb is on every captured app. Room size sets how big the space sounds and how long it rings."
                    st.reverbApps > 0 -> "Reaching apps that share their audio with sound apps. YouTube and most video apps don't, so they need Advanced."
                    else -> "No app is sharing its audio yet. Spotify and most music players do; YouTube and most video apps don't, so in Standard mode rooms can't reach them. Advanced mode can."
                })
                if (!st.capture) GhostButton(if (st.advanced) "Advanced status" else "Set up Advanced", Modifier.padding(top = 12.dp)) { onSetup() }
            }
        }
    }
    val std: @Composable (Modifier) -> Unit = { m ->
        Panel(m) {
            SettingRow("Room reverb", if (st.capture) "Rada's own reverb on captured apps" else "Only apps that share their audio", s.reverb,
                tag = if (st.capture) "Advanced" else "Some apps", tagKind = if (st.capture) TagKind.Advanced else TagKind.Limit) { RadaEngine.update { it.copy(reverb = !it.reverb) } }
            Divider()
            SettingRow("Virtual surround",
                if (st.capture) "Rada speaker simulation: two speakers in front of you, in your headphones"
                else when (st.virtualizerAccepted) { true -> "Android's virtualiser, accepted by this phone"; false -> "This phone ignored Android's virtualiser"; null -> "Android's virtualiser in Standard; Rada's own in Advanced" },
                s.virtualizer, tag = if (st.capture) "Advanced" else "Varies by phone", tagKind = if (st.capture) TagKind.Advanced else TagKind.Limit,
                locked = onSpeaker, lockedText = HEADPHONES_ONLY) { RadaEngine.update { it.copy(virtualizer = !it.virtualizer) } }
            SliderRow("Stereo width", s.width, 0f..100f, "${s.width.roundToInt()}%") { v -> RadaEngine.update { it.copy(width = v.roundToInt().toFloat()) } }
            Note("Subtle in Standard mode: Android lets Rada lean frequencies left and right, not mix channels.")
        }
    }
    val adv: @Composable (Modifier) -> Unit = { m ->
        AdvancedPanel(m, "Advanced effects", st, onSetup, listOf(
            Triple("HRTF spatial", "Needs measured head-response data, not in this build", s.spatial) to { RadaEngine.update { it.copy(spatial = !it.spatial) } },
            Triple("Crossfeed", "Speaker-like stereo on headphones", s.crossfeed) to { RadaEngine.update { it.copy(crossfeed = !it.crossfeed) } },
            Triple("True mono", "Left and right in both ears", s.mono) to { RadaEngine.update { it.copy(mono = !it.mono) } },
        ), footer = if (st.advanced) null else "Until then, Android's own Mono audio is in Settings › Accessibility.",
            speakerOff = if (onSpeaker) setOf("Crossfeed") else emptySet())
    }
    Page(columns, "Immersive", "Sound space") {
        when (columns) {
            1 -> { room(Modifier); std(Modifier); adv(Modifier) }
            2 -> EqualRow(columns) { room(Modifier.weight(1f).fillMaxHeight()); Stack { std(Modifier); adv(Modifier.weight(1f)) } }
            else -> EqualRow(columns) { room(Modifier.weight(1f).fillMaxHeight()); std(Modifier.weight(1f).fillMaxHeight()); adv(Modifier.weight(1f).fillMaxHeight()) }
        }
    }
}

/** Shown on headphone-only effects while the output is a speaker. */
private const val HEADPHONES_ONLY = "Headphones only. Off while playing on a speaker."

/** Effects Rada has not built yet; shown so nothing pretends to work. */
private val NOT_BUILT = setOf("HRTF spatial", "Convolution")

@Composable
fun AdvancedPanel(modifier: Modifier, title: String, st: RadaEngine.Status, onSetup: () -> Unit, rows: List<Pair<Triple<String, String, Boolean>, () -> Unit>>, footer: String? = null,
                  speakerOff: Set<String> = emptySet()) {
    val advanced = st.advanced
    Panel(modifier, title = title, trailing = { Tag("Shizuku", TagKind.Advanced) }, contrast = true) {
        rows.forEachIndexed { i, (t, toggle) ->
            if (i > 0) Divider()
            if (t.first in NOT_BUILT) SettingRow(t.first, t.second, false, tag = "Not built yet", locked = true, lockedText = t.second) {}
            else if (t.first in speakerOff) SettingRow(t.first, t.second, t.third, locked = true, lockedText = HEADPHONES_ONLY) {}
            else SettingRow(t.first, t.second, t.third, locked = !advanced) { if (advanced) toggle() else onSetup() }
        }
        when {
            st.capture -> Note("Working now" + (st.capturedApps.takeIf { it.isNotEmpty() }?.let { " on " + it.joinToString() } ?: ", on apps as they play") + ".")
            advanced -> Note(st.captureMessage ?: "Advanced is on. It starts when Android hands over audio capture.")
            else -> CtaButton("Set up Advanced mode", Modifier.padding(top = 14.dp)) { onSetup() }
        }
        footer?.let { Note(it) }
    }
}

// ---------- Devices ---------------------------------------------------------------------------

@Composable
fun DevicesScreen(columns: Int, s: Settings, st: RadaEngine.Status) {
    val o = st.output
    val hero: @Composable (Modifier) -> Unit = { m ->
        DeviceHero(m, if (st.running) "Connected" else "Engine off", o.mode, o, specs = {
            if (o.bluetooth) Spec(IC_BT, "Bluetooth") else Spec(IC_WIRE, when (artKind(o)) { "dac" -> "USB"; "phone" -> "Built in"; else -> "Wired" })
            Spec(IC_ENGINE, s.preset)
        }) {
            Note(if (o.bluetooth) "Battery and codec readouts are not built yet." else "Direct route: no Bluetooth encoding or hardware controls.")
        }
    }
    val noise: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "Noise control", trailing = { Tag("Manual") }) {
            Segmented(listOf("Off", "ANC", "Transparency"), s.noise) { n -> RadaEngine.update { it.copy(noise = n) } }
            Note("Tell Rada which mode your headphones are in. It does not switch your headphones yet; set the mode on the headphones or their own app.")
        }
    }
    val correction: @Composable (Modifier) -> Unit = { m ->
        Panel(m) {
            SettingRow("Headphone correction", "Measured curve for your model", s.autoEq, tag = "Coming soon", tagKind = TagKind.Limit) { RadaEngine.update { it.copy(autoEq = !it.autoEq) } }
            Text("Model", color = Pal.muted2, fontSize = 12.5.sp, modifier = Modifier.padding(top = 6.dp))
            Box(Modifier.fillMaxWidth().padding(top = 8.dp).height(50.dp).clip(RoundedCornerShape(TileRadius)).background(Pal.bg)
                .border(1.dp, Pal.line, RoundedCornerShape(TileRadius)).padding(horizontal = 16.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(value = o.name, onValueChange = {}, readOnly = true, singleLine = true,
                    textStyle = TextStyle(fontFamily = Figtree, color = Pal.black, fontSize = 15.sp), cursorBrush = SolidColor(Pal.black))
            }
        }
    }
    val outputs: @Composable () -> Unit = {
        Panel(title = "Your outputs") {
            val keys = (RadaEngine.savedOutputs() + o.key).distinct()
            keys.forEach { key ->
                val playing = key == o.key
                val name = if (playing) o.name else key.substringAfter(":").let { if (it == "speaker") "Phone speaker" else if (it == "wired") "Wired headphones" else it }
                OutputRow(name.take(1).uppercase(), name, RadaEngine.profileFor(key).preset, playing)
            }
            Note("Connect headphones or a DAC and Rada switches by itself. Each output remembers its last sound; saved profiles are the ones you keep.")
        }
    }
    Page(columns, "Rada Sense", "Devices") {
        when (columns) {
            1 -> { hero(Modifier); if (o.bluetooth) noise(Modifier); correction(Modifier); ProfilesPanel(s, st); outputs() }
            2 -> {
                EqualRow(columns) {
                    hero(Modifier.weight(1f).fillMaxHeight())
                    Stack { if (o.bluetooth) noise(Modifier); correction(Modifier.weight(1f)) }
                }
                ProfilesPanel(s, st); outputs()
            }
            else -> {
                EqualRow(columns) {
                    hero(Modifier.weight(1f).fillMaxHeight())
                    if (o.bluetooth) noise(Modifier.weight(1f).fillMaxHeight())
                    correction(Modifier.weight(if (o.bluetooth) 1f else 2f).fillMaxHeight())
                }
                ProfilesPanel(s, st); outputs()
            }
        }
    }
}

@Composable
fun OutputRow(letter: String, title: String, sub: String, on: Boolean, onClick: (() -> Unit)? = null, trailing: (@Composable () -> Unit)? = null) {
    val shape = RoundedCornerShape(TileRadius)
    Row(
        Modifier.fillMaxWidth().padding(top = 10.dp).clip(shape)
            .background(if (on) Pal.powder2 else Color.Transparent)
            .border(1.dp, if (on) Color.Transparent else Pal.line, shape)
            .let { if (onClick != null) it.clickable { onClick() } else it }
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(if (on) Pal.pri else Pal.powder2), contentAlignment = Alignment.Center) {
            Text(letter, color = if (on) Pal.onPri else Pal.black, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = Pal.black, fontWeight = FontWeight.Bold, fontSize = 14.5.sp, maxLines = 1)
            Text(sub, color = Pal.muted2, fontSize = 12.sp, maxLines = 1)
        }
        if (trailing != null) trailing()
        else Row(verticalAlignment = Alignment.CenterVertically) {
            if (on) { RedDot(7.dp); Spacer(Modifier.width(6.dp)) }
            Text(if (on) "PLAYING" else "SAVED", fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold, color = if (on) Pal.black else Pal.muted)
        }
    }
}

// ---------- Studio ----------------------------------------------------------------------------

@Composable
fun StudioScreen(columns: Int, s: Settings, st: RadaEngine.Status, version: String, onSetup: () -> Unit, onCopy: () -> Unit) {
    val ctx = LocalContext.current
    val dyn: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "Dynamics") {
            SettingRow("Peak limiter", "Stops clipping when the EQ adds gain", s.limiter) { RadaEngine.update { it.copy(limiter = !it.limiter) } }
            Divider()
            SettingRow("Compressor", "Evens out loud and quiet parts", s.compressor) { RadaEngine.update { it.copy(compressor = !it.compressor) } }
            Note("Android gives the output one compressor, so the Night preset and Compressor share it.")
        }
    }
    val ears: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "Your ears", trailing = { Tag("All apps") }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Left ear" to s.leftEar, "Right ear" to s.rightEar).forEach { (l, v) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(TileRadius)).background(Pal.powder2).padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(l, color = Pal.muted2, fontSize = 13.sp)
                        Text(db(v), color = Pal.black, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.7).sp, maxLines = 1)
                    }
                }
            }
            SliderRow("Left trim", s.leftEar, -12f..12f, db(s.leftEar), 47) { v -> RadaEngine.update { it.copy(leftEar = (v * 2).roundToInt() / 2f) } }
            SliderRow("Right trim", s.rightEar, -12f..12f, db(s.rightEar), 47) { v -> RadaEngine.update { it.copy(rightEar = (v * 2).roundToInt() / 2f) } }
            Note("Set each side until voices sit in the middle. The hearing check comes in a later build.")
        }
    }
    val adv: @Composable (Modifier) -> Unit = { m ->
        AdvancedPanel(m, "Advanced processing", st, onSetup, listOf(
            Triple("Convolution", "Room or headphone impulse responses, not in this build", s.convolution) to { RadaEngine.update { it.copy(convolution = !it.convolution) } },
            Triple("Harmonic warmth", "Gentle valve-style colour", s.warmth) to { RadaEngine.update { it.copy(warmth = !it.warmth) } },
            Triple("Loudness levelling", "Even volume between songs and apps", s.levelling) to { RadaEngine.update { it.copy(levelling = !it.levelling) } },
        ))
    }
    val output: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "This output") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Copy settings") { onCopy() }
                GhostButton("Reset this output") { RadaEngine.resetOutput() }
            }
            Note("Save named profiles on the Home or Devices tab. Profiles hold settings only; Rada never records or uploads audio. Rada Audio $version")
        }
    }
    Page(columns, "Studio", "Processing") {
        when (columns) {
            1 -> { dyn(Modifier); ears(Modifier); adv(Modifier); output(Modifier) }
            2 -> {
                EqualRow(columns) { dyn(Modifier.weight(1f).fillMaxHeight()); ears(Modifier.weight(1f).fillMaxHeight()) }
                EqualRow(columns) { adv(Modifier.weight(1f).fillMaxHeight()); output(Modifier.weight(1f).fillMaxHeight()) }
            }
            else -> {
                EqualRow(columns) { dyn(Modifier.weight(1f).fillMaxHeight()); ears(Modifier.weight(1f).fillMaxHeight()); adv(Modifier.weight(1f).fillMaxHeight()) }
                output(Modifier)
            }
        }
    }
}

// ---------- About -----------------------------------------------------------------------------

private val QR = listOf(
    "1111111001110100001111111",
    "1000001010101100001000001",
    "1011101011011010001011101",
    "1011101011001110001011101",
    "1011101001110100101011101",
    "1000001000111110101000001",
    "1111111010101010101111111",
    "0000000010001000100000000",
    "1000001011111111011001110",
    "1011000010111011000111110",
    "1110101110110101001101011",
    "0010010001111011110101001",
    "1011001011110000101100001",
    "1000000010001101110100010",
    "1011101000100111001111011",
    "1011000011101000001101101",
    "1000011000101101111110100",
    "0000000010100101100010000",
    "1111111001101000101010001",
    "1000001001010011100010000",
    "1011101001010001111110111",
    "1011101001101110011000011",
    "1011101000100111000001101",
    "1000001000001101101110001",
    "1111111010011100101001001"
)

/** QR code for ecpartners.co, always black on white so phone cameras can read it. */
@Composable
private fun QrCode(modifier: Modifier = Modifier) {
    Canvas(modifier.aspectRatio(1f)) {
        val n = QR.size
        val cell = size.width / n
        QR.forEachIndexed { y, row ->
            row.forEachIndexed { x, c ->
                if (c == '1') drawRect(Color(0xFF010A10), Offset(x * cell, y * cell), Size(cell + 0.5f, cell + 0.5f))
            }
        }
    }
}

@Composable
private fun Tick(title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
        Box(Modifier.padding(top = 7.dp, end = 14.dp)) { RedDot(6.dp) }
        Column {
            Text(title, fontSize = 14.5.sp, fontWeight = FontWeight.Bold, color = Pal.black)
            Text(body, fontSize = 12.5.sp, color = Pal.muted2, modifier = Modifier.padding(top = 2.dp))
        }
    }
}

@Composable
private fun Maker(mark: String, name: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(12.dp)).background(Pal.pri), contentAlignment = Alignment.Center) {
            Text(mark, color = Pal.onPri, fontWeight = FontWeight.ExtraBold, fontSize = 12.5.sp, letterSpacing = (-0.3).sp)
        }
        Spacer(Modifier.width(14.dp))
        Text(name, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Pal.black)
    }
}

@Composable
private fun Fact(label: String, value: String, modifier: Modifier) {
    Column(modifier.clip(RoundedCornerShape(TileRadius)).background(Pal.powder2).padding(horizontal = 14.dp, vertical = 12.dp)) {
        Text(label.uppercase(), fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 1.5.sp, color = Pal.muted)
        Text(value, fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Pal.black)
    }
}

@Composable
fun AboutScreen(columns: Int, version: String) {
    val ctx = LocalContext.current
    val open = { runCatching { ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://ecpartners.co/"))) } }
    val what: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "What it does") {
            Text("Rada Audio shapes the sound of every app on your phone. Your apps play, Rada makes them sound better.",
                fontSize = 16.sp, fontWeight = FontWeight.Medium, color = Pal.black, lineHeight = 23.sp, modifier = Modifier.padding(bottom = 4.dp))
            Tick("EQ and sound signatures", "Ten bands, presets and quick dials for bass, clarity and width.")
            Divider()
            Tick("Room and space", "Room reverb, stereo width and virtual surround.")
            Divider()
            Tick("Your gear", "Each output keeps its own sound. Save profiles for your earphones and headphones.")
            Divider()
            Tick("Advanced engine", "Crossfeed, HRTF spatial, convolution and true mono through Shizuku. No root needed.")
            Note("Rada is not a music player. It never records or uploads audio, and your profiles stay on your phone.")
        }
    }
    val made: @Composable (Modifier) -> Unit = { m ->
        Panel(m, title = "Made by") {
            Maker("E&C", "E&C Partners")
            Divider()
            Maker("T180", "T180")
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Fact("Version", version.removeSuffix("-debug"), Modifier.weight(1f))
                Fact("Android", "9 and later", Modifier.weight(1f))
            }
        }
    }
    val qr: @Composable (Modifier) -> Unit = { m ->
        AlwaysLight {
            Panel(m, title = "Visit E&C Partners") {
                Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Box(
                        Modifier.widthIn(max = 220.dp).fillMaxWidth().clip(RoundedCornerShape(TileRadius)).background(Color.White)
                            .border(1.dp, Color(0x1A010A10), RoundedCornerShape(TileRadius)).clickable { open() }.padding(16.dp)
                            .semantics { contentDescription = "QR code for ecpartners.co. Tap to open." }
                    ) { QrCode(Modifier.fillMaxWidth()) }
                }
                Row(Modifier.fillMaxWidth().padding(top = 14.dp).clickable { open() }, horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                    RedDot(7.dp); Spacer(Modifier.width(8.dp))
                    Text("ecpartners.co", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Pal.black)
                }
                Note("Scan with your camera or tap the code.", modifier = Modifier.fillMaxWidth())
            }
        }
    }
    Page(columns, "About", "Rada Audio") {
        when (columns) {
            1 -> { what(Modifier); made(Modifier); qr(Modifier) }
            2 -> { what(Modifier); EqualRow(columns) { made(Modifier.weight(1f).fillMaxHeight()); qr(Modifier.weight(1f).fillMaxHeight()) } }
            else -> EqualRow(columns) { what(Modifier.weight(2f).fillMaxHeight()); Stack { made(Modifier); qr(Modifier.weight(1f)) } }
        }
    }
}
