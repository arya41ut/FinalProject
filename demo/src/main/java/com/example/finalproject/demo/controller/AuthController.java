package com.example.finalproject.demo.controller;

import com.example.finalproject.demo.dto.LoginDto;
import com.example.finalproject.demo.dto.UserRegistrationDto;
import com.example.finalproject.demo.model.User;
import com.example.finalproject.demo.service.UserService;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    
    private final UserService userService;
    // Store the email of the last logged-in user
    private static String lastLoggedInUserEmail = null;
    
    private static final Logger logger = LoggerFactory.getLogger(AuthController.class);
    
    @PostMapping("/register")
    public ResponseEntity<?> registerUser(@RequestBody UserRegistrationDto registrationDto) {
        // Check if username already exists
        if (userService.existsByUsername(registrationDto.getUsername())) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Username is already taken!");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
        
        // Check if email already exists
        if (userService.existsByEmail(registrationDto.getEmail())) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Email is already in use!");
            return new ResponseEntity<>(response, HttpStatus.BAD_REQUEST);
        }
        
        // Create new user
        User user = new User();
        user.setUsername(registrationDto.getUsername());
        user.setEmail(registrationDto.getEmail());
        user.setPassword(registrationDto.getPassword());
        
        User savedUser = userService.registerUser(user);
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User registered successfully!");
        response.put("id", savedUser.getId());
        response.put("username", savedUser.getUsername());
        response.put("email", savedUser.getEmail());
        
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }
    
    @PostMapping("/login")
    public ResponseEntity<?> authenticateUser(@RequestBody LoginDto loginDto) {
        // Find user by email
        Optional<User> userOptional = userService.findByEmail(loginDto.getEmail());
        if (userOptional.isEmpty()) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "User not found with email: " + loginDto.getEmail());
            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }
        
        User user = userOptional.get();
        
        // Directly compare passwords
        if (!user.getPassword().equals(loginDto.getPassword())) {
            Map<String, String> response = new HashMap<>();
            response.put("error", "Invalid password!");
            return new ResponseEntity<>(response, HttpStatus.UNAUTHORIZED);
        }
        
        // Store the email of the logged-in user
        lastLoggedInUserEmail = user.getEmail();
        
        Map<String, Object> response = new HashMap<>();
        response.put("message", "User logged in successfully!");
        response.put("id", user.getId());
        response.put("username", user.getUsername());
        response.put("email", user.getEmail());
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestParam(required = false) String email) {
        logger.info("getCurrentUser called with email param: {}", email);
        
        // Step 1: Try to use the provided email parameter if available
        if (email != null && !email.isEmpty()) {
            logger.info("Using provided email parameter: {}", email);
            Optional<User> userOptional = userService.findByEmail(email);
            
            if (userOptional.isPresent()) {
                User user = userOptional.get();
                // Don't send the password back to the client
                user.setPassword(null);
                logger.info("User found by email parameter: {}", user.getUsername());
                return ResponseEntity.ok(user);
            } else {
                logger.warn("No user found with provided email: {}", email);
            }
        }
        
        // Step 2: Fall back to the session-based approach if email param not provided or not found
        logger.info("Falling back to session-based user identification");
        String sessionEmail = getLastLoggedInUserEmail();
        logger.info("Session email: {}", sessionEmail);
        
        if (sessionEmail == null) {
            logger.warn("No email in session");
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "No user is currently logged in");
            errorResponse.put("error", "Authentication required");
            return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
        }
        
        // Find the user by the stored email
        Optional<User> userOptional = userService.findByEmail(sessionEmail);
        
        if (userOptional.isPresent()) {
            User user = userOptional.get();
            // Don't send the password back to the client
            user.setPassword(null);
            logger.info("User found by session: {}", user.getUsername());
            return ResponseEntity.ok(user);
        } else {
            logger.warn("User not found for email in session: {}", sessionEmail);
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("message", "User not found");
            errorResponse.put("error", "User not found");
            return new ResponseEntity<>(errorResponse, HttpStatus.UNAUTHORIZED);
        }
    }
    
    @PostMapping("/logout")
    public ResponseEntity<?> logout() {
        // Clear the stored email
        lastLoggedInUserEmail = null;
        
        Map<String, String> response = new HashMap<>();
        response.put("message", "Logged out successfully");
        return ResponseEntity.ok(response);
    }

    // Update the getter method for lastLoggedInUserEmail
    public static String getLastLoggedInUserEmail() {
        return lastLoggedInUserEmail;
    }
} 