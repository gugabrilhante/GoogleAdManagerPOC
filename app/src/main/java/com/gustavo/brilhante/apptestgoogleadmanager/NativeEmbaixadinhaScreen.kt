package com.gustavo.brilhante.apptestgoogleadmanager

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.Log
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseIn
import androidx.compose.animation.core.EaseOut
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.libraries.ads.mobile.sdk.nativead.CustomNativeAd
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdAssetNames
import kotlinx.coroutines.launch

private const val TAG = "NativeEmbaixadinha"

private val DEFAULT_BACKGROUND_COLOR = Color(0xFF1B5E20)
private val DEFAULT_CTA_COLOR = Color(0xFF388E3C)
private val DEFAULT_SCORE_COLOR = Color.White

@Composable
fun NativeEmbaixadinhaScreen(viewModel: BakingViewModel) {
    val adsState by viewModel.embaixadinhaAdsState.collectAsState()
    val adError by viewModel.adError.collectAsState()

    when {
        adError != null && adsState.isEmpty() -> NativeEmbaixadinhaError(adError!!)
        adsState.isNotEmpty() -> NativeEmbaixadinhaGame(ad = adsState.first())
        else -> NativeEmbaixadinhaLoading()
    }
}

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

@Composable
private fun NativeEmbaixadinhaGame(ad: CustomNativeAd) {
    val scope = rememberCoroutineScope()
    var score by remember { mutableIntStateOf(0) }
    var isAnimating by remember { mutableStateOf(false) }

    // Animatable in Dp — negative = ball moving upward
    val ballOffset = remember { Animatable(0.dp, Dp.VectorConverter) }

    val backgroundBitmap = remember(ad) { loadAdImage(ad, "background", "background") }
    val ballBitmap = remember(ad) { loadAdImage(ad, "ballImg", "ballImg") }
    val ctaText = remember(ad) { loadAdText(ad, "ctaText", "Jogar agora") }
    val ctaColor = remember(ad) { parseAdColor(ad, "ctaBackgroundColor", DEFAULT_CTA_COLOR) }
    val scoreColor = remember(ad) { parseAdColor(ad, "scoreColor", DEFAULT_SCORE_COLOR) }

    LaunchedEffect(ad) {
        Log.d(TAG, "Assets encontrados — ballImg:${ballBitmap != null}, background:${backgroundBitmap != null}, ctaText:'$ctaText'")
        ad.recordImpression()
        Log.d(TAG, "Impressão registrada")
    }

    // Black fills letterbox/pillarbox areas around the background image
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize().background(Color.Black)
    ) {
        val screenWidthDp = maxWidth
        val screenHeightDp = maxHeight

        // Calculate where the background image renders under ContentScale.Fit (centered, ratio preserved)
        val bgW = backgroundBitmap?.width?.toFloat() ?: 1f
        val bgH = backgroundBitmap?.height?.toFloat() ?: 1f
        val bgRatio = bgW / bgH
        val screenRatio = screenWidthDp.value / screenHeightDp.value

        val imageWidthDp: Dp
        val imageHeightDp: Dp
        if (bgRatio > screenRatio) {
            // Image wider than screen → fit width, letterbox top/bottom
            imageWidthDp = screenWidthDp
            imageHeightDp = screenWidthDp / bgRatio
        } else {
            // Image taller → fit height, pillarbox left/right
            imageHeightDp = screenHeightDp
            imageWidthDp = screenHeightDp * bgRatio
        }

        val imageTopDp = (screenHeightDp - imageHeightDp) / 2f
        val imageBottomDp = imageTopDp + imageHeightDp

        val ballSizeDp = 100.dp
        // Ball rests at the bottom edge of the background image
        val ballRestTopDp = imageBottomDp - ballSizeDp - 12.dp

        // --- Background: ContentScale.Fit shows the full image ---
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
                    .size(imageWidthDp, imageHeightDp)
                    .background(DEFAULT_BACKGROUND_COLOR)
            )
        }

        // --- Score at top of the background image ---
        Text(
            text = score.toString(),
            color = scoreColor,
            fontSize = 72.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = imageTopDp + 24.dp),
            textAlign = TextAlign.Center
        )

        // --- Ball: sits at image bottom, jumps up on tap with gravity fall-back ---
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = ballRestTopDp + ballOffset.value)
                .size(ballSizeDp)
                .clip(CircleShape)
                .clickable(enabled = !isAnimating) {
                    Log.d(TAG, "Clique na bola — score atual: $score")
                    scope.launch {
                        isAnimating = true
                        score++
                        Log.d(TAG, "Score incrementado: $score")
                        // EaseOut upward = ball decelerates as it rises
                        ballOffset.animateTo(
                            targetValue = -180.dp,
                            animationSpec = tween(durationMillis = 280, easing = EaseOut)
                        )
                        // EaseIn downward = ball accelerates as gravity pulls it back
                        ballOffset.animateTo(
                            targetValue = 0.dp,
                            animationSpec = tween(durationMillis = 400, easing = EaseIn)
                        )
                        isAnimating = false
                    }
                }
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
                    Text("⚽", fontSize = 48.sp, textAlign = TextAlign.Center)
                }
            }
        }

        // --- CTA centered on the screen ---
        Button(
            onClick = {
                Log.d(TAG, "Clique no CTA — asset: ctaText")
                ad.performClick("ctaText")
            },
            colors = ButtonDefaults.buttonColors(containerColor = ctaColor),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .align(Alignment.Center)
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

        // --- AdChoices overlay top-right ---
        AdChoicesOverlay(ad = ad)
    }
}

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
    val colorStr = ad.getText(assetName)?.toString()
    return if (colorStr != null) {
        try {
            val parsed = Color(android.graphics.Color.parseColor(colorStr))
            Log.d(TAG, "Asset '$assetName' cor parseada: '$colorStr'")
            parsed
        } catch (e: Exception) {
            Log.w(TAG, "Asset '$assetName' cor inválida '$colorStr' — usando fallback")
            fallback
        }
    } else {
        Log.w(TAG, "Asset '$assetName' ausente — usando cor fallback")
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
