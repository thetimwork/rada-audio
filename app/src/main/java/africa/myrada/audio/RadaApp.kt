package africa.myrada.audio

import android.app.Application
import africa.myrada.audio.engine.RadaEngine

class RadaApp : Application() {
    override fun onCreate() {
        super.onCreate()
        RadaEngine.init(this)
    }
}
