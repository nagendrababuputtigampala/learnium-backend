package com.learnium.learniumbackend.entity.response.auth;

import com.learnium.learniumbackend.model.v1.user.StudentProfile;
import com.learnium.learniumbackend.model.v1.user.UserAccount;
import com.learnium.learniumbackend.service.auth.VerifiedAuthToken;
import lombok.*;

import java.util.List;
import java.util.Map;

@Getter
@Setter
@AllArgsConstructor
@Builder
public class AuthBootstrapResult {
    private final boolean newUser;
    private final VerifiedAuthToken authToken;
    private final UserAccount user;
    private final StudentProfile studentProfile;
    private final List<String> roles;
    private final List<String> scopes;
    private final Map<String, Boolean> featureFlags;
}
