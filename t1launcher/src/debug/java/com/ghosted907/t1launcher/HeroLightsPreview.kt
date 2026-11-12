package com.ghosted907.t1launcher

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.tooling.preview.Preview

private fun previewSnap(high: Boolean) = CarBridge.Snap(readings = mapOf(
    "turn_left" to SignalReading(1.0, status = ReadingStatus.LIVE),
    "turn_right" to SignalReading(1.0, status = ReadingStatus.LIVE),
    "low_beam" to SignalReading(1.0, status = ReadingStatus.LIVE),
    "high_beam" to SignalReading(if (high) 1.0 else 0.0, status = ReadingStatus.LIVE)
))

@Preview(widthDp = 1000, heightDp = 570)
@Composable
fun HeroLightsLowPreview() {
    Box(Modifier.fillMaxSize().background(Color(0xFF16231D))) {
        Image(androidx.compose.ui.res.painterResource(R.drawable.oem_t1_cutout_saudi), null,
            Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        HeroVehicleLights(previewSnap(false))
    }
}

@Preview(widthDp = 1000, heightDp = 570)
@Composable
fun HeroLightsHighPreview() {
    Box(Modifier.fillMaxSize().background(Color(0xFF16231D))) {
        Image(androidx.compose.ui.res.painterResource(R.drawable.oem_t1_cutout_saudi), null,
            Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        HeroVehicleLights(previewSnap(true))
    }
}
