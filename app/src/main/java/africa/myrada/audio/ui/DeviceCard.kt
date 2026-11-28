package africa.myrada.audio.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import africa.myrada.audio.engine.DeviceTypes
import africa.myrada.audio.engine.OutputInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Asks for the edit card from the device card's Change link. */
object DeviceEditor {
    var open by mutableStateOf(false)
}

private val TYPES = listOf(DeviceTypes.SPEAKER to "Speaker", DeviceTypes.HEADPHONES to "Headphones", DeviceTypes.EARBUDS to "Earbuds")

/**
 * The New device card: shown once for a Bluetooth device Rada doesn't know yet, or from Change.
 * Name (from Bluetooth, editable) and type (Speaker, Headphones or Earbuds), nothing else.
 * Dismissing without saving keeps the suggestion for now and asks again next time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewDeviceSheet(o: OutputInfo, edit: Boolean, onSave: (name: String, kind: String) -> Unit, onDismiss: () -> Unit) {
    val start = if (edit) o.kind else o.suggestion?.kind ?: o.kind
    var name by remember(o.id) { mutableStateOf(if (edit) o.name else o.btName ?: o.name) }
    var pick by remember(o.id) { mutableStateOf(if (start in TYPES.map { it.first }) start else DeviceTypes.HEADPHONES) }
    var touched by remember(o.id) { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = Pal.card,
    ) {
        Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
            Text(if (edit) "Edit device" else "New device", fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Pal.black, letterSpacing = (-0.4).sp)
            Text(if (edit) "Change the name or type. Rada uses this from now on." else "Rada doesn't know this one yet. Tell it what it is, once.",
                fontSize = 14.sp, color = Pal.muted2, modifier = Modifier.padding(top = 4.dp, bottom = 18.dp))

            Eyebrow("Name")
            Box(Modifier.padding(top = 8.dp, bottom = 18.dp).fillMaxWidth().height(50.dp).clip(RoundedCornerShape(TileRadius)).background(Pal.bg)
                .border(1.dp, Pal.line, RoundedCornerShape(TileRadius)).padding(horizontal = 14.dp), contentAlignment = Alignment.CenterStart) {
                BasicTextField(value = name, onValueChange = { name = it.take(40) }, singleLine = true,
                    textStyle = TextStyle(fontFamily = Figtree, color = Pal.black, fontSize = 16.sp, fontWeight = FontWeight.SemiBold),
                    cursorBrush = SolidColor(Pal.black), modifier = Modifier.fillMaxWidth())
            }

            Eyebrow("Type")
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TYPES.forEach { (k, label) ->
                    TypeTile(k, label, k == pick, Modifier.weight(1f)) { pick = k; touched = true }
                }
            }
            val hint = if (!edit && !touched && pick == o.suggestion?.kind) o.suggestion?.reason else null
            Text(hint ?: "", fontSize = 12.sp, color = Pal.muted, modifier = Modifier.padding(top = 10.dp, bottom = 16.dp).height(18.dp))

            CtaButton("Save device") { onSave(name.trim().ifEmpty { o.btName ?: o.name }, pick) }
        }
    }
}

@Composable
private fun TypeTile(kind: String, label: String, on: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val ctx = LocalContext.current
    val img by produceState<ImageBitmap?>(null, kind) {
        value = withContext(Dispatchers.IO) {
            runCatching { ctx.assets.open("devices/${kind}_still.webp").use { BitmapFactory.decodeStream(it).asImageBitmap() } }.getOrNull()
        }
    }
    val shape = RoundedCornerShape(TileRadius)
    Box(modifier.clip(shape).background(if (on) Pal.card else Pal.bg).border(if (on) 1.5.dp else 1.dp, if (on) Pal.black else Pal.line, shape)
        .clickable { onClick() }.padding(top = 8.dp, bottom = 10.dp, start = 6.dp, end = 6.dp)) {
        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            img?.let { Image(it, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxWidth().height(60.dp)) }
                ?: Box(Modifier.height(60.dp))
            Text(label, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Pal.black, modifier = Modifier.padding(top = 4.dp))
        }
        if (on) Box(Modifier.align(Alignment.TopEnd).size(8.dp).clip(CircleShape).background(Red))
    }
}
