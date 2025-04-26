package com.example.finalproject.demo.controller;

import com.example.finalproject.demo.dto.RestaurantDto;
import com.example.finalproject.demo.model.Restaurant;
import com.example.finalproject.demo.model.User;
import com.example.finalproject.demo.service.RestaurantService;
import com.example.finalproject.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/restaurants")
@RequiredArgsConstructor
public class RestaurantController {
    
    private final RestaurantService restaurantService;
    private final UserService userService;
    
    // Public endpoints
    
    @GetMapping("/public/all")
    public ResponseEntity<List<Restaurant>> getAllRestaurants() {
        return ResponseEntity.ok(restaurantService.findAllRestaurants());
    }
    
    @GetMapping("/public/cuisines")
    public ResponseEntity<List<String>> getAllCuisines() {
        return ResponseEntity.ok(restaurantService.findAllCuisines());
    }
    
    @GetMapping("/public/cities")
    public ResponseEntity<List<String>> getAllCities() {
        return ResponseEntity.ok(restaurantService.findAllCities());
    }
    
    @GetMapping("/public/cuisine/{cuisine}")
    public ResponseEntity<List<Restaurant>> getRestaurantsByCuisine(@PathVariable String cuisine) {
        return ResponseEntity.ok(restaurantService.findByCuisine(cuisine));
    }
    
    @GetMapping("/public/city/{city}")
    public ResponseEntity<List<Restaurant>> getRestaurantsByCity(@PathVariable String city) {
        return ResponseEntity.ok(restaurantService.findByCity(city));
    }
    
    @GetMapping("/public/random")
    public ResponseEntity<Restaurant> getRandomRestaurant() {
        Restaurant restaurant = restaurantService.findRandomRestaurant();
        if (restaurant == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(restaurant);
    }
    
    @GetMapping("/public/random/cuisine/{cuisine}")
    public ResponseEntity<Restaurant> getRandomRestaurantByCuisine(@PathVariable String cuisine) {
        Restaurant restaurant = restaurantService.findRandomRestaurantByCuisine(cuisine);
        if (restaurant == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(restaurant);
    }
    
    // Authenticated endpoints
    
    @GetMapping("/my-restaurants")
    public ResponseEntity<List<Restaurant>> getUserRestaurants() {
        User currentUser = getCurrentUser();
        return ResponseEntity.ok(restaurantService.findByUser(currentUser));
    }
    
    @PostMapping
    public ResponseEntity<?> createRestaurant(@RequestBody RestaurantDto restaurantDto) {
        User currentUser = getCurrentUser();
        
        Restaurant restaurant = new Restaurant();
        restaurant.setName(restaurantDto.getName());
        restaurant.setCuisine(restaurantDto.getCuisine());
        restaurant.setAddress(restaurantDto.getAddress());
        restaurant.setCity(restaurantDto.getCity());
        restaurant.setState(restaurantDto.getState());
        restaurant.setZipCode(restaurantDto.getZipCode());
        restaurant.setPhoneNumber(restaurantDto.getPhoneNumber());
        restaurant.setWebsite(restaurantDto.getWebsite());
        restaurant.setRating(restaurantDto.getRating());
        restaurant.setPriceRange(restaurantDto.getPriceRange());
        restaurant.setDescription(restaurantDto.getDescription());
        restaurant.setImageUrls(restaurantDto.getImageUrls());
        restaurant.setUser(currentUser);
        
        Restaurant savedRestaurant = restaurantService.saveRestaurant(restaurant);
        
        return new ResponseEntity<>(savedRestaurant, HttpStatus.CREATED);
    }
    
    @GetMapping("/{id}")
    public ResponseEntity<?> getRestaurant(@PathVariable Long id) {
        Optional<Restaurant> restaurant = restaurantService.findById(id);
        if (restaurant.isEmpty()) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Restaurant not found with id: " + id);
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
        
        return ResponseEntity.ok(restaurant.get());
    }
    
    @PutMapping("/{id}")
    public ResponseEntity<?> updateRestaurant(@PathVariable Long id, @RequestBody RestaurantDto restaurantDto) {
        User currentUser = getCurrentUser();
        
        Optional<Restaurant> existingRestaurant = restaurantService.findById(id);
        if (existingRestaurant.isEmpty()) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Restaurant not found with id: " + id);
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
        
        Restaurant restaurant = existingRestaurant.get();
        
        // Check if the restaurant belongs to the current user
        if (restaurant.getUser() == null || !restaurant.getUser().getId().equals(currentUser.getId())) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "You do not have permission to update this restaurant");
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }
        
        // Update restaurant data
        restaurant.setName(restaurantDto.getName());
        restaurant.setCuisine(restaurantDto.getCuisine());
        restaurant.setAddress(restaurantDto.getAddress());
        restaurant.setCity(restaurantDto.getCity());
        restaurant.setState(restaurantDto.getState());
        restaurant.setZipCode(restaurantDto.getZipCode());
        restaurant.setPhoneNumber(restaurantDto.getPhoneNumber());
        restaurant.setWebsite(restaurantDto.getWebsite());
        restaurant.setRating(restaurantDto.getRating());
        restaurant.setPriceRange(restaurantDto.getPriceRange());
        restaurant.setDescription(restaurantDto.getDescription());
        restaurant.setImageUrls(restaurantDto.getImageUrls());
        
        Restaurant updatedRestaurant = restaurantService.updateRestaurant(restaurant);
        
        return ResponseEntity.ok(updatedRestaurant);
    }
    
    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteRestaurant(@PathVariable Long id) {
        User currentUser = getCurrentUser();
        
        Optional<Restaurant> existingRestaurant = restaurantService.findById(id);
        if (existingRestaurant.isEmpty()) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Restaurant not found with id: " + id);
            return new ResponseEntity<>(response, HttpStatus.NOT_FOUND);
        }
        
        Restaurant restaurant = existingRestaurant.get();
        
        // Check if the restaurant belongs to the current user
        if (restaurant.getUser() == null || !restaurant.getUser().getId().equals(currentUser.getId())) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "You do not have permission to delete this restaurant");
            return new ResponseEntity<>(response, HttpStatus.FORBIDDEN);
        }
        
        restaurantService.deleteRestaurant(id);
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Restaurant deleted successfully!");
        
        return ResponseEntity.ok(response);
    }
    
    // Helper method to get the current authenticated user
    private User getCurrentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication.getName();
        
        return userService.findByUsername(username)
                .orElseThrow(() -> new IllegalStateException("Authenticated user not found in database"));
    }
} 