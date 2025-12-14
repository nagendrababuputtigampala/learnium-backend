package com.learnium.learniumbackend.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.IOException;

@Configuration
public class FirebaseConfig {
    private static final Logger logger = LoggerFactory.getLogger(FirebaseConfig.class);

    @Bean
    public FirebaseApp firebaseApp() {
        try {
            // Uses Application Default Credentials:
            // - if GOOGLE_APPLICATION_CREDENTIALS is set, it reads that JSON file
            // - otherwise uses the environment’s default (e.g., GCP workload identity)
            GoogleCredentials credentials = GoogleCredentials.getApplicationDefault();

            FirebaseOptions options = FirebaseOptions.builder()
                    .setCredentials(credentials)
                    .build();

            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp app = FirebaseApp.initializeApp(options);
                logger.info("FirebaseApp initialized using Application Default Credentials");
                return app;
            } else {
                return FirebaseApp.getInstance();
            }
        } catch (IOException e) {
            logger.error("Failed to initialize FirebaseApp. Set GOOGLE_APPLICATION_CREDENTIALS to your service account JSON path.", e);
            throw new RuntimeException(e);
        }
    }
}
