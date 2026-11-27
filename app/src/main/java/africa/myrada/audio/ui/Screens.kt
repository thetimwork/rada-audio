package africa.myrada.audio.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.myrada.audio.engine.Presets
import africa.myrada.audio.engine.RadaEngine
import africa.myrada.audio.engine.Settings
import kotlin.math.roundToInt

// ---------- building blocks ----------------------------------------------------------------

@Composable
fun Panel(title: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(Brush.linearGradient(listOf(Color(0xFF302C28), Color(0xFF191817))))
            .border(1.dp, RadaColors.Line, RoundedCornerShape(22.dp))
            .padding(18.dp)
    ) {
        if (title != null) {
            Text(title, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = RadaColors.Text)
            Spacer(Modifier.height(10.dp))
        }
        content()
    }
}

@Composable
fun Heading(eyebrow: String, title: String) {
    Column(Modifier.padding(bottom = 6.dp)) {
        Text(eyebrow.uppercase(), color = RadaColors.Copper, fontSize = 11.sp, letterSpacing = 2.sp)
        Text(title, fontSize = 30.sp, fontWeight = FontWeight.Bold, color = RadaColors.Text)
    }
}

@Composable
fun Note(text: String, color: Color = RadaColors.Muted) {
    Text(text, color = color, fontSize = 12.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 6.dp))
}

@Composable
fun Tag(text: String, color: Color = RadaColors.Copper) {
    Text(
        text, color = color, fontSize = 11.sp,
        modifier = Modifier
            .border(1.dp, Color(0xFF856143), RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    )
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, enabled: Boolean = true, tag: String? = null, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.SemiBold, color = RadaColors.Text)
                if (tag != null) { Spacer(Modifier.width(8.dp)); Tag(tag, RadaColors.Amber) }
            }
            Text(subtitle, color = RadaColors.Muted, fontSize = 12.sp)
        }
        Switch(
            checked = checked, onCheckedChange = onChange, enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = RadaColors.Copper, checkedThumbColor = RadaColors.OnCopper),
        )
    }
}

@Composable
fun LabeledSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, unit: String, onChange: (Float) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(label, color = RadaColors.Text, modifier = Modifier.weight(1f))
            val v = value.roundToInt()
            Text((if (unit == " dB" && v > 0) "+" else "") + "$v$unit", color = RadaColors.Copper)
        }
        Slider(
            value = value, onValueChange = onChange, valueRange = range,
            colors = SliderDefaults.colors(thumbColor = RadaColors.Copper, activeTrackColor = RadaColors.Copper, inactiveTrackColor = RadaColors.Panel2),
        )
    }
}

@Composable
fun PresetRow(current: String) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(Presets.names) { name ->
            FilterChip(
                selected = name == current,
                onClick = { RadaEngine.update { Presets.apply(name, it) } },
                label = { Text(name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = RadaColors.Copper, selectedLabelColor = RadaColors.OnCopper,
                    labelColor = RadaColors.Text,
                ),
            )
        }
    }
}

@Composable
fun Page(content: androidx.compose.foundation.lazy.LazyListScope.() -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        content = content,
    )
}

// ---------- screens ------------------------------------------------------------------------

@Composable
fun HomeScreen(s: Settings, st: RadaEngine.Status, onPower: (Boolean) -> Unit) {
    Page {
        item { Heading("RADA Sense", "Your sound") }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Sound engine", fontWeight = FontWeight.SemiBold, color = RadaColors.Text)
                        Text(
                            when (st.reach) {
                                RadaEngine.Reach.SYSTEM_WIDE -> "On · all apps"
                                RadaEngine.Reach.PER_APP -> "On · apps that announce their audio"
                                RadaEngine.Reach.OFF -> "Off · you hear the original sound"
                            },
                            color = RadaColors.Muted, fontSize = 12.sp,
                        )
                    }
                    Switch(
                        checked = st.running, onCheckedChange = onPower,
                        colors = SwitchDefaults.colors(checkedTrackColor = RadaColors.Copper, checkedThumbColor = RadaColors.OnCopper),
                    )
                }
                st.message?.let { Note(it, RadaColors.Amber) }
            }
        }
        item {
            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Tag(st.output.mode)
                    Spacer(Modifier.weight(1f))
                    Text("Engine: Standard", color = RadaColors.Muted, fontSize = 12.sp)
                }
                Spacer(Modifier.height(12.dp))
                Text(st.output.name, fontSize = 24.sp, fontWeight = FontWeight.Bold, color = RadaColors.Text)
                Note("Settings below are saved for this output and come back when you reconnect it.")
                Spacer(Modifier.height(12.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .border(BorderStroke(1.dp, if (st.bypassed) RadaColors.Amber else RadaColors.Line), RoundedCornerShape(14.dp))
                        .background(if (st.bypassed) Color(0xFF3A2A1C) else Color.Transparent)
                        .pointerInput(Unit) {
                            detectTapGestures(onPress = {
                                RadaEngine.setBypass(true)
                                tryAwaitRelease()
                                RadaEngine.setBypass(false)
                            })
                        }
                        .padding(14.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        if (st.bypassed) "Original sound" else "Hold to compare with original",
                        color = if (st.bypassed) RadaColors.Amber else RadaColors.Text,
                    )
                }
            }
        }
        item { Panel("Sound signature") { PresetRow(s.preset) } }
        item {
            Panel("Quick controls") {
                LabeledSlider("Bass", s.bass, 0f..100f, "") { v -> RadaEngine.update { it.copy(bass = v, preset = "Custom") } }
                LabeledSlider("Clarity", s.clarity, 0f..100f, "") { v -> RadaEngine.update { it.copy(clarity = v, preset = "Custom") } }
            }
        }
    }
}

@Composable
fun EqScreen(s: Settings) {
    Page {
        item { Heading("Precision", "Equaliser") }
        item { Panel("Sound signature") { PresetRow(s.preset) } }
        item {
            Panel("10-band graphic EQ") {
                Settings.FREQS.forEachIndexed { i, f ->
                    val label = if (f >= 1000f) "${(f / 1000f).let { if (it % 1f == 0f) it.toInt().toString() else it.toString() }} kHz" else "${f.toInt()} Hz"
                    LabeledSlider(label, s.bands[i], -12f..12f, " dB") { v ->
                        RadaEngine.update { cur -> cur.copy(bands = cur.bands.toMutableList().also { it[i] = v.roundToInt().toFloat() }, preset = "Custom") }
                    }
                }
                OutlinedButton(onClick = { RadaEngine.update { Presets.apply("Reference", it) } }) { Text("Flatten EQ", color = RadaColors.Copper) }
            }
        }
        item {
            Panel("Tone and gain") {
                LabeledSlider("Preamp", s.preamp, -18f..6f, " dB") { v -> RadaEngine.update { it.copy(preamp = v.roundToInt().toFloat()) } }
                LabeledSlider("Balance", s.balance, -100f..100f, "") { v -> RadaEngine.update { it.copy(balance = v.roundToInt().toFloat()) } }
                Note("Preamp lowers the level before boosts, so the limiter rarely has to work.")
            }
        }
    }
}

@Composable
fun SpaceScreen(shizukuInstalled: Boolean) {
    Page {
        item { Heading("Immersive", "Sound space") }
        item {
            Panel("Coming in the next build") {
                Note("Room reverb (apps that announce their audio) and virtual surround (varies by phone) are planned for the Standard engine.")
            }
        }
        item {
            Panel("Advanced engine (Shizuku)") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Shizuku", color = RadaColors.Text, modifier = Modifier.weight(1f))
                    Tag(if (shizukuInstalled) "Installed" else "Not installed", if (shizukuInstalled) RadaColors.Copper else RadaColors.Amber)
                }
                Note("Crossfeed, HRTF spatial, convolution and true mono need Advanced mode, which runs other apps' audio through RADA's own engine. It is not in this alpha.")
                Note("Until then, Android's own Mono audio is in Settings › Accessibility.")
            }
        }
    }
}

@Composable
fun DevicesScreen(st: RadaEngine.Status) {
    Page {
        item { Heading("RADA Sense", "Devices") }
        item {
            Panel("Now playing through") {
                Text(st.output.name, fontSize = 20.sp, fontWeight = FontWeight.Bold, color = RadaColors.Text)
                Spacer(Modifier.height(6.dp)); Tag(st.output.mode)
                Note(if (st.output.bluetooth) "Noise control will appear here for headphone models with a tested adapter." else "Direct route: no Bluetooth encoding or hardware controls.")
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { RadaEngine.resetOutput() }) { Text("Reset this output", color = RadaColors.Copper) }
            }
        }
        item {
            Panel("Saved outputs") {
                val saved = RadaEngine.savedOutputs()
                if (saved.isEmpty()) Note("None yet. Change a setting and it is saved for the current output.")
                saved.forEach { key ->
                    Text(key.substringAfter(":").replaceFirstChar { it.uppercase() }, color = RadaColors.Text, modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@Composable
fun StudioScreen(s: Settings, st: RadaEngine.Status, version: String) {
    Page {
        item { Heading("Studio", "Processing") }
        item {
            Panel("Dynamics") {
                ToggleRow("Peak limiter", "Stops clipping when the EQ adds gain", s.limiter) { v -> RadaEngine.update { it.copy(limiter = v) } }
                ToggleRow("Compressor", "Evens out loud and quiet parts (Night preset uses it)", s.compressor) { v -> RadaEngine.update { it.copy(compressor = v) } }
            }
        }
        item {
            Panel("Apps reached") {
                if (st.reach == RadaEngine.Reach.SYSTEM_WIDE) Note("System-wide effect active: every app is processed.")
                if (st.apps.isEmpty()) Note("No app has announced its audio yet. Play something in Spotify or another player.")
                st.apps.forEach { Text(it, color = RadaColors.Text, fontSize = 13.sp, modifier = Modifier.padding(vertical = 3.dp)) }
            }
        }
        item { Note("RADA audio $version · Standard engine alpha · Settings only, no audio is recorded or uploaded.") }
    }
}
