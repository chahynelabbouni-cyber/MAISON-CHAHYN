package com.maisonchahyn.app.camera

import android.os.Bundle
import android.view.KeyEvent
import android.view.OrientationEventListener
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.maisonchahyn.app.Prefs
import com.maisonchahyn.app.ui.MaisonTheme

/** « Chahyn Photo » : l'appareil photo qui s'adapte tout seul à chaque situation. */
class CameraActivity : ComponentActivity() {
    lateinit var engine: CameraEngine
    lateinit var motion: MotionSensor
    private lateinit var orientation: OrientationEventListener

    /** Défini par l'écran : déclenchement (retardateur compris). */
    var shutter: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        engine = CameraEngine(this)
        motion = MotionSensor(this)
        orientation = object : OrientationEventListener(this) {
            override fun onOrientationChanged(o: Int) {
                if (o != OrientationEventListener.ORIENTATION_UNKNOWN) engine.deviceOrientation = o
            }
        }
        setContent { MaisonTheme { CameraScreen(engine, motion, this) } }
    }

    override fun onResume() {
        super.onResume()
        engine.resume()
        motion.start()
        if (orientation.canDetectOrientation()) orientation.enable()
    }

    override fun onPause() {
        engine.pause()
        motion.stop()
        orientation.disable()
        super.onPause()
    }

    override fun onDestroy() {
        engine.release()
        super.onDestroy()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (Prefs.cVolumeShutter.value &&
            (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN || keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_CAMERA)
        ) {
            if (event?.repeatCount == 0) shutter?.invoke()
            return true
        }
        return super.onKeyDown(keyCode, event)
    }
}
