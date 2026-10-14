/*
 * SecurityConfig
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Configure authentication, password storage and API access.
 */
package com.equitransit.backend.config;


import java.io.IOException;
import java.time.Clock;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.method.configuration.
        EnableMethodSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import com.equitransit.backend.security.LiveJwtConverter;


/** Configures stateless bearer authentication and exact-origin CORS. */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final int MIN_KEY_BYTES = 32;

    /**
     * Uses the same UTC clock for account timestamps and token expiry.
     * @return the UTC clock
     */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }

    /**
     * Creates an adaptive one-way password encoder.
     * @return the versioned password encoder
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Loads a signing key from deployment configuration.
     * @param secret the Base64-encoded secret
     * @return the HMAC signing key
     */
    @Bean
    public SecretKey jwtKey(@Value("${app.jwt.secret}") String secret) {
        byte[] bytes = Base64.getDecoder().decode(secret);

        if (bytes.length < MIN_KEY_BYTES) {
            throw new IllegalStateException("JWT key must have 32 bytes.");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }

    /**
     * Creates the signing encoder.
     * @param key the signing key
     * @return the JWT encoder
     */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    /**
     * Validates the signature, issuer and expiry of access tokens.
     * @param key the signing key
     * @param issuer the accepted issuer
     * @return the JWT decoder
     */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey key,
            @Value("${app.jwt.issuer}") String issuer) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256).build();

        decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(issuer));
        return decoder;
    }

    /**
     * Allows only the configured frontend origin.
     * @param origin the frontend origin
     * @return the CORS configuration
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors-origin}") String origin) {
        CorsConfiguration cors = new CorsConfiguration();
        UrlBasedCorsConfigurationSource source =
                new UrlBasedCorsConfigurationSource();

        cors.setAllowedOrigins(List.of(origin));
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "OPTIONS"));
        cors.setAllowedHeaders(List.of("Authorization", "Content-Type"));
        cors.setAllowCredentials(false);
        source.registerCorsConfiguration("/api/**", cors);
        return source;
    }

    /**
     * Protects profiles and disables session-based authentication.
     * @param http the security builder
     * @param converter the live account validator
     * @param corsSource the explicitly configured frontend-origin policy
     * @return the security filter chain
     * @throws Exception if security configuration fails
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
            LiveJwtConverter converter,
            @Qualifier("corsConfigurationSource")
            CorsConfigurationSource corsSource)
            throws Exception {
        http.cors(cors -> cors.configurationSource(corsSource))
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(
                        SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/swagger-ui.html", "/swagger-ui/**",
                                "/v3/api-docs", "/v3/api-docs/**",
                                "/v3/api-docs.yaml")
                        .permitAll()
                        .requestMatchers("/api/health", "/api/auth/**")
                        .permitAll()
                        .requestMatchers("/api/customers/**", "/api/horses",
                                "/api/horses/**")
                        .hasRole("CUSTOMER")
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(converter))
                        .authenticationEntryPoint((request, response, error) ->
                                writeFailure(response,
                                        HttpStatus.UNAUTHORIZED)))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint((request, response, error) ->
                                writeFailure(response,
                                        HttpStatus.UNAUTHORIZED))
                        .accessDeniedHandler((request, response, error) ->
                                writeFailure(response, HttpStatus.FORBIDDEN)));
        return http.build();
    }

    private void writeFailure(HttpServletResponse response, HttpStatus status)
            throws IOException {
        String message = status == HttpStatus.UNAUTHORIZED
                ? "Authentication required." : "Access denied.";

        response.setStatus(status.value());
        response.setContentType("application/json");
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
