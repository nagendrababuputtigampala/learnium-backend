package com.learnium.learniumbackend.entity;

import com.learnium.learniumbackend.model.User;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@AllArgsConstructor
@NoArgsConstructor
@Data
public class AuthResponse {
    private User userProfile;
    private boolean onboardingRequired;

}

