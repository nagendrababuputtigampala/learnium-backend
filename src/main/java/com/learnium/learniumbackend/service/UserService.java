package com.learnium.learniumbackend.service;

import com.learnium.learniumbackend.model.User;
import com.learnium.learniumbackend.repository.UserRepository;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

import static com.learnium.learniumbackend.util.Constants.DEFAULT_GRADE;

@Service
public class UserService {
    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    public User provisionUser(String firebaseUid, String email, String displayName, Integer grade) {
        Optional<User> existing = userRepository.findByFirebaseUid(firebaseUid);
        return existing.orElseGet(() -> saveNewUser(firebaseUid, email, displayName, grade, StringUtils.EMPTY));
    }

    private User saveNewUser(String firebaseUid, String email, String displayName, Integer grade, String password) {
        User user = User.builder()
                .firebaseUid(firebaseUid)
                .email(email)
                .displayName(displayName)
                .gradeLevel(grade)
                .password(StringUtils.isNotBlank(password) ? passwordEncoder.encode(password) : StringUtils.EMPTY)
                .onboardingDone(false)
                .role("STUDENT")
                .status("ACTIVE")
                .createdAt(java.time.OffsetDateTime.now())
                .updatedAt(java.time.OffsetDateTime.now())
                .build();
        return userRepository.save(user);
    }

}
