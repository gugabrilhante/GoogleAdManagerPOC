package com.gustavo.brilhante.apptestgoogleadmanager

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.CustomNativeAd
import com.google.android.libraries.ads.mobile.sdk.common.VideoController
import com.google.android.libraries.ads.mobile.sdk.nativead.MediaView
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAd.NativeAdType
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdAssetNames
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoader
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdLoaderCallback
import com.google.android.libraries.ads.mobile.sdk.nativead.NativeAdRequest

class CustomNativeAdManager {

    private var loadedCustomNativeAd: CustomNativeAd? = null

    fun loadCustomNativeAd(
        context: Context,
        adUnitId: String,
        customFormatId: String,
        numberOfAds: Int = 1,
        customTargeting: Map<String, Any> = emptyMap(),
        onAdLoaded: (CustomNativeAd) -> Unit,
        onAdFailed: (LoadAdError) -> Unit = {}
    ) {
        val builder = NativeAdRequest.Builder(adUnitId, listOf(NativeAdType.CUSTOM_NATIVE))
            .setCustomFormatIds(listOf(customFormatId))

        customTargeting.forEach { (key, value) ->
            when (value) {
                is String -> builder.putCustomTargeting(key, value)
                is List<*> -> {
                    val stringList = value.filterIsInstance<String>()
                    if (stringList.isNotEmpty()) {
                        builder.putCustomTargeting(key, stringList)
                    }
                }
            }
        }

        val adRequest = builder.build()

        NativeAdLoader.load(adRequest, numberOfAds, object : NativeAdLoaderCallback {
            override fun onCustomNativeAdLoaded(customNativeAd: CustomNativeAd) {
                onAdLoaded(customNativeAd)
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                onAdFailed(adError)
            }
        })
    }

    /**
     * Exibir o anúncio nativo personalizado (Vídeo / Shortz) com suporte a FLUID.
     *
     * @param cropToFill Quando true, aplica center-crop: escala o MediaView para preencher
     *   o container inteiramente, recortando o excesso. O container deve ter clipChildren=true
     *   (ou Modifier.clipToBounds() no lado Compose) para que o overflow seja cortado.
     *   Útil quando o ratio nativo do vídeo difere do ratio do container.
     */
    fun displayVideoCustomNativeAd(
        customNativeAd: CustomNativeAd,
        context: Context,
        cropToFill: Boolean = false
    ): View {
        val adView = LayoutInflater.from(context).inflate(R.layout.layout_custom_native_ad, null)

        adView.layoutParams = ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.MATCH_PARENT
        )

        val headlineView = adView.findViewById<TextView>(R.id.ad_headline)
        val captionView = adView.findViewById<TextView>(R.id.ad_caption)
        val mediaPlaceholder = adView.findViewById<FrameLayout>(R.id.media_placeholder)

        // Buscar assets com fallback para nomes comuns
        val headline = customNativeAd.getText("Headline") ?: customNativeAd.getText("Title")
        val caption = customNativeAd.getText("Caption") ?: customNativeAd.getText("Body")

        headlineView.text = headline ?: ""
        captionView.text = caption ?: ""
        headlineView.visibility = if (headline != null) View.VISIBLE else View.GONE
        captionView.visibility = if (caption != null) View.VISIBLE else View.GONE

        val mediaContent = customNativeAd.mediaContent
        if (mediaContent != null) {
            val mediaView = MediaView(context)
            mediaView.mediaContent = mediaContent

            mediaView.layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            mediaPlaceholder.removeAllViews()
            mediaPlaceholder.addView(mediaView)

            if (cropToFill) {
                val videoAspectRatio = mediaContent.aspectRatio.takeIf { it > 0f } ?: (16f / 9f)
                mediaPlaceholder.clipChildren = true
                mediaView.viewTreeObserver.addOnGlobalLayoutListener(
                    object : ViewTreeObserver.OnGlobalLayoutListener {
                        override fun onGlobalLayout() {
                            mediaView.viewTreeObserver.removeOnGlobalLayoutListener(this)
                            applyCenterCropScale(mediaView, mediaPlaceholder, videoAspectRatio)
                        }
                    }
                )
            }

            // Defer play until after the view is attached and the Surface is ready.
            // view.post() runs after the next draw frame, by which time MediaView
            // has a valid Surface to render onto (avoids the black screen).
            if (mediaContent.hasVideoContent) {
                val videoController = mediaContent.videoController
                videoController?.videoLifecycleCallbacks = object : VideoController.VideoLifecycleCallbacks {
                    override fun onVideoEnd() {
                        videoController.play()
                    }
                }
                adView.tag = Runnable { videoController?.play() }
            }
            Log.d("AdManager", "MediaView renderizado. AspectRatio: ${mediaContent.aspectRatio}, cropToFill: $cropToFill")
        } else {
            val mainImage = customNativeAd.getImage("MainImage") ?: customNativeAd.getImage("mainImage")
            if (mainImage != null) {
                val imageView = ImageView(context)
                imageView.setImageDrawable(mainImage.drawable)
                imageView.adjustViewBounds = true
                imageView.layoutParams = FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.MATCH_PARENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT
                )
                mediaPlaceholder.removeAllViews()
                mediaPlaceholder.addView(imageView)
                imageView.setOnClickListener { customNativeAd.performClick("MainImage") }
            }
        }

        renderAdChoices(customNativeAd, adView)
        customNativeAd.recordImpression()

        return adView
    }

    /**
     * Escala o [mediaView] para preencher [container] centralmente (center-crop).
     *
     * O MediaView renderiza o vídeo respeitando o ratio nativo, o que causa letterbox
     * (barras pretas) quando o container tem ratio diferente. Esta função compensa isso
     * aplicando um scale uniforme que faz o vídeo preencher o container por completo;
     * o excesso é recortado pelo clipChildren do container.
     */
    private fun applyCenterCropScale(mediaView: View, container: FrameLayout, videoAspectRatio: Float) {
        val containerW = container.width.toFloat()
        val containerH = container.height.toFloat()
        if (containerW <= 0f || containerH <= 0f) return

        val containerRatio = containerW / containerH

        // Scale necessário para center-crop: o maior dos dois fatores de preenchimento.
        val scale = if (videoAspectRatio >= containerRatio) {
            // Vídeo mais largo que o container → letterbox vertical → escala pela altura
            videoAspectRatio / containerRatio
        } else {
            // Vídeo mais alto que o container → pillarbox horizontal → escala pela largura
            containerRatio / videoAspectRatio
        }

        mediaView.scaleX = scale
        mediaView.scaleY = scale
        mediaView.pivotX = containerW / 2f
        mediaView.pivotY = containerH / 2f

        Log.d("AdManager", "CenterCrop scale=$scale videoRatio=$videoAspectRatio containerRatio=$containerRatio")
    }

    fun displayWebCustomNativeAd(
        customNativeAd: CustomNativeAd,
        context: Context
    ): View {
        val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            webViewClient = WebViewClient()
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val url = customNativeAd.getText("webAd")
        Log.d("AdManager", "Renderizando Embaixadinha via WebView. URL: $url")
        
        if (url != null) {
            webView.loadUrl(url.toString())
        } else {
            Log.e("AdManager", "Asset 'webAd' não encontrado no anúncio customizado.")
        }

        customNativeAd.recordImpression()

        return webView
    }

    fun renderAdChoices(customNativeAd: CustomNativeAd, adView: View) {
        val adChoicesImageView = adView.findViewById<ImageView>(R.id.adchoices)
        val adChoicesAsset = customNativeAd.getImage(NativeAdAssetNames.ASSET_ADCHOICES_CONTAINER_VIEW)
        
        if (adChoicesAsset != null) {
            adChoicesImageView.setImageDrawable(adChoicesAsset.drawable)
            adChoicesImageView.visibility = View.VISIBLE
            adChoicesImageView.setOnClickListener {
                customNativeAd.performClick(NativeAdAssetNames.ASSET_ADCHOICES_CONTAINER_VIEW)
            }
        } else {
            adChoicesImageView.visibility = View.GONE
        }
    }
}
