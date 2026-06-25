package com.gustavo.brilhante.apptestgoogleadmanager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.libraries.ads.mobile.sdk.nativead.CustomNativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdAssetNames
import kotlin.math.sqrt

private const val TAG = "NativeEmbaixadinha"

// Physics constants expressed in dp/s or dp/s² to be density-independent
private const val GRAVITY               = 620f   // dp/s²
private const val BOUNCE_IMPULSE        = -700f  // dp/s  (negative = upward)
private const val MAX_VELOCITY_X        = 370f   // dp/s
private const val HORIZONTAL_FACTOR     = 2.6f   // scales dp-distance to dp/s
private const val WALL_DAMPING          = 0.60f  // velocity kept after lateral wall bounce
private const val CEILING_DAMPING       = 0.55f  // velocity kept after ceiling bounce
private const val FLOOR_DAMPING         = 0.38f  // velocity kept after floor bounce
private const val FLOOR_FRICTION        = 0.78f  // horizontal damping on each floor contact
private const val FLOOR_REST_THRESHOLD  = 35f    // dp/s — below this, ball is considered at rest
private const val BALL_RADIUS           = 40f    // dp
private const val CURSOR_RADIUS         = 30f    // dp  (visual + collision)
private const val COLLISION_DIST        = BALL_RADIUS + CURSOR_RADIUS  // dp

private val DEFAULT_BACKGROUND_COLOR = Color(0xFF1B5E20)
private val DEFAULT_CTA_COLOR        = Color(0xFF388E3C)
private val DEFAULT_SCORE_COLOR      = Color.White

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NativeEmbaixadinhaScreen(viewModel: BakingViewModel) {
    val adsState by viewModel.embaixadinhaAdsState.collectAsState()
    val adError  by viewModel.adError.collectAsState()

    when {
        adError != null && adsState.isEmpty() -> NativeEmbaixadinhaError(adError!!)
        adsState.isNotEmpty()                 -> NativeEmbaixadinhaGame(ad = adsState.first())
        else                                  -> NativeEmbaixadinhaLoading()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Loading / Error states
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NativeEmbaixadinhaLoading() {
    Box(
        modifier = Modifier.fillMaxSize().background(DEFAULT_BACKGROUND_COLOR),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = "Carregando Embaixadinha...", color = Color.White, fontSize = 16.sp)
        }
    }
}

@Composable
private fun NativeEmbaixadinhaError(error: String) {
    Box(
        modifier = Modifier.fillMaxSize().background(Color(0xFF1B0000)),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Falha ao carregar anúncio:\n$error",
            color = Color(0xFFFF8A80),
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(24.dp)
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Game screen
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun NativeEmbaixadinhaGame(ad: CustomNativeAd) {

    // ── Asset loading ────────────────────────────────────────────────────────
    val backgroundBitmap = remember(ad) { loadAdImage(ad, "background", "background") }
    val ballBitmap       = remember(ad) { loadAdImage(ad, "ballImg",    "ballImg")    }
    val ctaText          = remember(ad) { loadAdText(ad,  "ctaText",    "Jogar agora") }
    val ctaColor         = remember(ad) { parseAdColor(ad, "ctaBackgroundColor", DEFAULT_CTA_COLOR)  }
    val scoreColor       = remember(ad) { parseAdColor(ad, "scoreColor",          DEFAULT_SCORE_COLOR) }

    LaunchedEffect(ad) {
        Log.d(TAG, "Assets — ballImg:${ballBitmap != null}, background:${backgroundBitmap != null}, ctaText:'$ctaText'")
        ad.recordImpression()
        Log.d(TAG, "Impressão registrada")
    }

    // ── Layout ───────────────────────────────────────────────────────────────
    // Black fills any letterbox/pillarbox area outside the background image.
    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {

        val pixelDensity   = LocalDensity.current.density
        val screenWidthDp  = maxWidth.value   // Float dp
        val screenHeightDp = maxHeight.value  // Float dp

        // Compute background image rendered bounds (ContentScale.Fit, centred).
        val bgW = backgroundBitmap?.width?.toFloat()  ?: 1f
        val bgH = backgroundBitmap?.height?.toFloat() ?: 1f
        val bgRatio     = bgW / bgH
        val screenRatio = screenWidthDp / screenHeightDp

        val imageWidthDp: Float
        val imageHeightDp: Float
        if (bgRatio > screenRatio) {
            imageWidthDp  = screenWidthDp
            imageHeightDp = screenWidthDp / bgRatio
        } else {
            imageHeightDp = screenHeightDp
            imageWidthDp  = screenHeightDp * bgRatio
        }

        val imageLeftDp = (screenWidthDp  - imageWidthDp)  / 2f
        val imageTopDp  = (screenHeightDp - imageHeightDp) / 2f

        // ── Physics state (dp-space, keyed to reset if layout changes) ───────
        // Ball position and velocity
        var ballX     by remember(imageWidthDp) { mutableStateOf(imageWidthDp / 2f) }
        var ballY     by remember(imageWidthDp) { mutableStateOf(BALL_RADIUS + 20f) }
        var velocityX by remember(imageWidthDp) { mutableStateOf(0f)  }
        var velocityY by remember(imageWidthDp) { mutableStateOf(80f) } // small initial drop

        // Cursor — starts centred in the lower area so the user can see the mechanic right away
        var cursorX       by remember(imageWidthDp) { mutableStateOf(imageWidthDp  / 2f) }
        var cursorY       by remember(imageWidthDp) { mutableStateOf(imageHeightDp * 0.75f) }
        var cursorVisible by remember { mutableStateOf(true) }

        // Collision guard — prevents multiple scores per contact
        var wasColliding by remember { mutableStateOf(false) }

        // Score persists independently of layout changes
        var score by remember { mutableIntStateOf(0) }

        // ── Physics loop (runs every display frame via withFrameNanos) ────────
        LaunchedEffect(imageWidthDp, imageHeightDp) {
            var lastNanos = 0L
            while (true) {
                withFrameNanos { nanos ->
                    if (lastNanos == 0L) { lastNanos = nanos; return@withFrameNanos }

                    // dt capped to 50ms so a GC pause doesn't launch the ball into space
                    val dt = ((nanos - lastNanos) / 1_000_000_000f).coerceAtMost(0.05f)
                    lastNanos = nanos

                    // Gravity
                    velocityY += GRAVITY * dt

                    // Move ball
                    ballX += velocityX * dt
                    ballY += velocityY * dt

                    // ── Lateral wall collisions ────────────────────────────────
                    if (ballX - BALL_RADIUS < 0f) {
                        ballX     = BALL_RADIUS
                        velocityX = -velocityX * WALL_DAMPING
                    } else if (ballX + BALL_RADIUS > imageWidthDp) {
                        ballX     = imageWidthDp - BALL_RADIUS
                        velocityX = -velocityX * WALL_DAMPING
                    }

                    // ── Cursor collision ──────────────────────────────────────
                    // Only checked while cursor is visible and ball is falling (velocityY > 0).
                    if (cursorVisible && velocityY >= 0f) {
                        val dx   = ballX - cursorX
                        val dy   = ballY - cursorY
                        val dist = sqrt(dx * dx + dy * dy)
                        val colliding = dist <= COLLISION_DIST

                        if (colliding && !wasColliding) {
                            // Horizontal delta: cursor left of ball → ball goes right, and vice-versa
                            val horizontalDelta = ballX - cursorX
                            velocityX = (horizontalDelta * HORIZONTAL_FACTOR)
                                .coerceIn(-MAX_VELOCITY_X, MAX_VELOCITY_X)
                            velocityY = BOUNCE_IMPULSE
                            score++
                            Log.d(TAG, "Quique! Score=$score | cursor=(${cursorX.toInt()},${cursorY.toInt()}) ball=(${ballX.toInt()},${ballY.toInt()})")
                        }
                        wasColliding = colliding
                    } else {
                        wasColliding = false
                    }

                    // ── Ceiling collision ─────────────────────────────────────
                    if (ballY - BALL_RADIUS < 0f) {
                        ballY     = BALL_RADIUS
                        velocityY = -velocityY * CEILING_DAMPING
                    }

                    // ── Floor collision ────────────────────────────────────────
                    // Only runs when ball is falling/resting (velocityY >= 0).
                    // Skipped if the cursor just launched the ball upward (velocityY < 0),
                    // otherwise the floor block would overwrite the bounce velocity.
                    if (ballY + BALL_RADIUS >= imageHeightDp && velocityY >= 0f) {
                        ballY = imageHeightDp - BALL_RADIUS

                        if (velocityY > FLOOR_REST_THRESHOLD) {
                            velocityY    = -velocityY * FLOOR_DAMPING
                            velocityX   *= FLOOR_FRICTION
                            wasColliding = false
                            Log.d(TAG, "Bola bateu no chão — bounce (vy=${velocityY.toInt()}dp/s)")
                        } else {
                            // Ball at rest on floor — zero velocity, clear contacts
                            velocityY    = 0f
                            velocityX   *= FLOOR_FRICTION
                            wasColliding = false
                        }
                    }
                }
            }
        }

        // ── Background ────────────────────────────────────────────────────────
        if (backgroundBitmap != null) {
            Image(
                bitmap = backgroundBitmap,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(imageWidthDp.dp, imageHeightDp.dp)
                    .background(DEFAULT_BACKGROUND_COLOR)
            )
        }

        // ── Game area overlay (positioned over the image) ─────────────────────
        // All gameplay elements live inside this Box so their coordinates are
        // relative to the image's top-left corner.
        Box(
            modifier = Modifier
                .offset(x = imageLeftDp.dp, y = imageTopDp.dp)
                .size(imageWidthDp.dp, imageHeightDp.dp)
                // Tap → move cursor to tapped position
                .pointerInput(pixelDensity) {
                    detectTapGestures { offset ->
                        cursorX       = offset.x / pixelDensity
                        cursorY       = offset.y / pixelDensity
                        cursorVisible = true
                        Log.d(TAG, "Cursor → (${cursorX.toInt()}dp, ${cursorY.toInt()}dp)")
                    }
                }
        ) {
            // Score — top centre of the image
            Text(
                text = score.toString(),
                color = scoreColor,
                fontSize = 72.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 24.dp),
                textAlign = TextAlign.Center
            )

            // Cursor — hollow circle showing where the "foot" is placed
            if (cursorVisible) {
                Box(
                    modifier = Modifier
                        .offset(
                            x = (cursorX - CURSOR_RADIUS).dp,
                            y = (cursorY - CURSOR_RADIUS).dp
                        )
                        .size((CURSOR_RADIUS * 2).dp)
                        .border(2.5.dp, Color.White.copy(alpha = 0.80f), CircleShape)
                )
            }

            // Ball
            Box(
                modifier = Modifier
                    .offset(
                        x = (ballX - BALL_RADIUS).dp,
                        y = (ballY - BALL_RADIUS).dp
                    )
                    .size((BALL_RADIUS * 2).dp)
                    .clip(CircleShape)
            ) {
                if (ballBitmap != null) {
                    Image(
                        bitmap = ballBitmap,
                        contentDescription = "Bola",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize().background(Color.White),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("⚽", fontSize = 32.sp, textAlign = TextAlign.Center)
                    }
                }
            }
        }

        // ── CTA — centred at ~40% from the top of the screen ────────────────
        Button(
            onClick = {
                Log.d(TAG, "Clique no CTA — asset: ctaText")
                ad.performClick("ctaText")
            },
            colors = ButtonDefaults.buttonColors(containerColor = ctaColor),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = maxHeight * 0.45f - 26.dp)
                .fillMaxWidth(0.70f)
                .height(52.dp)
        ) {
            Text(
                text = ctaText,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // ── AdChoices overlay ─────────────────────────────────────────────────
        AdChoicesOverlay(ad = ad)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// AdChoices
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AdChoicesOverlay(ad: CustomNativeAd) {
    val adChoicesBitmap = remember(ad) {
        val drawable = ad.getImage(NativeAdAssetNames.ASSET_ADCHOICES_CONTAINER_VIEW)?.drawable
        if (drawable != null) {
            Log.d(TAG, "AdChoices icon encontrado")
            drawableToBitmap(drawable)?.asImageBitmap()
        } else {
            Log.d(TAG, "AdChoices icon não encontrado")
            null
        }
    }

    if (adChoicesBitmap != null) {
        Box(modifier = Modifier.fillMaxSize()) {
            Image(
                bitmap = adChoicesBitmap,
                contentDescription = "AdChoices",
                modifier = Modifier
                    .size(24.dp)
                    .align(Alignment.TopEnd)
                    .padding(top = 4.dp, end = 4.dp)
                    .clickable {
                        ad.performClick(NativeAdAssetNames.ASSET_ADCHOICES_CONTAINER_VIEW)
                    }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Asset helpers
// ─────────────────────────────────────────────────────────────────────────────

private fun loadAdImage(ad: CustomNativeAd, assetName: String, logName: String): ImageBitmap? {
    val drawable = ad.getImage(assetName)?.drawable
    return if (drawable != null) {
        Log.d(TAG, "Asset '$logName' encontrado")
        drawableToBitmap(drawable)?.asImageBitmap()
    } else {
        Log.w(TAG, "Asset '$logName' ausente — usando fallback")
        null
    }
}

private fun loadAdText(ad: CustomNativeAd, assetName: String, fallback: String): String {
    val value = ad.getText(assetName)?.toString()
    return if (value != null) {
        Log.d(TAG, "Asset '$assetName' encontrado: '$value'")
        value
    } else {
        Log.w(TAG, "Asset '$assetName' ausente — usando fallback: '$fallback'")
        fallback
    }
}

private fun parseAdColor(ad: CustomNativeAd, assetName: String, fallback: Color): Color {
    val colorStr = ad.getText(assetName)?.toString() ?: run {
        Log.w(TAG, "Asset '$assetName' ausente — usando cor fallback")
        return fallback
    }
    return try {
        Color(android.graphics.Color.parseColor(colorStr))
            .also { Log.d(TAG, "Asset '$assetName' cor parseada: '$colorStr'") }
    } catch (e: Exception) {
        Log.w(TAG, "Asset '$assetName' cor inválida '$colorStr' — usando fallback")
        fallback
    }
}

private fun drawableToBitmap(drawable: Drawable): Bitmap? {
    if (drawable is BitmapDrawable) return drawable.bitmap
    val w = drawable.intrinsicWidth.coerceAtLeast(1)
    val h = drawable.intrinsicHeight.coerceAtLeast(1)
    return try {
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap
    } catch (e: Exception) {
        Log.e(TAG, "Erro ao converter drawable para bitmap: ${e.message}")
        null
    }
}
