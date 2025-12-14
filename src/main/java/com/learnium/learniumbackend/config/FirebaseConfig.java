package com.learnium.learniumbackend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {
    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    public FirebaseApp firebaseApp() {
        try (InputStream serviceAccount = getClass().getClassLoader().getResourceAsStream("firebase-service-account.json")) {
            if (serviceAccount == null) {
                throw new IllegalStateException("firebase-service-account.json not found in resources");
            }
            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(GoogleCredentials.fromStream(serviceAccount))
                    .build();
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp app = FirebaseApp.initializeApp(options);
                logger.info("FirebaseApp initialized successfully");
                return app;
            } else {
                return FirebaseApp.getInstance();
            }
        } catch (IOException e) {
            logger.error("Failed to initialize FirebaseApp", e);
            throw new RuntimeException(e);
        }
    }
}
