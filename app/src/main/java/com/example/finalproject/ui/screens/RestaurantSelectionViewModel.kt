package com.example.finalproject.ui.screens

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.finalproject.model.RestaurantSuggestion
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class RestaurantSelectionState(
    val restaurants: List<RestaurantSuggestion> = emptyList(),
    val selectedRestaurant: RestaurantSuggestion? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class RestaurantSelectionViewModel : ViewModel() {
    
    private val _state = MutableStateFlow(RestaurantSelectionState())
    val state: StateFlow<RestaurantSelectionState> = _state.asStateFlow()

    fun loadRestaurantSuggestions(suggestions: List<String>) {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, errorMessage = null) }
            
            try {
                val restaurantSuggestions = suggestions.map { suggestion ->
                    RestaurantSuggestion(
                        id = UUID.randomUUID().toString(),
                        name = suggestion,
                        location = "Unknown location",
                        rating = null,
                        priceLevel = null,
                        imageUrl = null
                    )
                }
                
                _state.update { 
                    it.copy(
                        restaurants = restaurantSuggestions,
                        isLoading = false
                    ) 
                }
            } catch (e: Exception) {
                _state.update { 
                    it.copy(
                        isLoading = false,
                        errorMessage = "Failed to load restaurant suggestions: ${e.message}"
                    ) 
                }
            }
        }
    }

    fun selectRestaurant(restaurant: RestaurantSuggestion) {
        _state.update { it.copy(selectedRestaurant = restaurant) }
    }
    
    fun selectRandomRestaurant() {
        val restaurants = state.value.restaurants
        if (restaurants.isNotEmpty()) {
            val randomRestaurant = restaurants.random()
            _state.update { it.copy(selectedRestaurant = randomRestaurant) }
        }
    }
    
    fun clearSelection() {
        _state.update { it.copy(selectedRestaurant = null) }
    }
    
    fun updateRestaurantInfo(restaurant: RestaurantSuggestion) {
        val updatedList = state.value.restaurants.map {
            if (it.id == restaurant.id) restaurant else it
        }
        _state.update { it.copy(restaurants = updatedList) }
        
        if (state.value.selectedRestaurant?.id == restaurant.id) {
            _state.update { it.copy(selectedRestaurant = restaurant) }
        }
    }
} 