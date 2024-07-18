package com.example.camera

import android.graphics.Bitmap

data class FrameData(
    val bitmap: Bitmap,
    val coordinates: List<Pair<Float, Float>>
)
