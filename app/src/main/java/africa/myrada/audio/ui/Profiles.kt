package africa.myrada.audio.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
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
 * Save the sound you have now as a named profile. compact = the Home shortlist (newest three,
 * drawn as a contrast card); the Devices tab shows them all with Delete.
 */
@Composable
fun ProfilesPanel(s: Settings, st: RadaEngine.Status, compact: Boolean = false, modifier: Modifier = Modifier) {
    val library by RadaEngine.library.collectAsState()
    var saving by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<RadaEngine.SavedProfile?>(null) }
    var justSaved by remember { mutableStateOf<String?>(null) }

    Panel(modifier, title = "Your profiles", trailing = { Tag("${library.size} saved") }, contrast = compact) {
        CtaButton("Save profile") { saving = true }
        Note(justSaved?.let { "Saved “$it”." } ?: "Saves everything you have set now: EQ, dials, room, ears and dynamics.")

        val shown = if (compact) library.take(3) else library
        if (library.isEmpty()) Note("No profiles yet. Set up the sound you like for your earphones or headphones, then hit Save.")
        shown.forEach { p ->
            val active = s.preset == p.name
            OutputRow(typeLetter(p.type), p.name, "${p.type} · made on ${p.outputName}", active, onClick = { RadaEngine.loadProfile(p.id) }) {
                if (active) {
                    RedDot(7.dp); Spacer(Modifier.width(6.dp))
                    Text("ON", fontSize = 10.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.SemiBold, color = Pal.black)
                } else {
                    Box(Modifier.size(34.dp).clip(CircleShape).background(Pal.pri), contentAlignment = Alignment.Center) {
                        StrokeIcon(IC_ARROW, Pal.onPri, 15.dp)
                    }
                }
                if (!compact) {
                    Spacer(Modifier.width(10.dp))
                    Text("Delete", fontSize = 12.sp, color = Pal.muted, fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.clip(RoundedCornerShape(50)).clickable { deleting = p }.padding(horizontal = 8.dp, vertical = 8.dp))
                }
            }
        }
        if (compact && library.size > 3) Note("${library.size - 3} more on the Devices tab.")
        if (library.isNotEmpty()) Note("Tap a profile to put it on ${st.output.name}.")
    }

    if (saving) SaveProfileDialog(s, st, onDismiss = { saving = false }) { name, type ->
        RadaEngine.saveProfile(name, type); justSaved = name.trim().ifEmpty { "My sound" }; saving = false
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Pal.bg,
            shape = RoundedCornerShape(CardRadius),
            title = { Text("Delete “${p.name}”?", color = Pal.black, fontWeight = FontWeight.ExtraBold) },
            text = { Text("The sound playing now stays as it is.", color = Pal.muted2) },
            confirmButton = { TextButton({ RadaEngine.deleteProfile(p.id); deleting = null }) { Text("Delete", color = Red, fontWeight = FontWeight.Bold) } },
            dismissButton = { TextButton({ deleting = null }) { Text("Keep", color = Pal.black, fontWeight = FontWeight.SemiBold) } },
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
        containerColor = Pal.bg,
        shape = RoundedCornerShape(CardRadius),
        title = { Text("Save profile", color = Pal.black, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    label = { Text("Name") },
                    shape = RoundedCornerShape(TileRadius),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Pal.black, unfocusedTextColor = Pal.black,
                        focusedBorderColor = Pal.black, unfocusedBorderColor = Pal.line,
                        focusedLabelColor = Pal.black, unfocusedLabelColor = Pal.muted,
                        cursorColor = Pal.black, focusedContainerColor = Pal.card, unfocusedContainerColor = Pal.card,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("For", color = Pal.muted2, fontSize = 12.sp)
                Chips(RadaEngine.PROFILE_TYPES, type) { type = it }
                Note(if (replacing) "A ${type.lowercase()} profile with this name exists. Saving replaces it."
                     else "Made on ${st.output.name}. You can load it on any output later.")
            }
        },
        confirmButton = { TextButton({ onSave(name, type) }) { Text(if (replacing) "Replace" else "Save", color = Pal.black, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onDismiss) { Text("Cancel", color = Pal.muted2) } },
    )
}
