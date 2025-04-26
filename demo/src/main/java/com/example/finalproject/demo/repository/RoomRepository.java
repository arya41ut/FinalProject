package com.example.finalproject.demo.repository;

import com.example.finalproject.demo.model.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RoomRepository extends JpaRepository<Room, Long> {
    // Additional query methods can be defined here if needed

    // Find a room by its invite code
    Optional<Room> findByInviteCode(String inviteCode);
} 