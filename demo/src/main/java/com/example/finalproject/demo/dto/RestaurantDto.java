package com.example.finalproject.demo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantDto {
    
    private Long id;
    private String name;
    private String cuisine;
    private String address;
    private String city;
    private String state;
    private String zipCode;
    private String phoneNumber;
    private String website;
    private Double rating;
    private String priceRange;
    private String description;
    private List<String> imageUrls;
    private Long userId;
} 