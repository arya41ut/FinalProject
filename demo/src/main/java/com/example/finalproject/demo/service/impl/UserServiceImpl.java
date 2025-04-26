package com.example.finalproject.demo.service.impl;

import com.example.finalproject.demo.model.User;
import com.example.finalproject.demo.repository.UserRepository;
import com.example.finalproject.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserServiceImpl implements UserService {
    
    private final UserRepository userRepository;
    // private final PasswordEncoder passwordEncoder;
    
    @Override
    public User registerUser(User user) {
        user.setPassword(user.getPassword());
        return userRepository.save(user);
    }
    
    @Override
    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }
    
    @Override
    public Optional<User> findByEmail(String email) {
        return userRepository.findByEmail(email);
    }
    
    @Override
    public List<User> findAllUsers() {
        return userRepository.findAll();
    }
    
    @Override
    public Optional<User> findById(Long id) {
        return userRepository.findById(id);
    }
    
    @Override
    public User updateUser(User user) {
        if (user.getPassword() != null && !user.getPassword().startsWith("$2a$")) {
            user.setPassword(user.getPassword());
        }
        return userRepository.save(user);
    }
    
    @Override
    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }
    
    @Override
    public boolean existsByUsername(String username) {
        return userRepository.existsByUsername(username);
    }
    
    @Override
    public boolean existsByEmail(String email) {
        return userRepository.existsByEmail(email);
    }
} 