package com.example.finalproject.demo.service.impl;

import com.example.finalproject.demo.model.Restaurant;
import com.example.finalproject.demo.model.User;
import com.example.finalproject.demo.repository.RestaurantRepository;
import com.example.finalproject.demo.service.RestaurantService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RestaurantServiceImpl implements RestaurantService {
    
    private final RestaurantRepository restaurantRepository;
    
    @Override
    public Restaurant saveRestaurant(Restaurant restaurant) {
        return restaurantRepository.save(restaurant);
    }
    
    @Override
    public Optional<Restaurant> findById(Long id) {
        return restaurantRepository.findById(id);
    }
    
    @Override
    public List<Restaurant> findAllRestaurants() {
        return restaurantRepository.findAll();
    }
    
    @Override
    public List<Restaurant> findByUser(User user) {
        return restaurantRepository.findByUser(user);
    }
    
    @Override
    public List<Restaurant> findByCuisine(String cuisine) {
        return restaurantRepository.findByCuisine(cuisine);
    }
    
    @Override
    public List<Restaurant> findByCity(String city) {
        return restaurantRepository.findByCity(city);
    }
    
    @Override
    public List<Restaurant> findByCuisineAndCity(String cuisine, String city) {
        return restaurantRepository.findByCuisineAndCity(cuisine, city);
    }
    
    @Override
    public List<String> findAllCuisines() {
        return restaurantRepository.findAllCuisines();
    }
    
    @Override
    public List<String> findAllCities() {
        return restaurantRepository.findAllCities();
    }
    
    @Override
    public Restaurant findRandomRestaurant() {
        return restaurantRepository.findRandomRestaurant();
    }
    
    @Override
    public Restaurant findRandomRestaurantByCuisine(String cuisine) {
        return restaurantRepository.findRandomRestaurantByCuisine(cuisine);
    }
    
    @Override
    public Restaurant updateRestaurant(Restaurant restaurant) {
        return restaurantRepository.save(restaurant);
    }
    
    @Override
    public void deleteRestaurant(Long id) {
        restaurantRepository.deleteById(id);
    }
} 