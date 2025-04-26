package com.example.finalproject.demo.service;

import com.example.finalproject.demo.model.User;
import com.example.finalproject.demo.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserDetailsServiceImpl implements UserDetailsService {
    
    private final UserRepository userRepository;
    
    @Override
    public UserDetails loadUserByUsername(String usernameOrEmail) throws UsernameNotFoundException {
        // Try to find by username first
        Optional<User> userOptional = userRepository.findByUsername(usernameOrEmail);
        
        // If not found by username, try email
        if (userOptional.isEmpty()) {
            userOptional = userRepository.findByEmail(usernameOrEmail);
        }
        
        // If still not found, throw exception
        User user = userOptional.orElseThrow(() -> 
            new UsernameNotFoundException("User not found with username or email: " + usernameOrEmail)
        );
        
        return new org.springframework.security.core.userdetails.User(
                user.getUsername(),
                user.getPassword(),
                Collections.singletonList(new SimpleGrantedAuthority("USER"))
        );
    }
} 