# AppTestGoogleAdManager 🚀

Este projeto é uma **Prova de Conceito (PoC)** para demonstrar a integração do **Google Ad Manager** em um aplicativo Android moderno, utilizando **Jetpack Compose** e o formato de **Custom Native Ads (Anúncios Nativos Personalizados)**.

O objetivo principal é validar a exibição do formato "Shortz" (vídeos curtos) integrados de forma fluida em uma lista de conteúdo.

## 📱 Demo

| Shortz Video Integration |
|--------------------------|
| <img src="docs/shortz_demo.gif" width="100%" alt="Shortz Video Demo"> |

> *Nota: Os GIFs acima são ilustrativos para esta documentação.*

---

## 🛠️ Tecnologias Utilizadas

- **Kotlin** & **Jetpack Compose**
- **Google Mobile Ads SDK (GAM)**: `com.google.android.libraries.ads.mobile.sdk:ads-mobile-sdk:1.1.0`
- **Firebase AI (Gemini)**: Utilizado para funcionalidades de análise de imagem no contexto da aplicação de exemplo.
- **Coroutines & Flow**: Para gerenciamento de estado assíncrono.

## 🏗️ Implementação

### 1. Inicialização do SDK
O SDK é inicializado na `MainActivity` utilizando um ID de aplicação placeholder. O carregamento de anúncios só inicia após o callback de sucesso da inicialização.

```kotlin
val initializationConfig = InitializationConfig.Builder("ca-app-pub-3940256099942544~3347511713")
    .build()

MobileAds.initialize(this, initializationConfig) {
    loadAd()
}
```

### 2. Custom Native Ad Manager
Criamos um `CustomNativeAdManager` para centralizar a lógica de:
- **Carregamento**: Configuração do `NativeAdRequest` com `customFormatId` e `customTargeting`.
- **Renderização**: Inflagem do layout XML (`layout_custom_native_ad.xml`) e vinculação dos assets do anúncio (Headline, Body, MediaContent).
- **Suporte a Vídeo**: Uso do `MediaView` para renderizar o conteúdo de vídeo do Shortz, com controle de autoplay.

### 3. Integração com Jetpack Compose
Os anúncios são exibidos dentro de um `LazyColumn` no `BakingScreen`. Utilizamos `AndroidView` para integrar o componente nativo do SDK:

```kotlin
AndroidView(
    factory = { ctx ->
        CustomNativeAdManager().displayVideoCustomNativeAd(ad, ctx)
    },
    modifier = Modifier.fillMaxWidth().aspectRatio(aspectRatio)
)
```

## 📋 Funcionalidades Implementadas

- [x] Inicialização do Google Mobile Ads SDK.
- [x] Carregamento de múltiplos anúncios nativos personalizados.
- [x] Suporte a Custom Targeting (`tvg_pos: SHORTZ`).
- [x] Renderização de MediaContent (Vídeo) com proporção dinâmica.
- [x] Controle de Impressões e Cliques.
- [x] Placeholder de carregamento enquanto o anúncio não está pronto.

## 🚀 Como Executar

1. Clone o repositório.
2. Certifique-se de ter o arquivo `google-services.json` (se necessário para o Firebase).
3. Sincronize o Gradle.
4. Execute o app em um emulador ou dispositivo físico.

---
Desenvolvido como referência técnica para implementações de Google Ad Manager.
