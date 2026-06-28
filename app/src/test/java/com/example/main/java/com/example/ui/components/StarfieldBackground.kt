package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import java.io.File
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlin.random.Random

data class CosmicStar(
    var x: Float,
    var y: Float,
    var radius: Float,
    var alpha: Float,
    val speed: Float,
    val color: Color
)

@Composable
fun StarfieldBackground(
    modifier: Modifier = Modifier,
    performanceMode: Boolean = true,
    batterySaver: Boolean = false,
    themeSelected: String = "Galaxy Dark",
    customBgSelected: String = "1"
) {
    val isCustomImage = customBgSelected != "1" && customBgSelected != "2" && customBgSelected != "3" && customBgSelected != "4"

    val maxStars = when {
        batterySaver -> 25
        !performanceMode -> 50
        else -> 100
    }

    val starColors = remember(themeSelected) {
        listOf(
            Color(0xFFFFFFFF),
            Color(0xFF80D8FF), // light blue
            Color(0xFFEA80FC), // light purple
            Color(0xFFFFD180), // light orange
            Color(0xFFB9F6CA)  // light green
        )
    }

    var stars by remember { mutableStateOf(emptyList<CosmicStar>()) }

    Box(modifier = modifier.fillMaxSize()) {
        if (isCustomImage) {
            val file = remember(customBgSelected) { File(customBgSelected) }
            if (file.exists()) {
                AsyncImage(
                    model = file,
                    contentDescription = "Fondo personalizado",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color(0xFF01020A))
                )
            }
        } else {
            // Determine gradient colors based on theme and customBgSelected
            val brush = remember(themeSelected, customBgSelected) {
                when {
                    customBgSelected == "2" || themeSelected == "Galaxy Dark" -> {
                        // Purple Nebula
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF03010A),
                                Color(0xFF0A051B),
                                Color(0xFF1E0E3D),
                                Color(0xFF08020E)
                            )
                        )
                    }
                    customBgSelected == "3" || themeSelected == "OLED" -> {
                        // Pure OLED Black
                        Brush.verticalGradient(
                            colors = listOf(Color.Black, Color.Black)
                        )
                    }
                    customBgSelected == "4" || themeSelected == "Solar Aurora" -> {
                        // Solar Aurora Teal / Green
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF000504),
                                Color(0xFF001410),
                                Color(0xFF002B20),
                                Color(0xFF000806)
                            )
                        )
                    }
                    else -> {
                        // Galaxy Blue / Deep Cosmic
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xFF01020A),
                                Color(0xFF02091B),
                                Color(0xFF061B3D),
                                Color(0xFF01030B)
                            )
                        )
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(brush)
            )
        }

        Canvas(
            modifier = Modifier.fillMaxSize()
        ) {
            val width = size.width
            val height = size.height

            if (width > 0 && height > 0 && stars.isEmpty()) {
                stars = List(maxStars) {
                    CosmicStar(
                        x = Random.nextFloat() * width,
                        y = Random.nextFloat() * height,
                        radius = Random.nextFloat() * 3f + 1f,
                        alpha = Random.nextFloat() * 0.8f + 0.2f,
                        speed = Random.nextFloat() * 0.8f + 0.2f,
                        color = starColors.random()
                    )
                }
            }

            // Render stars
            stars.forEach { star ->
                drawCircle(
                    color = star.color.copy(alpha = star.alpha),
                    radius = star.radius,
                    center = Offset(star.x, star.y)
                )
            }
        }
    }

    // Star position update animation loop
    if (!batterySaver) {
        val infiniteTransition = rememberInfiniteTransition(label = "star_twinkle")
        val animateTrigger by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 3000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "star_float"
        )

        LaunchedEffect(animateTrigger) {
            stars = stars.map { star ->
                var nextY = star.y + star.speed
                if (nextY > 2500f) { // fallback check
                    nextY = 0f
                }
                // Twinkle alpha variation
                val nextAlpha = (star.alpha + (Random.nextFloat() * 0.2f - 0.1f)).coerceIn(0.1f, 1.0f)
                star.copy(y = nextY, alpha = nextAlpha)
            }
        }
    }
}
