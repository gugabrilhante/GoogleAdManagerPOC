package com.gustavo.brilhante.apptestgoogleadmanager

import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import com.google.android.libraries.ads.mobile.sdk.common.LoadAdError
import com.google.android.libraries.ads.mobile.sdk.nativead.CustomNativeAd
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

        NativeAdLoader.load(adRequest, object : NativeAdLoaderCallback {
            override fun onCustomNativeAdLoaded(customNativeAd: CustomNativeAd) {
                loadedCustomNativeAd = customNativeAd
                onAdLoaded(customNativeAd)
            }

            override fun onAdFailedToLoad(adError: LoadAdError) {
                onAdFailed(adError)
            }
        })
    }

    /**
     * Exibir o anúncio nativo personalizado (Vídeo / Shortz) com suporte a FLUID
     */
    fun displayVideoCustomNativeAd(customNativeAd: CustomNativeAd, context: Context): View {
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

            // Defer play until after the view is attached and the Surface is ready.
            // view.post() runs after the next draw frame, by which time MediaView
            // has a valid Surface to render onto (avoids the black screen).
            if (mediaContent.hasVideoContent) {
                adView.tag = Runnable { mediaContent.videoController?.play() }
            }
            Log.d("AdManager", "MediaView renderizado. AspectRatio: ${mediaContent.aspectRatio}")
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
