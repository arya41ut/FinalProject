package com.example.finalproject.demo.dto;

import com.example.finalproject.demo.service.RestaurantSelectionService.SelectionStrategy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
public class RestaurantSelectionRequest {
    private SelectionStrategy strategy;
} 