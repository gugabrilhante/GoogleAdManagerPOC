# AppTestGoogleAdManager 🚀

This project is a **Proof of Concept (PoC)** to demonstrate **Google Ad Manager** integration in a modern Android application, using **Jetpack Compose** and the **Custom Native Ads** format.

The main goal is to validate the display of the "Shortz" format (short videos) seamlessly integrated into a content list.

## 📱 Demo

| Shortz Video Integration |
|--------------------------|
| <img src="docs/shortz_demo.gif" width="100%" alt="Shortz Video Demo"> |

> *Note: The GIFs above are illustrative for this documentation.*

---

## 🛠️ Technologies Used

- **Kotlin** & **Jetpack Compose**
- **Google Mobile Ads SDK (GAM)**: `com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.1.0`
- **Firebase AI (Gemini)**: Used for image analysis features within the sample application context.
- **Coroutines & Flow**: For asynchronous state management.

## 🏗️ Implementation

### 1. SDK Initialization
The SDK is initialized in `MainActivity` using a placeholder application ID. Ad loading only starts after the successful initialization callback.

```kotlin
val initializationConfig = InitializationConfig.Builder("ca-app-pub-3940256099942544~3347511713")
    .build()

MobileAds.initialize(this, initializationConfig) {
    loadAd()
}
```

### 2. Custom Native Ad Manager
We created a `CustomNativeAdManager` to centralize the logic for:
- **Loading**: Configuring the `NativeAdRequest` with `customFormatId` and `customTargeting`.
- **Rendering**: Inflating the XML layout (`layout_custom_native_ad.xml`) and binding ad assets (Headline, Body, MediaContent).
- **Video Support**: Using `MediaView` to render Shortz video content, with autoplay control.

### 3. Jetpack Compose Integration
Ads are displayed within a `LazyColumn` in `BakingScreen`. We use `AndroidView` to integrate the native SDK component:

```kotlin
AndroidView(
    factory = { ctx ->
        CustomNativeAdManager().displayVideoCustomNativeAd(ad, ctx)
    },
    modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio)
)
```

## 📋 Implemented Features

- [x] Google Mobile Ads SDK initialization.
- [x] Loading multiple custom native ads.
- [x] Custom Targeting support (`tvg_pos: SHORTZ`).
- [x] MediaContent (Video) rendering with dynamic aspect ratio.
- [x] Impression and Click tracking.
- [x] Loading placeholder while the ad is not ready.

## 🚀 How to Run

1. Clone the repository.
2. Ensure you have the `google-services.json` file (if required for Firebase).
3. Sync Gradle.
4. Run the app on an emulator or physical device.

---
Developed as a technical reference for Google Ad Manager implementations.
