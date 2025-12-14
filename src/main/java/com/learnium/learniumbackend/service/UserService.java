package com.learnium.learniumbackend.service;

import com.learnium.learniumbackend.model.User;
import com.learnium.learniumbackend.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    public User provisionUser(String firebaseUid, String email, String displayName) {
        Optional<User> existing = userRepository.findByFirebaseUid(firebaseUid);
        if (existing.isPresent()) {
            return existing.get();
        }
        User user = User.builder()
                .firebaseUid(firebaseUid)
                .email(email)
                .displayName(displayName)
                .gradeLevel(1) // default or fetch from request/onboarding
                .onboardingDone(false)
                .role("STUDENT")
                .status("ACTIVE")
                .createdAt(java.time.OffsetDateTime.now())
                .updatedAt(java.time.OffsetDateTime.now())
                .build();
        return userRepository.save(user);
    }

    public Optional<User> getByFirebaseUid(String firebaseUid) {
        return userRepository.findByFirebaseUid(firebaseUid);
    }
}
