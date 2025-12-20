package com.learnium.learniumbackend.service.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.learnium.learniumbackend.exception.InvalidAuthTokenException;
import org.springframework.stereotype.Component;

@Component
public class FirebaseAuthTokenVerifier implements AuthTokenVerifier {

    @Override
    public VerifiedAuthToken verify(String bearerToken) {
        try {
            FirebaseToken token = FirebaseAuth.getInstance().verifyIdToken(bearerToken);

            return new VerifiedAuthToken(
                    token.getUid(),
                    token.getEmail(),
                    Boolean.TRUE.equals(token.isEmailVerified()),
                    token.getName(),
                    token.getPicture(),
                    VerifiedAuthToken.AuthProvider.GOOGLE
            );
        } catch (Exception e) {
            throw new InvalidAuthTokenException("Invalid or expired Firebase token", e);
        }
    }
}
