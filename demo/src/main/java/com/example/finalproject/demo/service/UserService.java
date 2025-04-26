package com.example.finalproject.demo.service;

import com.example.finalproject.demo.model.User;

import java.util.List;
import java.util.Optional;

public interface UserService {
    
    User registerUser(User user);
    
    Optional<User> findByUsername(String username);
    
    Optional<User> findByEmail(String email);
    
    List<User> findAllUsers();
    
    Optional<User> findById(Long id);
    
    User updateUser(User user);
    
    void deleteUser(Long id);
    
    boolean existsByUsername(String username);
    
    boolean existsByEmail(String email);
} 