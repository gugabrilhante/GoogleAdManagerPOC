package com.gustavo.brilhante.apptestgoogleadmanager

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.ads.mobile.sdk.MobileAds
import com.google.android.libraries.ads.mobile.sdk.initialization.InitializationConfig
import com.gustavo.brilhante.apptestgoogleadmanager.ui.theme.AppTestGoogleAdManagerTheme
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: BakingViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        viewModel = ViewModelProvider(this)[BakingViewModel::class.java]

        findViewById<ComposeView>(R.id.compose_view).setContent {
            AppTestGoogleAdManagerTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    BakingScreen()
                }
            }
        }

        initializeMobileAds()
    }

    private fun initializeMobileAds() {
        lifecycleScope.launch(Dispatchers.IO) {
            // Using a placeholder Application ID for Google Ad Manager
            // In a real app, use your actual Ad Manager or AdMob App ID
            val initializationConfig = InitializationConfig.Builder("ca-app-pub-3940256099942544~3347511713")
                .build()

            MobileAds.initialize(this@MainActivity, initializationConfig) {
                lifecycleScope.launch(Dispatchers.Main) {
                    loadAd()
                }
            }
        }
    }

    private fun loadAd() {
        val manager = CustomNativeAdManager()

        // Dados para a PoC do formato Shortz
        val adUnitId = "/32352161/formatos/ShortzVideo"
        val customFormatId = "12420626"
        val customTargeting = mapOf("tvg_pos" to "SHORTZ")

        manager.loadCustomNativeAd(
            context = this,
            adUnitId = adUnitId,
            customFormatId = customFormatId,
            customTargeting = customTargeting,
            onAdLoaded = { customNativeAd ->
                runOnUiThread {
                    viewModel.setAd(customNativeAd)
                }
            },
            onAdFailed = { error ->
                Log.e("AdManager", "Falha ao carregar anúncio: ${error.message}")
            }
        )
    }
}
