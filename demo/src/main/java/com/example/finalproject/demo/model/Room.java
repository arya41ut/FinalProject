package com.example.finalproject.demo.model;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "rooms")
public class Room {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
        name = "room_users",
        joinColumns = @JoinColumn(name = "room_id"),
        inverseJoinColumns = @JoinColumn(name = "user_id")
    )
    private List<User> users = new ArrayList<>();
    
    @ElementCollection
    @CollectionTable(name = "restaurant_suggestions", joinColumns = @JoinColumn(name = "room_id"))
    @Column(name = "suggestion")
    private List<String> restaurantSuggestions = new ArrayList<>();
    
    @Column(name = "invite_code", unique = true)
    private String inviteCode;

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public List<User> getUsers() {
        return users;
    }

    public void setUsers(List<User> users) {
        this.users = users;
    }

    public List<String> getRestaurantSuggestions() {
        return restaurantSuggestions;
    }

    public void setRestaurantSuggestions(List<String> restaurantSuggestions) {
        this.restaurantSuggestions = restaurantSuggestions;
    }
    
    public String getInviteCode() {
        return inviteCode;
    }
    
    public void setInviteCode(String inviteCode) {
        this.inviteCode = inviteCode;
    }
} 