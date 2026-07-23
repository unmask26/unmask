package com.example.unmask.features.ar

import android.content.Context
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.compose.ui.graphics.Color
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.core.Delegate
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarker
import com.google.mediapipe.tasks.vision.facelandmarker.FaceLandmarkerResult
import kotlin.math.atan2
import kotlin.math.hypot

// ─── Data types ───────────────────────────────────────────────────────────────

data class Keypoint(val x: Float, val y: Float)

data class FaceGeometryData(
    val landmarks: List<Keypoint>,
    val rawWidth: Int,
    val rawHeight: Int,
    val rotation: Int,
    val analysisSensorToBuffer: Matrix
)

enum class MaskCategory { FEMININE, MASCULINE }

data class PartyMaskInfo(
    val id: String,
    val name: String,
    val icon: String,
    val category: MaskCategory,
    val badgeColor: Color
)

class SmoothedFaceMetrics {
    @Volatile var eyeMidX: Float = 0f
    @Volatile var eyeMidY: Float = 0f
    @Volatile var eyeDistance: Float = 0f
    @Volatile var rollAngle: Float = 0f
    @Volatile var isInitialized: Boolean = false

    fun update(
        targetMidX: Float,
        targetMidY: Float,
        targetDist: Float,
        targetAngle: Float,
        alpha: Float = 0.35f
    ) {
        if (!isInitialized) {
            eyeMidX    = targetMidX
            eyeMidY    = targetMidY
            eyeDistance = targetDist
            rollAngle  = targetAngle
            isInitialized = true
        } else {
            eyeMidX     += (targetMidX    - eyeMidX)     * alpha
            eyeMidY     += (targetMidY    - eyeMidY)     * alpha
            eyeDistance += (targetDist    - eyeDistance) * alpha
            var diff = (targetAngle - rollAngle) % 360f
            if (diff >  180f) diff -= 360f
            if (diff < -180f) diff += 360f
            rollAngle += diff * alpha
        }
    }

    fun reset() { isInitialized = false }
}

// ─── PartyMaskEngine ──────────────────────────────────────────────────────────

object PartyMaskEngine {

    const val FRAME_INTERVAL_NS = 66_666_666L

    // ── 10 Custom Video Party Masks ───────────────────────────────────────────

    val PARTY_MASKS: List<PartyMaskInfo> = listOf(
        // 5 Feminine Masks
        PartyMaskInfo("glamour_star",      "Glamour Star",      "🌟", MaskCategory.FEMININE,  Color(0xFFFFD700)),
        PartyMaskInfo("mystic_fairy",      "Mystic Fairy",      "🧚‍♀️", MaskCategory.FEMININE,  Color(0xFFE040FB)),
        PartyMaskInfo("cyber_kawaii",      "Cyber Kawaii",      "🐱", MaskCategory.FEMININE,  Color(0xFFFF4081)),
        PartyMaskInfo("vintage_diva",      "Vintage Diva",      "💃", MaskCategory.FEMININE,  Color(0xFFC2185B)),
        PartyMaskInfo("celestial_goddess", "Celestial Goddess", "🌙", MaskCategory.FEMININE,  Color(0xFFE0E0E0)),

        // 5 Masculine Masks
        PartyMaskInfo("phantom_gentleman", "Phantom Gentleman", "🎩", MaskCategory.MASCULINE, Color(0xFF37474F)),
        PartyMaskInfo("neon_samurai",      "Neon Samurai",      "👹", MaskCategory.MASCULINE, Color(0xFF00E676)),
        PartyMaskInfo("cyberpunk_hacker",  "Cyberpunk Hacker",  "💻", MaskCategory.MASCULINE, Color(0xFF00E5FF)),
        PartyMaskInfo("viking_warrior",    "Viking Warrior",    "🪓", MaskCategory.MASCULINE, Color(0xFF8D6E63)),
        PartyMaskInfo("stealth_agent",     "Stealth Agent",     "🕶️", MaskCategory.MASCULINE, Color(0xFF263238))
    )

    fun createFaceLandmarker(
        context: Context,
        listener: (FaceLandmarkerResult, com.google.mediapipe.framework.image.MPImage) -> Unit
    ): FaceLandmarker? {
        return try {
            buildLandmarker(context, Delegate.GPU, listener)
        } catch (e: Exception) {
            e.printStackTrace()
            try { buildLandmarker(context, Delegate.CPU, listener) }
            catch (ex: Exception) { ex.printStackTrace(); null }
        }
    }

    private fun buildLandmarker(
        context: Context,
        delegate: Delegate,
        listener: (FaceLandmarkerResult, com.google.mediapipe.framework.image.MPImage) -> Unit
    ): FaceLandmarker {
        val baseOptions = BaseOptions.builder()
            .setModelAssetPath("face_landmarker.task")
            .setDelegate(delegate)
            .build()
        val options = FaceLandmarker.FaceLandmarkerOptions.builder()
            .setBaseOptions(baseOptions)
            .setMinFaceDetectionConfidence(0.5f)
            .setMinTrackingConfidence(0.5f)
            .setMinFacePresenceConfidence(0.5f)
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener(listener)
            .setErrorListener { it.printStackTrace() }
            .build()
        return FaceLandmarker.createFromOptions(context, options)
    }

    // ── Mask Specs ────────────────────────────────────────────────────────────

    data class MaskSpec(
        val basePath: Path,
        val fillColor: Int,
        val strokeColor: Int,
        val strokeWidthRatio: Float = 0.05f,
        val accentPath: Path? = null,
        val accentColor: Int = android.graphics.Color.WHITE,
        val accentStroke: Boolean = false
    )

    private val maskSpecs = HashMap<String, MaskSpec>()

    init { initMaskSpecs() }

    private fun initMaskSpecs() {
        val leftHole  = Path().apply { addCircle(-0.5f, 0f, 0.22f, Path.Direction.CW) }
        val rightHole = Path().apply { addCircle( 0.5f, 0f, 0.22f, Path.Direction.CW) }

        fun createPartyMask(
            base: Path,
            fill: String,
            stroke: String,
            accent: Path? = null,
            accentFill: String = "#FFFFFF",
            accentStroke: Boolean = false,
            strokeWidthRatio: Float = 0.05f,
            subtractEyeHoles: Boolean = true
        ): MaskSpec {
            val maskPath = if (subtractEyeHoles) {
                Path().apply {
                    op(base, leftHole,  Path.Op.DIFFERENCE)
                    op(this, rightHole, Path.Op.DIFFERENCE)
                }
            } else base

            return MaskSpec(
                basePath         = maskPath,
                fillColor        = android.graphics.Color.parseColor(fill),
                strokeColor      = android.graphics.Color.parseColor(stroke),
                strokeWidthRatio = strokeWidthRatio,
                accentPath       = accent,
                accentColor      = android.graphics.Color.parseColor(accentFill),
                accentStroke     = accentStroke
            )
        }

        // 1. Glamour Star (Full Hollywood Gold Masquerade Mask with Star & Pearl Tiara)
        val glamourBase = Path().apply {
            moveTo(0f, -1.1f)
            cubicTo( 0.7f, -1.1f,  1.4f, -0.8f,  1.6f, -0.2f)
            cubicTo( 1.7f,  0.3f,  1.3f,  0.75f, 0.6f,  0.7f)
            cubicTo( 0.2f,  0.65f, 0f,    0.45f, 0f,    0.45f)
            cubicTo( 0f,    0.45f,-0.2f,  0.65f,-0.6f,  0.7f)
            cubicTo(-1.3f,  0.75f,-1.7f,  0.3f, -1.6f, -0.2f)
            cubicTo(-1.4f, -0.8f, -0.7f, -1.1f,  0f,   -1.1f)
            close()
        }
        val glamourTiara = Path().apply {
            // Pearl hairline tiara crown
            moveTo(-1.2f, -0.9f); cubicTo(-0.6f, -1.4f, 0.6f, -1.4f, 1.2f, -0.9f)
            // Star gems at temples
            addCircle(-1.45f, -0.3f, 0.15f, Path.Direction.CW)
            addCircle( 1.45f, -0.3f, 0.15f, Path.Direction.CW)
        }
        maskSpecs["glamour_star"] = createPartyMask(glamourBase, "#EED4AF37", "#FFD700", glamourTiara, "#FFF8DC", accentStroke = false, strokeWidthRatio = 0.06f)

        // 2. Mystic Fairy (Pastel Butterfly Wings & Floral Crown)
        val fairyWings = Path().apply {
            moveTo(0f, -0.2f)
            cubicTo( 0.4f, -1.05f, 1.35f, -1.25f, 1.75f, -0.65f)
            cubicTo( 2.0f, -0.25f, 1.75f,  0.55f, 1.2f,   0.65f)
            cubicTo( 0.6f,  0.75f, 0f,     0.45f, 0f,     0.45f)
            cubicTo( 0f,    0.45f,-0.6f,   0.75f,-1.2f,   0.65f)
            cubicTo(-1.75f, 0.55f,-2.0f,  -0.25f,-1.75f, -0.65f)
            cubicTo(-1.35f,-1.25f,-0.4f,  -1.05f, 0f,    -0.2f)
            close()
        }
        val fairyFlowers = Path().apply {
            addCircle(-0.5f, -1.05f, 0.16f, Path.Direction.CW)
            addCircle( 0f,   -1.25f, 0.22f, Path.Direction.CW)
            addCircle( 0.5f, -1.05f, 0.16f, Path.Direction.CW)
        }
        maskSpecs["mystic_fairy"] = createPartyMask(fairyWings, "#CCE040FB", "#FF00FF", fairyFlowers, "#FF69B4", strokeWidthRatio = 0.05f)

        // 3. Cyber Kawaii (Full Neon Face Mask + Cat Ears + Cheek Heart Stickers)
        val kawaiiFace = Path().apply {
            addRoundRect(RectF(-1.4f, -0.65f, 1.4f, 0.75f), 0.35f, 0.35f, Path.Direction.CW)
        }
        val kawaiiAccents = Path().apply {
            // Cat ears on forehead
            moveTo(-0.75f, -0.65f); lineTo(-1.25f, -1.4f); lineTo(-0.25f, -0.65f); close()
            moveTo( 0.25f, -0.65f); lineTo( 1.25f, -1.4f); lineTo( 0.75f, -0.65f); close()
            // Heart cheek stickers
            addCircle(-1.0f, 0.45f, 0.14f, Path.Direction.CW)
            addCircle( 1.0f, 0.45f, 0.14f, Path.Direction.CW)
        }
        maskSpecs["cyber_kawaii"] = createPartyMask(kawaiiFace, "#EEFF4081", "#00E5FF", kawaiiAccents, "#FF80AB", strokeWidthRatio = 0.06f)

        // 4. Vintage Diva (1920s Art Deco Venetian Lace & Feather Plume)
        val divaMask = Path().apply {
            moveTo(0f, -0.6f)
            cubicTo( 0.6f, -0.9f, 1.4f, -0.8f, 1.65f, -0.2f)
            cubicTo( 1.8f,  0.35f, 1.3f,  0.8f,  0.65f,  0.75f)
            cubicTo( 0.2f,  0.7f,  0f,    0.45f, 0f,     0.45f)
            cubicTo( 0f,    0.45f,-0.2f,  0.7f, -0.65f,  0.75f)
            cubicTo(-1.3f,  0.8f, -1.8f,  0.35f,-1.65f, -0.2f)
            cubicTo(-1.4f, -0.8f, -0.6f, -0.9f,  0f,    -0.6f)
            close()
        }
        val divaPlume = Path().apply {
            // Large asymmetric feather plume at left temple
            moveTo(-1.4f, -0.3f)
            cubicTo(-1.9f, -0.9f, -2.1f, -1.6f, -1.6f, -1.7f)
            cubicTo(-1.2f, -1.4f, -1.1f, -0.8f, -1.1f, -0.3f)
            close()
        }
        maskSpecs["vintage_diva"] = createPartyMask(divaMask, "#EE1A1A1A", "#C2185B", divaPlume, "#FFD700", strokeWidthRatio = 0.07f)

        // 5. Celestial Goddess (Silver Goddess Mask & Crescent Moon Crown)
        val goddessMask = Path().apply {
            addOval(RectF(-1.4f, -0.75f, 1.4f, 0.75f), Path.Direction.CW)
        }
        val moonCrown = Path().apply {
            addCircle(0f, -1.15f, 0.3f, Path.Direction.CW)
            addCircle(-1.0f, -0.95f, 0.12f, Path.Direction.CW)
            addCircle( 1.0f, -0.95f, 0.12f, Path.Direction.CW)
        }
        maskSpecs["celestial_goddess"] = createPartyMask(goddessMask, "#EAE0E0E0", "#FFFFFF", moonCrown, "#E0F7FA", strokeWidthRatio = 0.05f)

        // 6. Phantom Gentleman (Half Opera Mask covering side of face)
        val phantomHalfMask = Path().apply {
            moveTo(-0.1f, -1.1f)
            cubicTo( 0.7f, -1.1f,  1.4f, -0.7f, 1.55f, -0.1f)
            cubicTo( 1.65f, 0.45f,  1.2f,  0.95f, 0.5f,   0.9f)
            cubicTo( 0.1f,  0.85f, -0.1f,  0.5f, -0.1f,  0.5f)
            close()
        }
        val phantomFiligree = Path().apply {
            moveTo(0.5f, -0.8f); lineTo(1.2f, -0.3f); lineTo(0.8f, 0.3f)
        }
        maskSpecs["phantom_gentleman"] = createPartyMask(phantomHalfMask, "#EE263238", "#CFD8DC", phantomFiligree, "#FFD700", accentStroke = true, strokeWidthRatio = 0.06f)

        // 7. Neon Samurai (Japanese Oni Demon Mask with Horns & Armor)
        val samuraiMask = Path().apply {
            moveTo(0f, -0.95f)
            lineTo( 1.35f, -0.6f); lineTo( 1.45f, 0.45f); lineTo( 0.85f, 1.15f)
            lineTo( 0f,     0.75f)
            lineTo(-0.85f,  1.15f); lineTo(-1.45f, 0.45f); lineTo(-1.35f, -0.6f)
            close()
        }
        val samuraiHorns = Path().apply {
            // Demon horns extending upwards
            moveTo(-0.65f, -0.85f); lineTo(-1.15f, -1.55f); lineTo(-0.35f, -0.9f); close()
            moveTo( 0.35f, -0.9f);  lineTo( 1.15f, -1.55f); lineTo( 0.65f, -0.85f); close()
        }
        maskSpecs["neon_samurai"] = createPartyMask(samuraiMask, "#EE1B5E20", "#00E676", samuraiHorns, "#FF3D00", accentStroke = false, strokeWidthRatio = 0.06f)

        // 8. Cyberpunk Hacker (Cybernetic Visor & Matrix Circuit Overlay)
        val hackerVisor = Path().apply {
            addRoundRect(RectF(-1.45f, -0.5f, 1.45f, 0.5f), 0.15f, 0.15f, Path.Direction.CW)
        }
        val matrixGrid = Path().apply {
            moveTo(-1.35f, 0f); lineTo(1.35f, 0f)
            moveTo(-1.35f, -0.2f); lineTo(1.35f, -0.2f)
            moveTo(-1.35f,  0.2f); lineTo(1.35f,  0.2f)
        }
        maskSpecs["cyberpunk_hacker"] = createPartyMask(hackerVisor, "#DD002B36", "#00E5FF", matrixGrid, "#00FF66", accentStroke = true, strokeWidthRatio = 0.05f)

        // 9. Viking Warrior (Bronzed Helmet & War Paint Mask)
        val vikingHelmet = Path().apply {
            moveTo(0f, -1.2f)
            lineTo( 1.35f, -0.7f); lineTo( 1.4f, 0.4f); lineTo( 0f, 0.15f); lineTo(-1.4f, 0.4f); lineTo(-1.35f, -0.7f)
            close()
        }
        val vikingRunes = Path().apply {
            // War paint stripes across cheeks
            moveTo(-1.2f, 0.45f); lineTo(-0.4f, 0.75f)
            moveTo( 0.4f, 0.75f); lineTo( 1.2f, 0.45f)
        }
        maskSpecs["viking_warrior"] = createPartyMask(vikingHelmet, "#DD4E3629", "#8D6E63", vikingRunes, "#FF3D00", accentStroke = true, strokeWidthRatio = 0.07f)

        // 10. Stealth Agent (Full Tactical Black Mask & Thermal HUD Lens)
        val stealthMask = Path().apply {
            addRoundRect(RectF(-1.4f, -0.6f, 1.4f, 0.95f), 0.3f, 0.3f, Path.Direction.CW)
        }
        val thermalHud = Path().apply {
            // Thermal HUD ring around right eye
            addCircle(0.5f, 0f, 0.32f, Path.Direction.CW)
            addRect(RectF(-1.2f, -0.45f, -0.9f, -0.25f), Path.Direction.CW)
        }
        maskSpecs["stealth_agent"] = createPartyMask(stealthMask, "#F5111111", "#37474F", thermalHud, "#FF1744", accentStroke = true, strokeWidthRatio = 0.05f)
    }

    // ── Paint Objects ─────────────────────────────────────────────────────────

    private val paintFill   = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val paintAccent = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val paintAccentStroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    // ── Core Render Function ──────────────────────────────────────────────────

    fun renderMask(
        canvas: Canvas,
        filterId: String,
        face: FaceGeometryData,
        overlaySensorToCanvas: Matrix,
        metrics: SmoothedFaceMetrics
    ) {
        val spec = maskSpecs[filterId] ?: return
        if (face.landmarks.size < 468) return

        // MediaPipe normalizes landmarks to [0,1] in the image space AFTER
        // applying the rotation passed via setRotationDegrees. So the
        // normalized coordinates are in the "upright" phone view — we must NOT
        // un-rotate them again. We simply denormalize to the image buffer size.
        val rw = face.rawWidth.toFloat()
        val rh = face.rawHeight.toFloat()

        // Pick the key landmarks we need:
        //   33  = right outer eye corner
        //  263  = left outer eye corner
        //    1  = nose tip (used for vertical face centre)
        //  152  = chin bottom
        val lm33  = face.landmarks[33]
        val lm263 = face.landmarks[263]
        val lmNose = face.landmarks[1]
        val lmChin = face.landmarks[152]

        // Denormalize all 6 floats in one array, then transform together.
        // [0,1] = eye33   [2,3] = eye263   [4,5] = nose   [6,7] = chin
        val pts = FloatArray(8)
        pts[0] = lm33.x * rw;   pts[1] = lm33.y * rh
        pts[2] = lm263.x * rw;  pts[3] = lm263.y * rh
        pts[4] = lmNose.x * rw; pts[5] = lmNose.y * rh
        pts[6] = lmChin.x * rw; pts[7] = lmChin.y * rh

        // Build the single transform: imageBuffer → sensor → overlayCanvas
        val bufToSensor = Matrix()
        if (!face.analysisSensorToBuffer.invert(bufToSensor)) return
        val bufToCanvas = Matrix(bufToSensor)
        bufToCanvas.postConcat(overlaySensorToCanvas)
        bufToCanvas.mapPoints(pts)

        val e33x  = pts[0]; val e33y  = pts[1]   // right eye on canvas
        val e263x = pts[2]; val e263y = pts[3]   // left eye on canvas
        val noseX = pts[4]; val noseY = pts[5]   // nose tip on canvas
        val chinX = pts[6]; val chinY = pts[7]   // chin on canvas

        // ── Centre: midpoint of the eye line ───────────────────────────────────
        val targetMidX = (e33x + e263x) / 2f
        val targetMidY = (e33y + e263y) / 2f

        // ── Scale: use the inter-eye distance as the canonical 1-unit size.
        //   This is what the mask path coords are drawn relative to (1.0 = half
        //   face width).  Multiply by a fixed factor so the mask covers the
        //   whole face rather than just the eye strip.
        val eyeDist = hypot((e33x - e263x).toDouble(), (e33y - e263y).toDouble()).toFloat()

        // ── Roll: angle of the eye line on screen ─────────────────────────────
        //   We want the mask X-axis to align with the eye line.
        val dX = e33x - e263x
        val dY = e33y - e263y
        val targetAngle = Math.toDegrees(atan2(dY.toDouble(), dX.toDouble())).toFloat()

        if (eyeDist < 1f) {
            if (!metrics.isInitialized) return
        } else {
            metrics.update(targetMidX, targetMidY, eyeDist, targetAngle)
        }
        if (!metrics.isInitialized) return

        paintFill.color         = spec.fillColor
        paintStroke.color       = spec.strokeColor
        paintStroke.strokeWidth = spec.strokeWidthRatio   // already in normalised units

        val saved = canvas.save()
        try {
            canvas.translate(metrics.eyeMidX, metrics.eyeMidY)
            canvas.rotate(metrics.rollAngle)
            // scale so that 1 unit == inter-eye distance, shrunk by 30%
            val scaledDist = metrics.eyeDistance * 0.7f
            canvas.scale(scaledDist, scaledDist)

            canvas.drawPath(spec.basePath, paintFill)
            canvas.drawPath(spec.basePath, paintStroke)

            spec.accentPath?.let { acc ->
                if (spec.accentStroke) {
                    paintAccentStroke.color       = spec.accentColor
                    paintAccentStroke.strokeWidth = spec.strokeWidthRatio * 0.8f
                    canvas.drawPath(acc, paintAccentStroke)
                } else {
                    paintAccent.color = spec.accentColor
                    canvas.drawPath(acc, paintAccent)
                }
            }
        } finally {
            canvas.restoreToCount(saved)
        }
    }
}
