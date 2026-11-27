package africa.myrada.audio.engine

import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku

/**
 * Advanced mode setup. Shizuku runs with shell rights (started over Wireless debugging), so with
 * its permission RADA can grant itself the two things the capture engine needs:
 *  - DUMP: see which apps are playing and their audio sessions
 *  - PROJECT_MEDIA app-op: capture playback without the screen-recording prompt each time
 */
object ShizukuBridge {
    private const val TAG = "ShizukuBridge"
    private const val SHIZUKU_PKG = "moe.shizuku.privileged.api"
    private const val REQUEST = 7

    enum class State { NOT_INSTALLED, NOT_RUNNING, NEEDS_PERMISSION, NEEDS_GRANTS, READY }

    data class Info(
        val state: State = State.NOT_INSTALLED,
        val dumpGranted: Boolean = false,
        val projectMediaAllowed: Boolean = false,
        val error: String? = null,
    )

    private val _info = MutableStateFlow(Info())
    val info: StateFlow<Info> = _info.asStateFlow()
    private var listening = false

    fun start(context: Context) {
        if (!listening) {
            listening = true
            Shizuku.addBinderReceivedListenerSticky { refresh(context) }
            Shizuku.addBinderDeadListener { refresh(context) }
            Shizuku.addRequestPermissionResultListener { _, _ -> refresh(context) }
        }
        refresh(context)
    }

    fun refresh(context: Context) {
        val installed = try {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(SHIZUKU_PKG, 0); true
        } catch (e: PackageManager.NameNotFoundException) { false }
        val running = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        val permitted = running && runCatching {
            !Shizuku.isPreV11() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        val dump = context.checkSelfPermission(android.Manifest.permission.DUMP) == PackageManager.PERMISSION_GRANTED
        val projectMedia = projectMediaAllowed(context)
        val state = when {
            !installed && !running -> State.NOT_INSTALLED
            !running -> State.NOT_RUNNING
            !permitted -> State.NEEDS_PERMISSION
            !dump || !projectMedia -> State.NEEDS_GRANTS
            else -> State.READY
        }
        _info.value = _info.value.copy(state = state, dumpGranted = dump, projectMediaAllowed = projectMedia)
    }

    private fun projectMediaAllowed(context: Context): Boolean = try {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val uid = android.os.Process.myUid()
        val mode = if (android.os.Build.VERSION.SDK_INT >= 29) ops.unsafeCheckOpNoThrow("android:project_media", uid, context.packageName)
        else ops.checkOpNoThrow("android:project_media", uid, context.packageName)
        mode == android.app.AppOpsManager.MODE_ALLOWED
    } catch (t: Throwable) { false }

    fun requestPermission(context: Context) {
        runCatching { Shizuku.requestPermission(REQUEST) }
            .onFailure { _info.value = _info.value.copy(error = "Shizuku did not answer: ${it.message}") }
        refresh(context)
    }

    /** Runs the two grants through Shizuku's shell. Returns true when both succeed. */
    suspend fun grant(context: Context): Boolean = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        val dump = shell("pm grant $pkg android.permission.DUMP")
        val media = shell("appops set $pkg PROJECT_MEDIA allow")
        // Playback capture needs the record permission; granting it here saves another prompt.
        shell("pm grant $pkg android.permission.RECORD_AUDIO")
        _info.value = _info.value.copy(
            error = if (dump && media) null else "Shizuku could not grant permissions. Restart Shizuku and try again.",
        )
        refresh(context)
        dump && media
    }

    /** Runs a shell command through Shizuku and returns what it printed, or null. */
    fun shellOutput(cmd: String): String? = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
        )
        m.isAccessible = true
        val process = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val out = process.inputStream.bufferedReader().readText()
        process.waitFor()
        out
    } catch (t: Throwable) { Log.w(TAG, "shell output failed: $cmd", t); null }

    /** Shizuku.newProcess is private in API 13; it is still the documented shell path, so call it reflectively. */
    private fun shell(cmd: String): Boolean = try {
        val m = Shizuku::class.java.getDeclaredMethod(
            "newProcess", Array<String>::class.java, Array<String>::class.java, String::class.java,
        )
        m.isAccessible = true
        val process = m.invoke(null, arrayOf("sh", "-c", cmd), null, null) as Process
        val code = process.waitFor()
        if (code != 0) Log.w(TAG, "'$cmd' exited $code: " + process.errorStream.bufferedReader().readText())
        code == 0
    } catch (t: Throwable) {
        Log.w(TAG, "shell failed: $cmd", t); false
    }
}
