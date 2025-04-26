package com.example.finalproject.demo.service;

import com.example.finalproject.demo.model.Restaurant;
import com.example.finalproject.demo.model.User;

import java.util.List;
import java.util.Optional;

public interface RestaurantService {
    
    Restaurant saveRestaurant(Restaurant restaurant);
    
    Optional<Restaurant> findById(Long id);
    
    List<Restaurant> findAllRestaurants();
    
    List<Restaurant> findByUser(User user);
    
    List<Restaurant> findByCuisine(String cuisine);
    
    List<Restaurant> findByCity(String city);
    
    List<Restaurant> findByCuisineAndCity(String cuisine, String city);
    
    List<String> findAllCuisines();
    
    List<String> findAllCities();
    
    Restaurant findRandomRestaurant();
    
    Restaurant findRandomRestaurantByCuisine(String cuisine);
    
    Restaurant updateRestaurant(Restaurant restaurant);
    
    void deleteRestaurant(Long id);
} 