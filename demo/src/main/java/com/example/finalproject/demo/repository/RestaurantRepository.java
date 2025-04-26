package com.example.finalproject.demo.repository;

import com.example.finalproject.demo.model.Restaurant;
import com.example.finalproject.demo.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {
    
    List<Restaurant> findByUser(User user);
    
    List<Restaurant> findByCuisine(String cuisine);
    
    List<Restaurant> findByCity(String city);
    
    @Query("SELECT DISTINCT r.cuisine FROM Restaurant r")
    List<String> findAllCuisines();
    
    @Query("SELECT DISTINCT r.city FROM Restaurant r WHERE r.city IS NOT NULL")
    List<String> findAllCities();
    
    @Query("SELECT r FROM Restaurant r WHERE r.cuisine = :cuisine AND r.city = :city")
    List<Restaurant> findByCuisineAndCity(String cuisine, String city);
    
    @Query(value = "SELECT * FROM restaurants ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Restaurant findRandomRestaurant();
    
    @Query(value = "SELECT * FROM restaurants WHERE cuisine = ?1 ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Restaurant findRandomRestaurantByCuisine(String cuisine);
} 