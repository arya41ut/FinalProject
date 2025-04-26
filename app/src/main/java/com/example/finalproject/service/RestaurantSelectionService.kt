package com.example.finalproject.service

object RestaurantSelectionService {
    enum class SelectionStrategy {
        RANDOM,
        WEIGHTED_RANDOM,
        HIGHEST_VOTES,
        CONSENSUS,
        AI_RECOMMEND
    }
} 