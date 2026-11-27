package africa.myrada.audio.ui

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.myrada.audio.engine.OutputInfo
import africa.myrada.audio.engine.Presets
import africa.myrada.audio.engine.RadaEngine
import africa.myrada.audio.engine.Settings
import kotlin.math.abs
import kotlin.math.roundToInt

/** A panel in the adaptive grid. full = spans every column. */
class Cell(val full: Boolean = false, val content: @Composable () -> Unit)

/**
 * Phone: one column. Foldable: two columns meeting at the hinge. Tablet: three columns.
 * Full-width cells break the flow; the others are dealt across the columns in order.
 */
@Composable
fun AdaptiveGrid(columns: Int, cells: List<Cell>, gap: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        var block = mutableListOf<Cell>()
        val blocks = mutableListOf<List<Cell>>()
        cells.forEach { c ->
            if (c.full) { if (block.isNotEmpty()) blocks.add(block); blocks.add(listOf(c)); block = mutableListOf() } else block.add(c)
        }
        if (block.isNotEmpty()) blocks.add(block)
        blocks.forEach { b ->
            if (b.size == 1 && b[0].full || columns == 1) {
                b.forEach { it.content() }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(gap.dp)) {
                    for (col in 0 until columns) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            b.filterIndexed { i, _ -> i % columns == col }.forEach { it.content() }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Page(columns: Int, eyebrow: String, title: String, cells: List<Cell>) {
    val gap = if (columns == 2) 48 else 14 // foldable: keep the gap over the hinge
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = if (columns == 1) 16.dp else 24.dp, vertical = 18.dp)
    ) {
        Heading(eyebrow, title)
        AdaptiveGrid(columns, cells, gap)
        Spacer(Modifier.height(24.dp))
    }
}

fun artKind(o: OutputInfo): String = when {
    o.mode == "Phone speaker" -> "spk"
    o.mode == "USB Hi-Fi" -> "dac"
    Regex("bud|wf-|pods|ear", RegexOption.IGNORE_CASE).containsMatchIn(o.name) -> "bud"
    else -> "head"
}

private val heroBrush = Brush.radialGradient(listOf(Color(0xFF3A2E26), Color(0xFF221F1D), Color(0xFF161514)))
private fun db(v: Float) = (if (v > 0) "+" else "") + (if (v % 1f == 0f) v.roundToInt().toString() else "%.1f".format(v)) + " dB"

// ---------------------------------------------------------------------------------------------

@Composable
fun HomeScreen(columns: Int, s: Settings, st: RadaEngine.Status, onEngine: (String) -> Unit, onApps: () -> Unit, openEq: () -> Unit) {
    val hero = Cell {
        Panel(brush = heroBrush) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(st.output.mode); Spacer(Modifier.weight(1f))
                Text(if (st.output.bluetooth) "Bluetooth · profile loaded" else "Profile loaded", color = RadaColors.Muted, fontSize = 11.sp)
            }
            DeviceArt(artKind(st.output), Modifier.padding(top = 4.dp))
            Text(st.output.name, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = RadaColors.Text,
                textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Row(Modifier.fillMaxWidth().padding(top = 14.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Metric(if (st.advanced) "Advanced" else "Standard", "Engine", Modifier.weight(1f))
                Metric(when (st.reach) { RadaEngine.Reach.SYSTEM_WIDE -> "All apps"; RadaEngine.Reach.PER_APP -> "Per app"; else -> "Off" }, "Reach", Modifier.weight(1f))
                Metric("≈0 ms", "Added delay", Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            CompareButton(st.bypassed) { RadaEngine.setBypass(it) }
            st.message?.let { Note(it, RadaColors.Amber) }
        }
    }
    val engine = Cell {
        Panel(title = "Engine") {
            Segmented(listOf("Standard", "Advanced"), if (st.advanced) "Advanced" else "Standard") { onEngine(it) }
            Note(if (st.advanced) "Advanced is on. Each app gets the best engine it allows." else "Works on every phone. EQ, correction and dynamics through Android's own effect chain.")
            if (st.advanced) GhostButton("See apps", Modifier.padding(top = 10.dp)) { onApps() }
        }
    }
    val sig = Cell { Panel(title = "Sound signature") { Chips(Presets.names, s.preset) { n -> RadaEngine.update { Presets.apply(n, it) } } } }
    val quick = Cell {
        Panel(title = "Quick controls") {
            DialRow {
                Dial("Bass", s.bass) { v -> RadaEngine.update { it.copy(bass = v.roundToInt().toFloat(), preset = "Custom") } }
                Dial("Clarity", s.clarity) { v -> RadaEngine.update { it.copy(clarity = v.roundToInt().toFloat(), preset = "Custom") } }
                Dial("Width", s.width) { v -> RadaEngine.update { it.copy(width = v.roundToInt().toFloat()) } }
            }
            Note("Drag a dial up or down. Width is deliberately subtle in Standard mode.")
        }
    }
    val eqPreview = Cell {
        Panel(title = "Equaliser") {
            EqCurve(List(10) { s.bandGain(it) })
            Note("${s.preset} · preamp ${db(s.preamp)}")
            GhostButton("Open equaliser", Modifier.padding(top = 10.dp)) { openEq() }
        }
    }
    val saved = Cell { ProfilesPanel(s, st, compact = true) }
    val cells = if (columns >= 3) listOf(hero, engine, quick, sig, saved, eqPreview) else listOf(hero, engine, sig, saved, quick)
    Page(columns, "RADA Sense", "Your sound", cells)
}

@Composable
fun EqScreen(columns: Int, s: Settings) {
    val graphic = Cell(full = columns > 1) {
        Panel(title = "Graphic EQ", trailing = { Tag("10 bands") }) {
            EqCurve(List(10) { s.bandGain(it) })
            Faders(s.bands, Settings.LABELS) { i, v ->
                RadaEngine.update { cur -> cur.copy(bands = cur.bands.toMutableList().also { it[i] = v }, preset = "Custom") }
            }
            Spacer(Modifier.height(14.dp))
            Chips(Presets.names, s.preset) { n -> RadaEngine.update { Presets.apply(n, it) } }
        }
    }
    val tone = Cell {
        Panel(title = "Tone and gain") {
            SliderRow("Preamp", s.preamp, -18f..6f, db(s.preamp), 23) { v -> RadaEngine.update { it.copy(preamp = v.roundToInt().toFloat()) } }
            SliderRow("Treble", s.treble, -12f..12f, db(s.treble), 23) { v -> RadaEngine.update { it.copy(treble = v.roundToInt().toFloat(), preset = "Custom") } }
            val bal = s.balance.roundToInt()
            SliderRow("Balance", s.balance, -100f..100f, if (bal == 0) "Centre" else (if (bal < 0) "L " else "R ") + abs(bal)) { v -> RadaEngine.update { it.copy(balance = v.roundToInt().toFloat()) } }
            Note("Preamp lowers the level before boosts, so the limiter rarely has to work.")
        }
    }
    val limits = Cell {
        Panel(title = "Headroom") {
            SettingRow("Peak limiter", "Stops clipping when the EQ adds gain", s.limiter, tag = "All apps", tagKind = TagKind.Normal) { RadaEngine.update { it.copy(limiter = !it.limiter) } }
            GhostButton("Flatten EQ") { RadaEngine.update { Presets.apply("Reference", it) } }
        }
    }
    Page(columns, "Precision", "Equaliser", listOf(graphic, tone, limits))
}

@Composable
fun SpaceScreen(columns: Int, s: Settings, st: RadaEngine.Status, onSetup: () -> Unit) {
    val room = Cell {
        Panel(title = "Room", trailing = { Tag(if (st.reverbApps > 0) "${st.reverbApps} app" + (if (st.reverbApps > 1) "s" else "") else "Some apps", TagKind.Limit) },
            brush = Brush.radialGradient(listOf(Color(0xFF4A3A2F), Color(0xFF211E1C), Color(0xFF141312)))) {
            RoomScene(s.roomSize, s.reverb && st.running)
            Chips(Presets.rooms, s.room) { r -> RadaEngine.update { it.copy(room = r) } }
            SliderRow("Room size", s.roomSize, 0f..100f, "${s.roomSize.roundToInt()}%") { v -> RadaEngine.update { it.copy(roomSize = v.roundToInt().toFloat()) } }
            Note("Reverb reaches apps that announce their audio, such as Spotify. Play something there and it lights up.")
        }
    }
    val std = Cell {
        Panel {
            SettingRow("Room reverb", "Adds a room around the music", s.reverb, tag = "Some apps") { RadaEngine.update { it.copy(reverb = !it.reverb) } }
            SettingRow("Virtual surround", when (st.virtualizerAccepted) { true -> "Accepted by this phone"; false -> "This phone ignored it"; null -> "Android's binaural virtualiser, opt-in" },
                s.virtualizer, tag = "Varies by phone") { RadaEngine.update { it.copy(virtualizer = !it.virtualizer) } }
            SliderRow("Stereo width", s.width, 0f..100f, "${s.width.roundToInt()}%") { v -> RadaEngine.update { it.copy(width = v.roundToInt().toFloat()) } }
            Note("Subtle in Standard mode: Android lets RADA lean frequencies left and right, not mix channels.")
        }
    }
    val adv = Cell { AdvancedPanel("Advanced effects", st.advanced, onSetup, listOf(
        Triple("HRTF spatial", "Binaural rendering of stereo", s.spatial) to { RadaEngine.update { it.copy(spatial = !it.spatial) } },
        Triple("Crossfeed", "Speaker-like stereo on headphones", s.crossfeed) to { RadaEngine.update { it.copy(crossfeed = !it.crossfeed) } },
        Triple("True mono", "Left and right in both ears", s.mono) to { RadaEngine.update { it.copy(mono = !it.mono) } },
    ), footer = if (st.advanced) null else "Until then, Android's own Mono audio is in Settings › Accessibility.") }
    Page(columns, "Immersive", "Sound space", listOf(room, std, adv))
}

@Composable
fun AdvancedPanel(title: String, advanced: Boolean, onSetup: () -> Unit, rows: List<Pair<Triple<String, String, Boolean>, () -> Unit>>, footer: String? = null) {
    Panel(title = title, trailing = { Tag("Shizuku", TagKind.Advanced) }, brush = AdvBrush, border = Color(0xFF856143)) {
        rows.forEach { (t, toggle) ->
            SettingRow(t.first, t.second, t.third, locked = !advanced) { if (advanced) toggle() else onSetup() }
        }
        if (advanced) Note("Saved for this output. The capture engine that applies these arrives in the next update.", RadaColors.Amber)
        else CtaButton("Set up Advanced mode", Modifier.padding(top = 12.dp)) { onSetup() }
        footer?.let { Note(it) }
    }
}

@Composable
fun DevicesScreen(columns: Int, s: Settings, st: RadaEngine.Status) {
    val hero = Cell {
        Panel(brush = heroBrush) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(if (st.running) "Connected" else "Engine off"); Spacer(Modifier.weight(1f))
                Text(st.output.mode, color = RadaColors.Muted, fontSize = 11.sp)
            }
            DeviceArt(artKind(st.output), Modifier.padding(top = 4.dp))
            Text(st.output.name, fontSize = 22.sp, fontWeight = FontWeight.Bold, color = RadaColors.Text, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Note(if (st.output.bluetooth) "Battery and codec appear here for models with a tested adapter." else "Direct route: no Bluetooth encoding or hardware controls.",
                modifier = Modifier.fillMaxWidth())
        }
    }
    val noise = Cell {
        if (st.output.bluetooth) Panel(title = "Noise control", trailing = { Tag("Manual") }) {
            Segmented(listOf("Off", "ANC", "Transparency"), s.noise) { n -> RadaEngine.update { it.copy(noise = n) } }
            Note("No tested adapter for this model yet, so this picks the preset for the mode your headphones are in.")
        } else Panel(title = "Noise control") { Note("Only for Bluetooth headphones.") }
    }
    val correction = Cell {
        Panel {
            SettingRow("Headphone correction", "Measured curve for your model", s.autoEq, tag = "Coming soon", tagKind = TagKind.Limit) { RadaEngine.update { it.copy(autoEq = !it.autoEq) } }
            Text("Model", color = RadaColors.Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
            Box(Modifier.fillMaxWidth().padding(top = 6.dp).height(44.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF171615))
                .border(1.dp, RadaColors.Line, RoundedCornerShape(12.dp)).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(value = st.output.name, onValueChange = {}, readOnly = true, singleLine = true,
                    textStyle = TextStyle(color = RadaColors.Text, fontSize = 15.sp), cursorBrush = SolidColor(RadaColors.Copper))
            }
        }
    }
    val outputs = Cell(full = true) {
        Panel(title = "Your outputs") {
            val keys = (RadaEngine.savedOutputs() + st.output.key).distinct()
            keys.forEach { key ->
                val playing = key == st.output.key
                val name = if (playing) st.output.name else key.substringAfter(":").let { if (it == "speaker") "Phone speaker" else if (it == "wired") "Wired headphones" else it }
                Row(
                    Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp))
                        .background(if (playing) Color(0xFF382B21) else Color(0x08FFFFFF))
                        .border(1.dp, if (playing) Color(0xFFE9AB76) else Color(0xFF3A302A), RoundedCornerShape(14.dp)).padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF171615)).border(1.dp, RadaColors.Line, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), color = RadaColors.Copper, fontWeight = FontWeight.Bold) }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(name, color = RadaColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        Text(RadaEngine.profileFor(key).preset, color = RadaColors.Muted, fontSize = 12.sp)
                    }
                    Text(if (playing) "Playing" else "Saved", color = if (playing) RadaColors.Copper else RadaColors.Muted, fontSize = 12.sp)
                }
            }
            Note("Connect headphones or a DAC and RADA switches by itself. Each output remembers its last sound; saved profiles are the ones you keep.")
        }
    }
    val library = Cell(full = true) { ProfilesPanel(s, st) }
    Page(columns, "RADA Sense", "Devices", listOf(hero, noise, correction, library, outputs))
}

@Composable
fun StudioScreen(columns: Int, s: Settings, st: RadaEngine.Status, version: String, onSetup: () -> Unit, onCopy: () -> Unit) {
    val dyn = Cell {
        Panel(title = "Dynamics") {
            SettingRow("Peak limiter", "Stops clipping when the EQ adds gain", s.limiter) { RadaEngine.update { it.copy(limiter = !it.limiter) } }
            SettingRow("Compressor", "Evens out loud and quiet parts", s.compressor) { RadaEngine.update { it.copy(compressor = !it.compressor) } }
            Note("Android gives the output one compressor, so the Night preset and Compressor share it.")
        }
    }
    val ears = Cell {
        Panel(title = "Your ears", trailing = { Tag("All apps") }) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                listOf("Left ear" to s.leftEar, "Right ear" to s.rightEar).forEach { (l, v) ->
                    Column(Modifier.weight(1f).clip(RoundedCornerShape(16.dp)).background(Color(0x08FFFFFF)).border(1.dp, Color(0xFF3A302A), RoundedCornerShape(16.dp)).padding(14.dp),
                        horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(l, color = RadaColors.Muted, fontSize = 13.sp)
                        Text(db(v), color = RadaColors.Copper, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            SliderRow("Left trim", s.leftEar, -12f..12f, db(s.leftEar), 47) { v -> RadaEngine.update { it.copy(leftEar = (v * 2).roundToInt() / 2f) } }
            SliderRow("Right trim", s.rightEar, -12f..12f, db(s.rightEar), 47) { v -> RadaEngine.update { it.copy(rightEar = (v * 2).roundToInt() / 2f) } }
            Note("Set each side until voices sit in the middle. The hearing check comes in a later build.")
        }
    }
    val adv = Cell { AdvancedPanel("Advanced processing", st.advanced, onSetup, listOf(
        Triple("Convolution", "Load a room or headphone impulse response", s.convolution) to { RadaEngine.update { it.copy(convolution = !it.convolution) } },
        Triple("Harmonic warmth", "Gentle valve-style colour", s.warmth) to { RadaEngine.update { it.copy(warmth = !it.warmth) } },
        Triple("Loudness levelling", "Even volume between songs and apps", s.levelling) to { RadaEngine.update { it.copy(levelling = !it.levelling) } },
    )) }
    val profiles = Cell(full = columns > 1) {
        Panel(title = "This output") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GhostButton("Copy settings") { onCopy() }
                GhostButton("Reset this output") { RadaEngine.resetOutput() }
            }
            Note("Save named profiles on the Home or Devices tab. Profiles hold settings only; RADA never records or uploads audio. RADA audio $version")
        }
    }
    Page(columns, "Studio", "Processing", listOf(dyn, ears, adv, profiles))
}
