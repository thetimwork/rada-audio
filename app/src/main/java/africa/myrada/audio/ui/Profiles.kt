package africa.myrada.audio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.myrada.audio.engine.OutputInfo
import africa.myrada.audio.engine.RadaEngine
import africa.myrada.audio.engine.Settings

fun defaultType(o: OutputInfo): String = when (artKind(o)) {
    "bud" -> "Earphones"
    "dac" -> "USB DAC"
    "spk" -> "Speaker"
    else -> "Headphones"
}

private fun typeLetter(type: String) = when (type) {
    "Earphones" -> "E"; "USB DAC" -> "D"; "Speaker" -> "S"; else -> "H"
}

/**
 * Save the sound you have now as a named profile. [compact] shows only the newest few,
 * for the Home screen; the Devices screen shows them all.
 */
@Composable
fun ProfilesPanel(s: Settings, st: RadaEngine.Status, compact: Boolean = false, modifier: Modifier = Modifier) {
    val library by RadaEngine.library.collectAsState()
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RadaEngine.SavedProfile?>(null) }
    var justSaved by remember { mutableStateOf<String?>(null) }

    Panel(modifier, title = "Your profiles", trailing = { Tag("${library.size} saved") }) {
        CtaButton("Save profile") { saving = true }
        Note(justSaved?.let { "Saved “$it”." }
            ?: "Saves everything you have set now: EQ, dials, room, ears and dynamics.")

        val shown = if (compact) library.take(3) else library
        if (library.isEmpty()) {
            Note("No profiles yet. Set up the sound you like for your earphones or headphones, then hit Save.",
                modifier = Modifier.padding(top = 8.dp))
        }
        shown.forEach { p ->
            val active = s.preset == p.name
            Row(
                Modifier.fillMaxWidth().padding(top = 8.dp).clip(RoundedCornerShape(14.dp))
                    .background(if (active) Color(0xFF382B21) else Color(0x08FFFFFF))
                    .border(1.dp, if (active) Color(0xFFE9AB76) else Color(0xFF3A302A), RoundedCornerShape(14.dp))
                    .clickable { RadaEngine.loadProfile(p.id) }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF171615))
                    .border(1.dp, RadaColors.Line, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                    Text(typeLetter(p.type), color = RadaColors.Copper, fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(p.name, color = RadaColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    Text("${p.type} · made on ${p.outputName}", color = RadaColors.Muted, fontSize = 12.sp)
                }
                if (active) Text("On", color = RadaColors.Copper, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                else Text("Load", color = RadaColors.Copper, fontSize = 12.sp)
                if (!compact) {
                    Spacer(Modifier.width(14.dp))
                    Text("Delete", color = RadaColors.Muted, fontSize = 12.sp,
                        modifier = Modifier.clickable { deleting = p }.padding(4.dp))
                }
            }
        }
        if (compact && library.size > 3) Note("${library.size - 3} more on the Devices tab.", modifier = Modifier.padding(top = 6.dp))
        if (library.isNotEmpty()) Note("Tap a profile to put it on ${st.output.name}.", modifier = Modifier.padding(top = 6.dp))
    }

    if (saving) SaveProfileDialog(s, st, onDismiss = { saving = false }) { name, type ->
        RadaEngine.saveProfile(name, type); justSaved = name.trim().ifEmpty { "My sound" }; saving = false
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = RadaColors.Panel,
            title = { Text("Delete “${p.name}”?", color = RadaColors.Text) },
            text = { Text("The sound playing now stays as it is.", color = RadaColors.Muted) },
            confirmButton = { TextButton({ RadaEngine.deleteProfile(p.id); deleting = null }) { Text("Delete", color = RadaColors.Amber) } },
            dismissButton = { TextButton({ deleting = null }) { Text("Keep", color = RadaColors.Copper) } },
        )
    }
}

@Composable
fun SaveProfileDialog(s: Settings, st: RadaEngine.Status, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    val suggested = if (s.preset == "Custom" || s.preset.isBlank()) "${st.output.name} sound" else s.preset
    var name by remember { mutableStateOf(suggested) }
    var type by remember { mutableStateOf(defaultType(st.output)) }
    val replacing = RadaEngine.library.value.any { it.name.equals(name.trim(), true) && it.type == type }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = RadaColors.Panel,
        title = { Text("Save profile", color = RadaColors.Text, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    label = { Text("Name") },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = RadaColors.Text, unfocusedTextColor = RadaColors.Text,
                        focusedBorderColor = RadaColors.Copper, unfocusedBorderColor = RadaColors.Line,
                        focusedLabelColor = RadaColors.Copper, unfocusedLabelColor = RadaColors.Muted,
                        cursorColor = RadaColors.Copper,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("For", color = RadaColors.Muted, fontSize = 12.sp)
                Chips(RadaEngine.PROFILE_TYPES, type) { type = it }
                Note(if (replacing) "A ${type.lowercase()} profile with this name exists. Saving replaces it."
                     else "Made on ${st.output.name}. You can load it on any output later.")
            }
        },
        confirmButton = { TextButton({ onSave(name, type) }) { Text(if (replacing) "Replace" else "Save", color = RadaColors.Copper, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = RadaColors.Muted) } },
    )
}
