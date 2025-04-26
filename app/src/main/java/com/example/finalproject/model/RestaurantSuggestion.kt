package com.example.finalproject.model

data class RestaurantSuggestion(
    val id: String,
    val name: String,
    val location: String,
    val rating: Float? = null,
    val priceLevel: Int? = null,
    val imageUrl: String? = null
) 