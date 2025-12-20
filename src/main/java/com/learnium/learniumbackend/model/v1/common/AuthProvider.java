package com.learnium.learniumbackend.model.v1.common;

public enum AuthProvider {
    google("google"),
    email("email"),
    facebook("facebook");

    AuthProvider(String value) {
    }
}
