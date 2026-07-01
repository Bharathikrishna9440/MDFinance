package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

@Composable
fun ShiningButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    color: Color = Color(0xFF4F46E5),
    contentColor: Color = Color.White
) {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by infiniteTransition.animateFloat(
        initialValue = -1000f,
        targetValue = 2000f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2500, easing = LinearEasing, delayMillis = 500),
            repeatMode = RepeatMode.Restart
        ),
        label = "shimmer_anim"
    )

    Button(
        onClick = onClick,
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .drawWithCache {
                onDrawWithContent {
                    drawContent()
                    val brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.White.copy(alpha = 0.5f),
                            Color.Transparent
                        ),
                        start = Offset(translateAnim, -translateAnim),
                        end = Offset(translateAnim + 500f, translateAnim + 500f)
                    )
                    drawRect(brush, blendMode = BlendMode.SrcAtop)
                }
            },
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = contentColor),
        shape = RoundedCornerShape(12.dp),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 6.dp, pressedElevation = 2.dp)
    ) {
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}

@Composable
fun AnimatedBlobBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "blobs")
    
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val x1 = width * (0.5f + 0.3f * cos(time))
        val y1 = height * (0.5f + 0.3f * sin(time * 0.7f))
        
        val x2 = width * (0.5f + 0.4f * sin(time * 1.3f))
        val y2 = height * (0.5f + 0.4f * cos(time * 0.9f))

        val brush1 = Brush.radialGradient(
            colors = listOf(Color(0x334F46E5), Color.Transparent),
            center = Offset(x1, y1),
            radius = width * 0.8f
        )
        val brush2 = Brush.radialGradient(
            colors = listOf(Color(0x3310B981), Color.Transparent),
            center = Offset(x2, y2),
            radius = width * 0.8f
        )

        drawRect(brush1)
        drawRect(brush2)
    }
}

@Composable
fun ParticleBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "particles")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(
            animation = tween(50000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "time"
    )

    val particles = remember { 
        List(30) { 
            Particle(
                x = Random.nextFloat(),
                y = Random.nextFloat(),
                speedX = (Random.nextFloat() - 0.5f) * 0.001f,
                speedY = (Random.nextFloat() - 0.5f) * 0.001f,
                radius = Random.nextFloat() * 5f + 2f,
                alpha = Random.nextFloat() * 0.5f + 0.1f
            )
        }
    }

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        
        particles.forEach { p ->
            val px = (p.x * width + p.speedX * width * time) % width
            val py = (p.y * height + p.speedY * height * time) % height
            
            val adjPx = if (px < 0) px + width else px
            val adjPy = if (py < 0) py + height else py

            drawCircle(
                color = Color.White.copy(alpha = p.alpha),
                radius = p.radius,
                center = Offset(adjPx, adjPy)
            )
        }
    }
}

private data class Particle(
    val x: Float,
    val y: Float,
    val speedX: Float,
    val speedY: Float,
    val radius: Float,
    val alpha: Float
)
