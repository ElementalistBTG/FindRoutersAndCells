package com.example.thesis_app.helper;

import java.io.Serializable


// For each tower we want the following data
data class Cell(
    val id: String,
    val latitude: Double,
    val longitude: Double,
    val serving_cell: Boolean,
    val signal_strength: Int,
    var type_of_service: String
) : Serializable