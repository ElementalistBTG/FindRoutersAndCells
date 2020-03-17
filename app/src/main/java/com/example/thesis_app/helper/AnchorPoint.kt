package com.example.thesis_app.helper

import java.io.Serializable

data class AnchorPoint(
    val number: Int,
    val distance: Double,
    val signal: Float
) : Serializable