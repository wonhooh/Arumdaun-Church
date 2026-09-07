package com.arumdaun.church;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class SecurityConfig {
    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/", "/index.html", "/api/health", "/api/lots").permitAll()
                .requestMatchers("/api/clients").hasRole("ADMIN")
                .requestMatchers("/admin.html", "/api/admin/**").hasRole("ADMIN")
                .anyRequest().authenticated())
                .httpBasic(httpBasic -> {
                })
                .csrf(csrf -> csrf.disable());
        return http.build();
    }

    @Bean
    UserDetailsService adminUser(
            @Value("${CEMETERY_ADMIN_USERNAME:admin}") String username,
            @Value("${CEMETERY_ADMIN_PASSWORD:change-me}") String password) {
        return new InMemoryUserDetailsManager(User.withUsername(username)
                .password("{noop}" + password)
                .roles("ADMIN")
                .build());
    }
}