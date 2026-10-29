package com.liquidglass.tvlauncher.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.liquidglass.tvlauncher.R
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The top art strip: a full-width photo that fades to the shelf colour
 * only at its very bottom edge, with the clock in the corner. The dock
 * sits across that fade, so the two gradient stops here must stay in sync
 * with the pre-blurred `bg_hero_blur` the dock draws behind itself.
 */
@Composable
fun HeroBanner(modifier: Modifier = Modifier) {
    var time by remember { mutableStateOf(currentTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            time = currentTime()
            delay(15_000)
        }
    }

    Box(modifier = modifier.fillMaxWidth()) {
        Image(
            painter = painterResource(id = R.drawable.bg_hero),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.00f to Color.Transparent,
                        0.55f to Color.Black.copy(alpha = 0.05f),
                        0.82f to ShelfColor.copy(alpha = 0.80f),
                        1.00f to ShelfColor,
                    ),
                ),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 48.dp, vertical = 26.dp),
            horizontalArrangement = Arrangement.End,
        ) {
            Text(
                text = time,
                color = Color.White.copy(alpha = 0.9f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

private fun currentTime(): String =
    SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
