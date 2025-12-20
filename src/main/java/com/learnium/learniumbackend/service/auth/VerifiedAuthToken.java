package com.learnium.learniumbackend.service.auth;


import lombok.*;

@Getter
@AllArgsConstructor
public class VerifiedAuthToken {
    private final String uid;
    private final String email;
    private final boolean emailVerified;
    private final String displayName;
    private final String photoUrl;
    private final AuthProvider provider;

    public enum AuthProvider { GOOGLE, EMAIL, APPLE }
}