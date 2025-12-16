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

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final FirebaseAuthenticationFilter firebaseAuthenticationFilter;
    private final LoggingContextFilter loggingContextFilter;

    @Value("${security.whitelist.urls}")
    private String[] whitelistUrls;

    public SecurityConfig(FirebaseAuthenticationFilter firebaseAuthenticationFilter,
                          LoggingContextFilter loggingContextFilter) {
        this.firebaseAuthenticationFilter = firebaseAuthenticationFilter;
        this.loggingContextFilter = loggingContextFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(whitelistUrls).permitAll()
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
