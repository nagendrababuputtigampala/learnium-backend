package com.learnium.learniumbackend.config;

import com.learnium.learniumbackend.logging.LoggingContextFilter;
import com.learnium.learniumbackend.security.FirebaseAuthenticationFilter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import java.util.Arrays;
import java.util.stream.Stream;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final FirebaseAuthenticationFilter firebaseAuthenticationFilter;
    private final LoggingContextFilter loggingContextFilter;

    @Value("${security.whitelist.urls}")
    private String[] whitelistUrls;

    private static final String[] SWAGGER_WHITELIST = {
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            "/swagger-ui/index.html"
    };


    public SecurityConfig(FirebaseAuthenticationFilter firebaseAuthenticationFilter,
                          LoggingContextFilter loggingContextFilter) {
        this.firebaseAuthenticationFilter = firebaseAuthenticationFilter;
        this.loggingContextFilter = loggingContextFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        String[] mergedWhitelist = Stream.concat(Arrays.stream(whitelistUrls), Arrays.stream(SWAGGER_WHITELIST))
                .toArray(String[]::new);
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(mergedWhitelist).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(loggingContextFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterBefore(firebaseAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
