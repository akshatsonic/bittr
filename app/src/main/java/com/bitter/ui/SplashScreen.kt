package com.bitter.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.bitter.R
import kotlinx.coroutines.delay
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun SplashScreen(onFinished: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2400)
        onFinished()
    }

    val transition = rememberInfiniteTransition(label = "splash")
    val progress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "splashProgress",
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val base = size.minDimension
            val count = 7
            for (i in 0 until count) {
                val phase = i.toFloat() / count
                val angle = phase * 2f * PI.toFloat() + progress * 2f * PI.toFloat()
                val dist = base * (0.18f + 0.16f * ((progress + phase) % 1f))
                val x = cx + cos(angle) * dist
                val y = cy + sin(angle) * dist
                val radius = base * (0.03f + 0.05f * ((progress + phase) % 1f))
                val alpha = 0.10f + 0.14f * ((progress + phase) % 1f)
                drawCircle(
                    color = BittrColors.Blurple.copy(alpha = alpha),
                    radius = radius,
                    center = Offset(x, y),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.ic_bittr_logo),
                contentDescription = "Bittr",
                modifier = Modifier
                    .height(96.dp)
                    .graphicsLayer {
                        scaleX = 1f + 0.05f * progress
                        scaleY = 1f + 0.05f * progress
                        alpha = 0.75f + 0.25f * progress
                    },
                colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onBackground),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "offline mesh timeline",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.graphicsLayer {
                    alpha = 0.4f + 0.6f * progress
                },
            )
        }
    }
}
