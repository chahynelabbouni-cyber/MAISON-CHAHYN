package com.chahyn.photo

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.CaptureResult
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.media.Image
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.util.Range
import android.util.Size
import android.view.Surface
import android.view.TextureView
import androidx.compose.runtime.mutableStateOf
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Ce que voit le capteur en ce moment (mis à jour ~6 fois par seconde). */
data class LiveInfo(
    val iso: Int = 0,
    val exposureNs: Long = 0,
    val aperture: Float = 1.8f,
    val focusDiopters: Float = 0f,
    val faces: Int = 0,
    val faceArea: Float = 0f,
    val ev100: Float = 10f,
    val aeProduct: Double = 0.0, // ISO × temps (ns) mesuré par l'exposition auto
    val flashRequired: Boolean = false,
    val afLocked: Boolean = false,
)

/** Tous les réglages demandés au capteur. Comparés par égalité → la requête n'est renvoyée que si quelque chose change. */
data class CaptureParams(
    val scene: Scene = Scenes.equilibre,
    val evUser: Float = 0f,
    val iso: Int? = null,
    val shutterNs: Long? = null,
    val focus: Float? = null,
    val awb: Int? = null,
    val flash: String = "off",
    val zoom: Float = 1f,
    val smartIso: Boolean = true,
    val allowTripod: Boolean = true,
)

/** Plan d'exposition calculé au moment du déclenchement. */
data class ExposurePlan(val iso: Int, val exposureNs: Long, val note: String)

/** Caractéristiques utiles d'un module photo. */
class CamInfo(val id: String, val c: CameraCharacteristics) {
    val facing = c.get(CameraCharacteristics.LENS_FACING) ?: CameraMetadata.LENS_FACING_BACK
    val front = facing == CameraMetadata.LENS_FACING_FRONT
    val sensorOrientation = c.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 90
    private val caps = c.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)?.toSet() ?: emptySet()
    val manualSensor = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_MANUAL_SENSOR in caps
    val rawCapable = CameraMetadata.REQUEST_AVAILABLE_CAPABILITIES_RAW in caps
    val isoRange: Range<Int> = c.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE) ?: Range(100, 3200)
    val expRange: Range<Long> = c.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE) ?: Range(100_000L, 250_000_000L)
    val maxFrameNs: Long = c.get(CameraCharacteristics.SENSOR_INFO_MAX_FRAME_DURATION) ?: 1_000_000_000L
    val minFocus: Float = c.get(CameraCharacteristics.LENS_INFO_MINIMUM_FOCUS_DISTANCE) ?: 0f
    val evRange: Range<Int> = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE) ?: Range(0, 0)
    val evStep: Float = c.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)?.toFloat() ?: (1f / 3f)
    val afModes = c.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toSet() ?: emptySet()
    val awbModes = c.get(CameraCharacteristics.CONTROL_AWB_AVAILABLE_MODES)?.toSet() ?: emptySet()
    val sceneModes = c.get(CameraCharacteristics.CONTROL_AVAILABLE_SCENE_MODES)?.toSet() ?: emptySet()
    val nrModes = c.get(CameraCharacteristics.NOISE_REDUCTION_AVAILABLE_NOISE_REDUCTION_MODES)?.toSet() ?: emptySet()
    val edgeModes = c.get(CameraCharacteristics.EDGE_AVAILABLE_EDGE_MODES)?.toSet() ?: emptySet()
    val aberrationModes = c.get(CameraCharacteristics.COLOR_CORRECTION_AVAILABLE_ABERRATION_MODES)?.toSet() ?: emptySet()
    val hotPixelModes = c.get(CameraCharacteristics.HOT_PIXEL_AVAILABLE_HOT_PIXEL_MODES)?.toSet() ?: emptySet()
    val tonemapModes = c.get(CameraCharacteristics.TONEMAP_AVAILABLE_TONE_MAP_MODES)?.toSet() ?: emptySet()
    val oisModes = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)?.toSet() ?: emptySet()
    val faceModes = c.get(CameraCharacteristics.STATISTICS_INFO_AVAILABLE_FACE_DETECT_MODES)?.toSet() ?: emptySet()
    val fpsRanges: Array<Range<Int>> = c.get(CameraCharacteristics.CONTROL_AE_AVAILABLE_TARGET_FPS_RANGES) ?: emptyArray()
    val hasFlash = c.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
    val activeArray: Rect = c.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE) ?: Rect(0, 0, 4000, 3000)
    val aperture: Float = c.get(CameraCharacteristics.LENS_INFO_AVAILABLE_APERTURES)?.firstOrNull() ?: 1.8f
    val maxRegionsAf = c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0
    val maxRegionsAe = c.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AE) ?: 0
    private val maxDigital = c.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM) ?: 1f
    val ratioZoom: Range<Float>? =
        if (Build.VERSION.SDK_INT >= 30) c.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)
            ?.takeIf { it.upper > 1.01f || it.lower < 0.99f } else null
    val zoomMin: Float get() = ratioZoom?.lower ?: 1f
    val zoomMax: Float get() = ratioZoom?.upper ?: max(1f, maxDigital)
    val zoomSpan: Float get() = zoomMax / zoomMin
}

@SuppressLint("MissingPermission")
class CameraEngine(private val ctx: Context) {
    private val manager = ctx.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val thread = HandlerThread("ChahynCam").apply { start() }
    private val bg = Handler(thread.looper)
    private val main = Handler(Looper.getMainLooper())

    // ---------- États lus par l'interface ----------
    val ready = mutableStateOf(false)
    val error = mutableStateOf<String?>(null)
    val live = mutableStateOf(LiveInfo())
    val capturing = mutableStateOf(false)
    val captureNote = mutableStateOf<String?>(null)
    val info = mutableStateOf<CamInfo?>(null)
    val rawActive = mutableStateOf(false)
    val front = mutableStateOf(false)
    /** Réglages réellement utilisés pour la dernière photo (fiche affichée après le déclenchement). */
    val lastShot = mutableStateOf<ShotInfo?>(null)

    var textureView: TextureView? = null
        private set
    var onPhoto: ((PhotoJob) -> Unit)? = null
    var onRaw: ((RawJob) -> Unit)? = null
    var onMessage: ((String) -> Unit)? = null

    /** Orientation physique (0..359) fournie par l'activité, pour l'orientation JPEG. */
    @Volatile var deviceOrientation = 0

    /** Stabilité du téléphone (gyroscope), utilisée seulement au déclenchement. */
    @Volatile var tripod = false
    @Volatile var shake = 0.2f

    // ---------- Configuration ----------
    @Volatile private var params = CaptureParams()
    @Volatile var wantRaw = false
    @Volatile var resolution = "12"
    @Volatile var ratio = "4:3"
    @Volatile private var hasPermission = false
    @Volatile private var resumed = false

    private var device: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var jpegReader: ImageReader? = null
    private var rawReader: ImageReader? = null
    private var previewSurface: Surface? = null
    private var cam: CamInfo? = null
    private var opening = false
    private var lastLivePush = 0L
    private var lastAeProduct = 0.0
    private var lastIso = 100
    private var lastExp = 16_000_000L
    private var lastFlashRequired = false

    // Mise au point/mesure touchée
    private var tapRegion: MeteringRectangle? = null
    private var tapUntil = 0L

    // Déclenchement
    private enum class Step { IDLE, WAIT_AF, WAIT_PRE, WAIT_PRE_DONE, SHOOTING }
    private var step = Step.IDLE
    private var stepToken = 0
    private var shotMeta: ShotMeta? = null
    private var pendingRawImage: Image? = null
    private var pendingRawResult: TotalCaptureResult? = null
    private var pendingRawMeta: ShotMeta? = null

    // ---------- Cycle de vie ----------
    fun attachView(tv: TextureView) {
        textureView = tv
        tv.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(st: SurfaceTexture, w: Int, h: Int) = tryOpen()
            override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, w: Int, h: Int) {}
            override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                bg.post { closeCamera() }
                return true
            }
            override fun onSurfaceTextureUpdated(st: SurfaceTexture) {}
        }
        if (tv.isAvailable) tryOpen()
    }

    fun setPermission(granted: Boolean) {
        hasPermission = granted
        tryOpen()
    }

    fun resume() {
        resumed = true
        tryOpen()
    }

    fun pause() {
        resumed = false
        bg.post { closeCamera() }
    }

    fun release() {
        bg.post {
            closeCamera()
            thread.quitSafely()
        }
    }

    fun switchFacing() {
        front.value = !front.value
        reopen()
    }

    /** Ferme et rouvre (changement de format, RAW, résolution, caméra). */
    fun reopen() {
        bg.post {
            closeCamera()
            main.post { tryOpen() }
        }
    }

    private fun tryOpen() {
        val tv = textureView ?: return
        if (!hasPermission || !resumed || !tv.isAvailable) return
        val wantFront = front.value
        bg.post {
            if (device != null || opening) return@post
            opening = true
            try {
                val id = chooseCamera(wantFront) ?: run {
                    opening = false
                    postError("Aucun appareil photo trouvé")
                    return@post
                }
                val ci = CamInfo(id, manager.getCameraCharacteristics(id))
                cam = ci
                main.post { info.value = ci }
                manager.openCamera(id, object : CameraDevice.StateCallback() {
                    override fun onOpened(d: CameraDevice) {
                        opening = false
                        device = d
                        createSession(ci, wantRaw && ci.rawCapable)
                    }

                    override fun onDisconnected(d: CameraDevice) {
                        opening = false
                        d.close()
                        if (device == d) device = null
                        main.post { ready.value = false }
                    }

                    override fun onError(d: CameraDevice, err: Int) {
                        opening = false
                        d.close()
                        if (device == d) device = null
                        postError(
                            if (err == CameraDevice.StateCallback.ERROR_CAMERA_IN_USE || err == CameraDevice.StateCallback.ERROR_MAX_CAMERAS_IN_USE)
                                "L'appareil photo est utilisé par une autre appli"
                            else "Erreur de l'appareil photo ($err)"
                        )
                    }
                }, bg)
            } catch (e: Exception) {
                opening = false
                postError("Impossible d'ouvrir l'appareil photo : ${e.message}")
            }
        }
    }

    /** Arrière : le module « logique » qui couvre la plus grande plage de zoom (ultra grand-angle → téléobjectif). */
    private fun chooseCamera(wantFront: Boolean): String? {
        val wanted = if (wantFront) CameraMetadata.LENS_FACING_FRONT else CameraMetadata.LENS_FACING_BACK
        val list = manager.cameraIdList.mapNotNull { id ->
            try {
                val ci = CamInfo(id, manager.getCameraCharacteristics(id))
                if (ci.facing == wanted) ci else null
            } catch (e: Exception) {
                null
            }
        }
        if (list.isEmpty()) return manager.cameraIdList.firstOrNull()
        return list.maxByOrNull { it.zoomSpan }?.id
    }

    private fun aspect(): Float = if (ratio == "16:9") 16f / 9f else 4f / 3f

    private fun pickSize(sizes: Array<Size>, maxPixels: Long, maxW: Int = Int.MAX_VALUE): Size? {
        val a = aspect()
        val match = sizes.filter { abs(it.width.toFloat() / it.height - a) < 0.02f && it.width <= maxW && it.width.toLong() * it.height <= maxPixels }
        return match.maxByOrNull { it.width.toLong() * it.height }
            ?: sizes.filter { it.width.toLong() * it.height <= maxPixels }.maxByOrNull { it.width.toLong() * it.height }
            ?: sizes.minByOrNull { it.width.toLong() * it.height }
    }

    private fun createSession(ci: CamInfo, withRaw: Boolean) {
        val d = device ?: return
        val tv = textureView ?: return
        val st = tv.surfaceTexture ?: return
        try {
            val map = ci.c.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP) ?: run {
                postError("Configuration de l'appareil photo illisible"); return
            }
            val previewSize = pickSize(map.getOutputSizes(SurfaceTexture::class.java), 1920L * 1440, 1920)
                ?: Size(1440, 1080)
            val jpegSizes = map.getOutputSizes(ImageFormat.JPEG)
            val jpegSize = pickSize(jpegSizes, if (resolution == "max") Long.MAX_VALUE else 13_000_000L)
                ?: Size(4000, 3000)
            st.setDefaultBufferSize(previewSize.width, previewSize.height)
            val ps = Surface(st)
            previewSurface = ps

            jpegReader?.close()
            jpegReader = ImageReader.newInstance(jpegSize.width, jpegSize.height, ImageFormat.JPEG, 2).apply {
                setOnImageAvailableListener({ r -> onJpeg(r) }, bg)
            }
            rawReader?.close()
            rawReader = null
            if (withRaw) {
                val rawSize = map.getOutputSizes(ImageFormat.RAW_SENSOR)?.maxByOrNull { it.width.toLong() * it.height }
                if (rawSize != null) {
                    rawReader = ImageReader.newInstance(rawSize.width, rawSize.height, ImageFormat.RAW_SENSOR, 2).apply {
                        setOnImageAvailableListener({ r -> onRawImage(r) }, bg)
                    }
                }
            }
            val outputs = listOfNotNull(ps, jpegReader?.surface, rawReader?.surface)
            @Suppress("DEPRECATION")
            d.createCaptureSession(outputs, object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(s: CameraCaptureSession) {
                    if (device == null) return
                    session = s
                    main.post {
                        rawActive.value = rawReader != null
                        ready.value = true
                        error.value = null
                    }
                    startRepeating()
                }

                override fun onConfigureFailed(s: CameraCaptureSession) {
                    if (withRaw) {
                        main.post { onMessage?.invoke("RAW indisponible sur ce module — JPEG seul") }
                        createSession(ci, false)
                    } else postError("Impossible de démarrer l'aperçu")
                }
            }, bg)
        } catch (e: Exception) {
            if (withRaw) createSession(ci, false) else postError("Erreur d'aperçu : ${e.message}")
        }
    }

    private fun closeCamera() {
        try {
            session?.close()
        } catch (_: Exception) {
        }
        session = null
        try {
            device?.close()
        } catch (_: Exception) {
        }
        device = null
        jpegReader?.close(); jpegReader = null
        rawReader?.close(); rawReader = null
        pendingRawImage?.close(); pendingRawImage = null
        previewSurface?.release(); previewSurface = null
        step = Step.IDLE
        opening = false
        main.post {
            ready.value = false
            capturing.value = false
        }
    }

    private fun postError(msg: String) = main.post { error.value = msg; ready.value = false }

    // ---------- Réglages ----------
    fun update(p: CaptureParams) {
        if (p == params) return
        val sceneChanged = p.scene.id != params.scene.id
        params = p
        bg.post {
            if (sceneChanged) tapRegion = null
            if (step == Step.IDLE) startRepeating()
        }
    }

    /** Toucher pour faire la mise au point et l'exposition à cet endroit (x, y entre 0 et 1 dans l'aperçu). */
    fun tapToFocus(x: Float, y: Float) {
        bg.post {
            val ci = cam ?: return@post
            val s = session ?: return@post
            val d = device ?: return@post
            if (step != Step.IDLE) return@post
            // Aperçu (portrait) → coordonnées capteur
            var u = x.coerceIn(0f, 1f)
            val v = y.coerceIn(0f, 1f)
            if (ci.front) u = 1f - u
            val (sx, sy) = when (ci.sensorOrientation) {
                90 -> v to (1f - u)
                180 -> (1f - u) to (1f - v)
                270 -> (1f - v) to u
                else -> u to v
            }
            val base = if (ci.ratioZoom != null) Rect(0, 0, ci.activeArray.width(), ci.activeArray.height()) else cropRect(ci, params.zoom)
            val half = (min(base.width(), base.height()) * 0.08f).roundToInt()
            val cx = base.left + (sx * base.width()).roundToInt()
            val cy = base.top + (sy * base.height()).roundToInt()
            val r = Rect(
                (cx - half).coerceAtLeast(base.left), (cy - half).coerceAtLeast(base.top),
                (cx + half).coerceAtMost(base.right - 1), (cy + half).coerceAtMost(base.bottom - 1)
            )
            tapRegion = MeteringRectangle(r, MeteringRectangle.METERING_WEIGHT_MAX - 1)
            tapUntil = SystemClock.elapsedRealtime() + 8000
            try {
                s.stopRepeating()
                // annule l'AF en cours puis relance sur la zone
                val cancel = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                    addTarget(previewSurface!!)
                    applyCommon(this, ci, still = false)
                    set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
                }
                s.capture(cancel.build(), null, bg)
                startRepeating()
                if (ci.afModes.contains(CameraMetadata.CONTROL_AF_MODE_AUTO) && params.focus == null && !params.scene.infinity) {
                    val trig = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(previewSurface!!)
                        applyCommon(this, ci, still = false)
                        set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                    }
                    s.capture(trig.build(), null, bg)
                }
                bg.postDelayed({ if (step == Step.IDLE) startRepeating() }, 8100)
            } catch (e: Exception) {
                startRepeating()
            }
        }
    }

    private fun tapActive() = tapRegion != null && SystemClock.elapsedRealtime() < tapUntil

    private fun cropRect(ci: CamInfo, zoom: Float): Rect {
        val a = ci.activeArray
        val z = zoom.coerceIn(1f, ci.zoomMax)
        val w = (a.width() / z).roundToInt()
        val h = (a.height() / z).roundToInt()
        val l = (a.width() - w) / 2
        val t = (a.height() - h) / 2
        return Rect(l, t, l + w, t + h)
    }

    private fun <T> CaptureRequest.Builder.setIf(ok: Boolean, key: CaptureRequest.Key<T>, value: T) {
        if (ok) set(key, value)
    }

    /** Applique tous les réglages (situation, pro, qualité) à une requête. */
    private fun applyCommon(b: CaptureRequest.Builder, ci: CamInfo, still: Boolean) {
        val p = params
        val s = p.scene
        b.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)

        val manualAe = ci.manualSensor && (p.iso != null || p.shutterNs != null)

        // Mode scène matériel : HDR (contre-jour, coucher) ou priorité visage
        if (!manualAe) {
            when {
                s.hdr && CameraMetadata.CONTROL_SCENE_MODE_HDR in ci.sceneModes -> {
                    b.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE)
                    b.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_HDR)
                }
                s.faces && CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY in ci.sceneModes -> {
                    b.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_USE_SCENE_MODE)
                    b.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_FACE_PRIORITY)
                }
            }
        }

        // ----- Mise au point -----
        val manualFocusOk = ci.minFocus > 0f
        when {
            p.focus != null && manualFocusOk -> {
                b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                b.set(CaptureRequest.LENS_FOCUS_DISTANCE, p.focus.coerceIn(0f, ci.minFocus))
            }
            s.infinity && manualFocusOk -> {
                b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_OFF)
                b.set(CaptureRequest.LENS_FOCUS_DISTANCE, 0f)
            }
            tapActive() && CameraMetadata.CONTROL_AF_MODE_AUTO in ci.afModes ->
                b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
            s.macro && CameraMetadata.CONTROL_AF_MODE_MACRO in ci.afModes ->
                b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_MACRO)
            CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE in ci.afModes ->
                b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE)
            CameraMetadata.CONTROL_AF_MODE_AUTO in ci.afModes ->
                b.set(CaptureRequest.CONTROL_AF_MODE, CameraMetadata.CONTROL_AF_MODE_AUTO)
        }
        if (tapActive()) {
            tapRegion?.let { r ->
                if (ci.maxRegionsAf > 0) b.set(CaptureRequest.CONTROL_AF_REGIONS, arrayOf(r))
                if (ci.maxRegionsAe > 0) b.set(CaptureRequest.CONTROL_AE_REGIONS, arrayOf(r))
            }
        }

        // ----- Exposition -----
        if (manualAe) {
            val product = if (lastAeProduct > 0) lastAeProduct else lastIso.toDouble() * lastExp
            var exp = p.shutterNs ?: (product / (p.iso ?: lastIso)).toLong()
            exp = exp.coerceIn(ci.expRange.lower, min(ci.expRange.upper, ci.maxFrameNs))
            var iso = p.iso ?: (product / exp).roundToInt()
            iso = iso.coerceIn(ci.isoRange.lower, ci.isoRange.upper)
            if (!still && exp > 100_000_000L) {
                // l'aperçu reste fluide : pose plus courte, ISO compensé (même luminosité)
                iso = (iso * exp / 100_000_000.0).roundToInt().coerceIn(ci.isoRange.lower, ci.isoRange.upper)
                exp = 100_000_000L
            }
            b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
            b.set(CaptureRequest.SENSOR_SENSITIVITY, iso)
            b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, exp)
            b.set(CaptureRequest.SENSOR_FRAME_DURATION, exp)
            b.set(CaptureRequest.FLASH_MODE, if (p.flash == "torch" && ci.hasFlash) CameraMetadata.FLASH_MODE_TORCH else CameraMetadata.FLASH_MODE_OFF)
        } else {
            val aeMode = when {
                !ci.hasFlash -> CameraMetadata.CONTROL_AE_MODE_ON
                p.flash == "auto" -> CameraMetadata.CONTROL_AE_MODE_ON_AUTO_FLASH
                p.flash == "on" -> CameraMetadata.CONTROL_AE_MODE_ON_ALWAYS_FLASH
                else -> CameraMetadata.CONTROL_AE_MODE_ON
            }
            b.set(CaptureRequest.CONTROL_AE_MODE, aeMode)
            if (p.flash == "torch" && ci.hasFlash) b.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_TORCH)
            else b.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
            val evSteps = ((s.ev + p.evUser) / ci.evStep).roundToInt().coerceIn(ci.evRange.lower, ci.evRange.upper)
            b.set(CaptureRequest.CONTROL_AE_EXPOSURE_COMPENSATION, evSteps)
            chooseFps(ci, s.fps)?.let { b.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, it) }
        }

        // ----- Balance des blancs -----
        val awb = p.awb ?: s.awb
        b.set(
            CaptureRequest.CONTROL_AWB_MODE,
            if (awb in ci.awbModes) awb else CameraMetadata.CONTROL_AWB_MODE_AUTO
        )

        // ----- Qualité d'image maximale -----
        if (still) {
            b.setIf(CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY in ci.nrModes, CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_HIGH_QUALITY)
            b.setIf(CameraMetadata.EDGE_MODE_HIGH_QUALITY in ci.edgeModes, CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_HIGH_QUALITY)
            b.setIf(CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY in ci.aberrationModes, CaptureRequest.COLOR_CORRECTION_ABERRATION_MODE, CameraMetadata.COLOR_CORRECTION_ABERRATION_MODE_HIGH_QUALITY)
            b.setIf(CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY in ci.hotPixelModes, CaptureRequest.HOT_PIXEL_MODE, CameraMetadata.HOT_PIXEL_MODE_HIGH_QUALITY)
            b.setIf(CameraMetadata.TONEMAP_MODE_HIGH_QUALITY in ci.tonemapModes, CaptureRequest.TONEMAP_MODE, CameraMetadata.TONEMAP_MODE_HIGH_QUALITY)
            b.set(CaptureRequest.JPEG_QUALITY, 100.toByte())
            b.set(CaptureRequest.JPEG_ORIENTATION, jpegOrientation(ci))
        } else {
            b.setIf(CameraMetadata.NOISE_REDUCTION_MODE_FAST in ci.nrModes, CaptureRequest.NOISE_REDUCTION_MODE, CameraMetadata.NOISE_REDUCTION_MODE_FAST)
            b.setIf(CameraMetadata.EDGE_MODE_FAST in ci.edgeModes, CaptureRequest.EDGE_MODE, CameraMetadata.EDGE_MODE_FAST)
            val fd = when {
                CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL in ci.faceModes -> CameraMetadata.STATISTICS_FACE_DETECT_MODE_FULL
                CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE in ci.faceModes -> CameraMetadata.STATISTICS_FACE_DETECT_MODE_SIMPLE
                else -> null
            }
            fd?.let { b.set(CaptureRequest.STATISTICS_FACE_DETECT_MODE, it) }
        }
        b.setIf(CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON in ci.oisModes, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CameraMetadata.LENS_OPTICAL_STABILIZATION_MODE_ON)

        // ----- Zoom -----
        if (ci.ratioZoom != null && Build.VERSION.SDK_INT >= 30) {
            b.set(CaptureRequest.CONTROL_ZOOM_RATIO, p.zoom.coerceIn(ci.ratioZoom.lower, ci.ratioZoom.upper))
        } else if (p.zoom > 1.01f) {
            b.set(CaptureRequest.SCALER_CROP_REGION, cropRect(ci, p.zoom))
        }
    }

    private fun chooseFps(ci: CamInfo, fps: Fps): Range<Int>? {
        val r = ci.fpsRanges.filter { it.upper <= 30 }.ifEmpty { ci.fpsRanges.toList() }
        if (r.isEmpty()) return null
        return when (fps) {
            Fps.LOW -> r.filter { it.upper >= 24 }.minByOrNull { it.lower } ?: r.minByOrNull { it.lower }
            Fps.HIGH -> r.maxWithOrNull(compareBy<Range<Int>> { it.lower }.thenBy { it.upper })
            Fps.NORMAL -> r.filter { it.upper == 30 }.minByOrNull { abs(it.lower - 15) } ?: r.maxByOrNull { it.upper }
        }
    }

    private fun jpegOrientation(ci: CamInfo): Int {
        var o = ((deviceOrientation + 45) / 90 * 90) % 360
        if (ci.front) o = -o
        return (ci.sensorOrientation + o + 360) % 360
    }

    private fun startRepeating() {
        val d = device ?: return
        val s = session ?: return
        val ci = cam ?: return
        val ps = previewSurface ?: return
        try {
            val b = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            b.addTarget(ps)
            applyCommon(b, ci, still = false)
            s.setRepeatingRequest(b.build(), previewCallback, bg)
        } catch (e: CameraAccessException) {
            postError("Aperçu interrompu")
        } catch (e: IllegalStateException) {
            // session fermée entre-temps
        } catch (e: IllegalArgumentException) {
            // réglage refusé par ce module : on réessaie avec le strict minimum
            try {
                val b = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                b.addTarget(ps)
                s.setRepeatingRequest(b.build(), previewCallback, bg)
            } catch (_: Exception) {
            }
        }
    }

    // ---------- Résultats en continu ----------
    private val previewCallback = object : CameraCaptureSession.CaptureCallback() {
        override fun onCaptureCompleted(s: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
            readLive(result)
            advance(result)
        }
    }

    private fun readLive(r: CaptureResult) {
        val ci = cam ?: return
        val iso = r.get(CaptureResult.SENSOR_SENSITIVITY) ?: lastIso
        val exp = r.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: lastExp
        val aeMode = r.get(CaptureResult.CONTROL_AE_MODE)
        val aeState = r.get(CaptureResult.CONTROL_AE_STATE)
        lastIso = iso
        lastExp = exp
        if (aeMode != null && aeMode != CameraMetadata.CONTROL_AE_MODE_OFF) {
            lastAeProduct = iso.toDouble() * exp
            lastFlashRequired = aeState == CameraMetadata.CONTROL_AE_STATE_FLASH_REQUIRED
        }
        val now = SystemClock.elapsedRealtime()
        if (now - lastLivePush < 160) return
        lastLivePush = now
        val ap = r.get(CaptureResult.LENS_APERTURE) ?: ci.aperture
        val fd = r.get(CaptureResult.LENS_FOCUS_DISTANCE) ?: 0f
        val faces = r.get(CaptureResult.STATISTICS_FACES)
        val area = ci.activeArray.width().toFloat() * ci.activeArray.height()
        val faceArea = faces?.maxOfOrNull { it.bounds.width().toFloat() * it.bounds.height() / area } ?: 0f
        val afState = r.get(CaptureResult.CONTROL_AF_STATE)
        val l = LiveInfo(
            iso = iso,
            exposureNs = exp,
            aperture = ap,
            focusDiopters = fd,
            faces = faces?.size ?: 0,
            faceArea = faceArea,
            ev100 = SceneDetector.ev100(ap, exp, iso),
            aeProduct = lastAeProduct,
            flashRequired = lastFlashRequired,
            afLocked = afState == CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED,
        )
        main.post { live.value = l }
    }

    // ---------- Déclenchement ----------
    fun takePicture(meta: ShotMeta) {
        bg.post {
            val ci = cam ?: return@post
            val s = session ?: return@post
            val d = device ?: return@post
            if (step != Step.IDLE) return@post
            shotMeta = meta
            main.post { capturing.value = true }
            val p = params
            val afRunning = p.focus == null && !p.scene.infinity &&
                (ci.afModes.contains(CameraMetadata.CONTROL_AF_MODE_CONTINUOUS_PICTURE) || ci.afModes.contains(CameraMetadata.CONTROL_AF_MODE_AUTO))
            try {
                if (afRunning) {
                    step = Step.WAIT_AF
                    val b = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                    b.addTarget(previewSurface!!)
                    applyCommon(b, ci, still = false)
                    b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
                    s.capture(b.build(), previewCallback, bg)
                    armTimeout(900) { afterFocus() }
                } else afterFocus()
            } catch (e: Exception) {
                failShot("Échec de la prise de vue")
            }
        }
    }

    private fun armTimeout(ms: Long, then: () -> Unit) {
        val token = ++stepToken
        bg.postDelayed({ if (token == stepToken && step != Step.IDLE && step != Step.SHOOTING) then() }, ms)
    }

    private fun flashWillFire(): Boolean {
        val p = params
        val ci = cam ?: return false
        if (!ci.hasFlash || (p.iso != null || p.shutterNs != null)) return false
        return p.flash == "on" || (p.flash == "auto" && lastFlashRequired)
    }

    private fun afterFocus() {
        val ci = cam ?: return
        val s = session ?: return
        val d = device ?: return
        if (flashWillFire()) {
            try {
                step = Step.WAIT_PRE
                val b = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                b.addTarget(previewSurface!!)
                applyCommon(b, ci, still = false)
                b.set(CaptureRequest.CONTROL_AE_PRECAPTURE_TRIGGER, CameraMetadata.CONTROL_AE_PRECAPTURE_TRIGGER_START)
                s.capture(b.build(), previewCallback, bg)
                armTimeout(1500) { shoot() }
            } catch (e: Exception) {
                shoot()
            }
        } else shoot()
    }

    private fun advance(r: CaptureResult) {
        when (step) {
            Step.WAIT_AF -> {
                val af = r.get(CaptureResult.CONTROL_AF_STATE) ?: return afterFocus()
                if (af == CameraMetadata.CONTROL_AF_STATE_FOCUSED_LOCKED ||
                    af == CameraMetadata.CONTROL_AF_STATE_NOT_FOCUSED_LOCKED ||
                    af == CameraMetadata.CONTROL_AF_STATE_INACTIVE
                ) afterFocus()
            }
            Step.WAIT_PRE -> {
                val ae = r.get(CaptureResult.CONTROL_AE_STATE)
                if (ae == null || ae == CameraMetadata.CONTROL_AE_STATE_PRECAPTURE) {
                    step = Step.WAIT_PRE_DONE
                    armTimeout(1500) { shoot() }
                } else if (ae == CameraMetadata.CONTROL_AE_STATE_CONVERGED || ae == CameraMetadata.CONTROL_AE_STATE_FLASH_REQUIRED) {
                    shoot()
                }
            }
            Step.WAIT_PRE_DONE -> {
                val ae = r.get(CaptureResult.CONTROL_AE_STATE)
                if (ae == null || ae != CameraMetadata.CONTROL_AE_STATE_PRECAPTURE) shoot()
            }
            else -> {}
        }
    }

    /**
     * Stratégie qualité : à partir de la lumière mesurée (ISO × temps), choisit le couple ISO/vitesse idéal
     * pour la situation : ISO le plus bas possible (moins de bruit) tant que la pose reste nette à main levée,
     * pose longue si le téléphone est posé, ou vitesse rapide pour figer le mouvement.
     */
    fun planExposure(ci: CamInfo, p: CaptureParams): ExposurePlan? {
        if (!ci.manualSensor || p.iso != null || p.shutterNs != null) return null
        if (flashWillFire() || lastAeProduct <= 0.0) return null
        val s = p.scene
        // Les modes HDR matériels gèrent eux-mêmes leur exposition
        if (s.hdr && CameraMetadata.CONTROL_SCENE_MODE_HDR in ci.sceneModes) return null
        val e = lastAeProduct
        val isoMin = ci.isoRange.lower
        val isoMax = ci.isoRange.upper
        val expMax = min(ci.expRange.upper, ci.maxFrameNs)
        val zoomFactor = max(1f, p.zoom)
        // vitesse de sécurité à main levée, raccourcie au zoom et si les mains tremblent
        var handheld = s.handheldMaxMs * 1_000_000.0 / zoomFactor
        if (shake > 0.15f) handheld /= 2.0
        val tripodOk = tripod && p.allowTripod && s.tripodMaxMs > 0f
        var limit = if (tripodOk) s.tripodMaxMs * 1_000_000.0 else handheld
        s.motionMaxMs?.let { limit = min(limit, it * 1_000_000.0) }
        limit = limit.coerceIn(ci.expRange.lower.toDouble(), expMax.toDouble())

        var t = (e / isoMin).coerceAtMost(limit).coerceAtLeast(ci.expRange.lower.toDouble())
        var iso = (e / t)
        val cap = max(isoMin, min(s.isoCap, isoMax))
        if (iso > cap && !tripodOk) {
            // trop sombre pour la limite : on accepte un peu plus de pose plutôt que trop de bruit (sauf mouvement)
            if (s.motionMaxMs == null) t = min(e / cap, handheld * 1.5).coerceAtMost(expMax.toDouble())
            iso = e / t
        }
        val isoI = iso.roundToInt().coerceIn(isoMin, isoMax)
        val tL = t.toLong().coerceIn(ci.expRange.lower, expMax)

        val sameIso = SceneDetector.nearlySame(isoI.toDouble(), lastIso.toDouble().coerceAtLeast(1.0))
        val sameT = SceneDetector.nearlySame(tL.toDouble(), lastExp.toDouble().coerceAtLeast(1.0))
        if (sameIso && sameT) return null
        if (!p.smartIso && !tripodOk && s.motionMaxMs == null) return null

        val note = when {
            tripodOk && tL > 150_000_000L -> "Téléphone posé : pose ${Fmt.shutter(tL)} à ISO $isoI"
            tL < lastExp -> "Vitesse ${Fmt.shutter(tL)} pour figer le mouvement"
            isoI < lastIso -> "ISO $lastIso → $isoI : image plus propre"
            else -> "ISO $isoI · ${Fmt.shutter(tL)}"
        }
        return ExposurePlan(isoI, tL, note)
    }

    private fun shoot() {
        val ci = cam ?: return failShot("Appareil fermé")
        val s = session ?: return failShot("Appareil fermé")
        val d = device ?: return failShot("Appareil fermé")
        val jr = jpegReader ?: return failShot("Appareil fermé")
        step = Step.SHOOTING
        stepToken++
        try {
            val b = d.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE)
            b.addTarget(jr.surface)
            rawReader?.let { b.addTarget(it.surface) }
            applyCommon(b, ci, still = true)
            b.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CameraMetadata.CONTROL_CAPTURE_INTENT_STILL_CAPTURE)
            val plan = planExposure(ci, params)
            if (plan != null) {
                b.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
                b.set(CaptureRequest.CONTROL_AE_MODE, CameraMetadata.CONTROL_AE_MODE_OFF)
                b.set(CaptureRequest.SENSOR_SENSITIVITY, plan.iso)
                b.set(CaptureRequest.SENSOR_EXPOSURE_TIME, plan.exposureNs)
                b.set(CaptureRequest.SENSOR_FRAME_DURATION, plan.exposureNs)
                b.set(CaptureRequest.FLASH_MODE, CameraMetadata.FLASH_MODE_OFF)
            }
            val longMs = (plan?.exposureNs ?: params.shutterNs ?: 0L) / 1_000_000L
            val meta = (shotMeta ?: ShotMeta()).copy(plan = plan?.note, orientation = jpegOrientation(ci))
            shotMeta = meta
            pendingRawMeta = meta
            main.post { captureNote.value = plan?.note ?: if (longMs > 300) "Ne bouge pas…" else null }
            s.capture(b.build(), object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(ss: CameraCaptureSession, r: CaptureRequest, result: TotalCaptureResult) {
                    val info = ShotInfo(
                        iso = result.get(CaptureResult.SENSOR_SENSITIVITY) ?: 0,
                        exposureNs = result.get(CaptureResult.SENSOR_EXPOSURE_TIME) ?: 0L,
                        aperture = result.get(CaptureResult.LENS_APERTURE) ?: ci.aperture,
                        flash = result.get(CaptureResult.FLASH_STATE) == CameraMetadata.FLASH_STATE_FIRED,
                        zoom = params.zoom,
                        meta = meta,
                        raw = rawReader != null,
                        at = SystemClock.elapsedRealtime(),
                    )
                    main.post { lastShot.value = info }
                    if (rawReader != null) {
                        pendingRawResult = result
                        tryWriteRaw()
                    }
                    finishShot()
                }

                override fun onCaptureFailed(ss: CameraCaptureSession, r: CaptureRequest, f: android.hardware.camera2.CaptureFailure) {
                    failShot("La photo n'a pas pu être prise")
                }
            }, bg)
        } catch (e: Exception) {
            failShot("Échec de la prise de vue : ${e.message}")
        }
    }

    private fun finishShot() {
        val ci = cam
        val s = session
        val d = device
        step = Step.IDLE
        // relâche le verrou de mise au point et relance l'aperçu
        if (ci != null && s != null && d != null) {
            try {
                val b = d.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                b.addTarget(previewSurface!!)
                applyCommon(b, ci, still = false)
                b.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
                s.capture(b.build(), null, bg)
            } catch (_: Exception) {
            }
            startRepeating()
        }
        main.post { capturing.value = false; captureNote.value = null }
    }

    private fun failShot(msg: String) {
        step = Step.IDLE
        stepToken++
        startRepeating()
        main.post {
            capturing.value = false
            captureNote.value = null
            onMessage?.invoke(msg)
        }
    }

    private fun onJpeg(r: ImageReader) {
        val img = try {
            r.acquireNextImage()
        } catch (e: Exception) {
            null
        } ?: return
        val bytes = try {
            val buf = img.planes[0].buffer
            ByteArray(buf.remaining()).also { buf.get(it) }
        } finally {
            img.close()
        }
        val meta = shotMeta ?: ShotMeta()
        main.post { onPhoto?.invoke(PhotoJob(bytes, meta)) }
    }

    private fun onRawImage(r: ImageReader) {
        val img = try {
            r.acquireNextImage()
        } catch (e: Exception) {
            null
        } ?: return
        pendingRawImage?.close()
        pendingRawImage = img
        tryWriteRaw()
    }

    private fun tryWriteRaw() {
        val img = pendingRawImage ?: return
        val res = pendingRawResult ?: return
        val ci = cam ?: return
        pendingRawImage = null
        pendingRawResult = null
        val cb = onRaw
        if (cb == null) {
            img.close(); return
        }
        // DngCreator a besoin de l'image ouverte : écrit directement sur ce fil
        cb(RawJob(ci.c, res, img, pendingRawMeta ?: ShotMeta()))
    }
}

/** Infos jointes à une photo (pour le nom, les métadonnées et le style). */
data class ShotMeta(
    val scene: String = "",
    val look: String = "naturel",
    val lookAmount: Float = 1f,
    val plan: String? = null,
    val orientation: Int = 0,
)

data class ShotInfo(
    val iso: Int,
    val exposureNs: Long,
    val aperture: Float,
    val flash: Boolean,
    val zoom: Float,
    val meta: ShotMeta,
    val raw: Boolean,
    val at: Long,
)

class PhotoJob(val jpeg: ByteArray, val meta: ShotMeta)
class RawJob(val chars: CameraCharacteristics, val result: TotalCaptureResult, val image: Image, val meta: ShotMeta)

object Fmt {
    fun shutter(ns: Long): String {
        if (ns <= 0) return "—"
        val s = ns / 1e9
        if (s < 0.3) return "1/${(1 / s).roundToInt()}"
        val r = (s * 10).roundToInt()
        return if (r % 10 == 0) "${r / 10} s" else "${r / 10},${r % 10} s"
    }

    fun ev(v: Float): String = (if (v > 0) "+" else if (v < 0) "−" else "") + "%.1f".format(abs(v))
}
