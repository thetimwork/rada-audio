package africa.myrada.audio.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import africa.myrada.audio.MainActivity
import africa.myrada.audio.R

/** Keeps the Standard engine alive while other apps play, and listens for announced sessions. */
class EngineService : Service() {

    private lateinit var audio: AudioManager
    private var capture: CaptureEngine? = null

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val id = intent.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, -1)
            val pkg = intent.getStringExtra(AudioEffect.EXTRA_PACKAGE_NAME) ?: "unknown app"
            when (intent.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> RadaEngine.openSession(id, pkg)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> RadaEngine.closeSession(id)
            }
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(added: Array<out AudioDeviceInfo>?) = refreshOutput()
        override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>?) = refreshOutput()
    }

    override fun onCreate() {
        super.onCreate()
        audio = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        RadaEngine.init(applicationContext)
        startInForeground()
        RadaEngine.start()
        refreshOutput()
        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        ContextCompat.registerReceiver(this, sessionReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
        audio.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CAPTURE -> if (Build.VERSION.SDK_INT >= 29) {
                val code = intent.getIntExtra(EXTRA_CODE, 0)
                @Suppress("DEPRECATION") val data: Intent? = intent.getParcelableExtra(EXTRA_DATA)
                if (data != null) startCapture(code, data)
            }
            ACTION_STOP_CAPTURE -> stopCapture(null)
        }
        return START_STICKY
    }

    private fun startCapture(code: Int, data: Intent) {
        if (Build.VERSION.SDK_INT < 29) return
        stopCapture(null)
        // Android 14 wants the media-projection service type in place before the token is used.
        startInForeground(projection = true)
        val mpm = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as android.media.projection.MediaProjectionManager
        val mp = runCatching { mpm.getMediaProjection(code, data) }.getOrNull()
        if (mp == null) {
            RadaEngine.onCaptureChanged(false, emptyList(), "Android did not hand over audio capture. Try turning Advanced on again.")
            startInForeground(projection = false); return
        }
        mp.registerCallback(object : android.media.projection.MediaProjection.Callback() {
            override fun onStop() { Handler(Looper.getMainLooper()).post { stopCapture("Audio capture was stopped by Android.") } }
        }, Handler(Looper.getMainLooper()))
        val engine = CaptureEngine(applicationContext, mp) { reason ->
            Handler(Looper.getMainLooper()).post { capture = null; startInForeground(projection = false) }
        }
        if (engine.start()) {
            capture = engine
            RadaEngine.onCaptureChanged(true, emptyList(), null)
        } else {
            runCatching { mp.stop() }
            RadaEngine.onCaptureChanged(false, emptyList(), "Rada could not open its audio output for Advanced mode.")
            startInForeground(projection = false)
        }
    }

    private fun stopCapture(reason: String?) {
        val c = capture ?: return
        capture = null
        c.stop(reason)
    }

    override fun onDestroy() {
        stopCapture(null)
        runCatching { unregisterReceiver(sessionReceiver) }
        runCatching { audio.unregisterAudioDeviceCallback(deviceCallback) }
        RadaEngine.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun refreshOutput() = RadaEngine.onOutputChanged(Outputs.current(this, audio))

    private fun startInForeground(projection: Boolean = false) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Sound engine", NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Rada Audio is shaping your sound")
            .setContentText("Tap to adjust")
            .setContentIntent(open)
            .setOngoing(true)
            .build()
        var type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        if (projection && Build.VERSION.SDK_INT >= 29) type = type or ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION
        ServiceCompat.startForeground(this, 1, notification, type)
    }

    companion object {
        private const val CHANNEL = "engine"
        private const val ACTION_CAPTURE = "africa.myrada.audio.CAPTURE"
        private const val ACTION_STOP_CAPTURE = "africa.myrada.audio.STOP_CAPTURE"
        private const val EXTRA_CODE = "code"
        private const val EXTRA_DATA = "data"

        /** Hands the media-projection result to the service, which starts the capture engine. */
        fun startCapture(context: Context, code: Int, data: Intent) =
            ContextCompat.startForegroundService(context, Intent(context, EngineService::class.java)
                .setAction(ACTION_CAPTURE).putExtra(EXTRA_CODE, code).putExtra(EXTRA_DATA, data))

        fun stopCapture(context: Context) {
            context.startService(Intent(context, EngineService::class.java).setAction(ACTION_STOP_CAPTURE))
        }
        fun start(context: Context) =
            ContextCompat.startForegroundService(context, Intent(context, EngineService::class.java))

        fun stop(context: Context) {
            context.stopService(Intent(context, EngineService::class.java))
        }
    }
}
