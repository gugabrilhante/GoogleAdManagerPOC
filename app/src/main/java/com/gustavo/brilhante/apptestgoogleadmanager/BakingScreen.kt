package com.gustavo.brilhante.apptestgoogleadmanager

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel

val images = arrayOf(
    R.drawable.baked_goods_1,
    R.drawable.baked_goods_2,
    R.drawable.baked_goods_3,
)
val imageDescriptions = arrayOf(
    R.string.image1_description,
    R.string.image2_description,
    R.string.image3_description,
)

@Composable
fun BakingScreen(
    bakingViewModel: BakingViewModel = viewModel()
) {
    val selectedImage = remember { mutableIntStateOf(0) }
    val placeholderPrompt = stringResource(R.string.prompt_placeholder)
    val placeholderResult = stringResource(R.string.results_placeholder)
    var prompt by rememberSaveable { mutableStateOf(placeholderPrompt) }
    var result by rememberSaveable { mutableStateOf(placeholderResult) }
    val uiState by bakingViewModel.uiState.collectAsState()
    val adState by bakingViewModel.adState.collectAsState()
    val resources = LocalResources.current

    Scaffold { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            item {
                Text(
                    text = stringResource(R.string.baking_title),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(16.dp)
                )
            }

            // Primeira Receita
            item {
                RecipeItem(0, images[0], imageDescriptions[0], selectedImage.intValue) {
                    selectedImage.intValue = 0
                }
            }

            // O ANÚNCIO (Item independente na lista)
            item {
                val ad = adState
                if (ad != null) {
                    // aspectRatio is available synchronously after the ad loads.
                    // Fallback to 16:9 if the server doesn't report a ratio.
                    val aspectRatio = ad.mediaContent?.aspectRatio?.takeIf { it > 0f } ?: (16f / 9f)

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp)
                            .background(Color(0xFFF9F9F9))
                    ) {
                        Text(
                            text = "Publicidade",
                            modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                            style = MaterialTheme.typography.labelSmall,
                            color = Color.Gray
                        )
                        // Drive the container size from the ad's aspect ratio so the
                        // MediaView always gets a non-zero canvas — no fixed dp needed.
                        AndroidView(
                            factory = { ctx ->
                                CustomNativeAdManager()
                                    .displayVideoCustomNativeAd(ad, ctx)
                                    .also { view ->
                                        // Run after the first draw frame so the Surface
                                        // backing the MediaView is ready for playback.
                                        view.post { (view.tag as? Runnable)?.run() }
                                    }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(aspectRatio)
                        )
                    }
                } else {
                    // Placeholder enquanto carrega
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(16f / 9f)
                            .background(Color(0xFFF0F0F0)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Buscando anúncio...", color = Color.Gray)
                    }
                }
            }

            // Restante das Receitas
            itemsIndexed(images) { index, image ->
                if (index > 0) {
                    RecipeItem(index, image, imageDescriptions[index], selectedImage.intValue) {
                        selectedImage.intValue = index
                    }
                }
            }

            // Campo de Input e Resultado (Gemini)
            item {
                BakingControls(prompt, onPromptChange = { prompt = it }) {
                    val bitmap = BitmapFactory.decodeResource(resources, images[selectedImage.intValue])
                    bakingViewModel.sendPrompt(bitmap, prompt)
                }
            }

            item {
                BakingResult(uiState, result)
            }
        }
    }
}

@Composable
fun RecipeItem(index: Int, image: Int, description: Int, selectedIndex: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp)
    ) {
        var imageModifier = Modifier
            .fillMaxWidth()
            .height(350.dp)
            .clickable { onClick() }

        if (index == selectedIndex) {
            imageModifier = imageModifier.border(BorderStroke(4.dp, MaterialTheme.colorScheme.primary))
        }

        Image(
            painter = painterResource(image),
            contentDescription = stringResource(description),
            modifier = imageModifier,
            contentScale = ContentScale.Crop
        )

        Text(
            text = stringResource(description),
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleLarge
        )
    }
}

@Composable
fun BakingControls(prompt: String, onPromptChange: (String) -> Unit, onGoClick: () -> Unit) {
    Row(modifier = Modifier.padding(16.dp)) {
        TextField(
            value = prompt,
            label = { Text(stringResource(R.string.label_prompt)) },
            onValueChange = onPromptChange,
            modifier = Modifier.weight(0.8f).padding(end = 16.dp).align(Alignment.CenterVertically)
        )
        Button(onClick = onGoClick, enabled = prompt.isNotEmpty(), modifier = Modifier.align(Alignment.CenterVertically)) {
            Text(text = stringResource(R.string.action_go))
        }
    }
}

@Composable
fun BakingResult(uiState: UiState, result: String) {
    if (uiState is UiState.Loading) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator()
        }
    } else {
        val displayResult = if (uiState is UiState.Error) uiState.errorMessage 
                           else if (uiState is UiState.Success) uiState.outputText 
                           else result
        val textColor = if (uiState is UiState.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        
        Text(text = displayResult, textAlign = TextAlign.Start, color = textColor, modifier = Modifier.padding(16.dp).fillMaxWidth())
    }
}

@Preview(showSystemUi = true)
@Composable
fun BakingScreenPreview() {
    BakingScreen()
}
