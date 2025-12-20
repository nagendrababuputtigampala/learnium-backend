package com.learnium.learniumbackend.service.auth;

public interface AuthTokenVerifier {

    VerifiedAuthToken verify(String bearerToken);
}
