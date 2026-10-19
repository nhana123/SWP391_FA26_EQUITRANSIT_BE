/*
 * AuthController
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Expose registration, login, verification and recovery APIs.
 */
package com.equitransit.backend.controller;


import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.http.MediaType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.equitransit.backend.dto.request.RegisterRequest;
import com.equitransit.backend.dto.request.LoginRequest;
import com.equitransit.backend.dto.request.EmailRequest;
import com.equitransit.backend.dto.request.TokenRequest;
import com.equitransit.backend.dto.request.ResetPasswordRequest;
import com.equitransit.backend.dto.response.MessageResponse;
import com.equitransit.backend.dto.response.LoginResponse;
import com.equitransit.backend.service.AuthService;


import com.equitransit.backend.service.RefreshSessionService;
import com.equitransit.backend.service.RefreshSessionService.Failure;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.http.ResponseCookie;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ExceptionHandler;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;

/** Exposes account APIs corresponding to Figma A01, A02 and A03. */
@RestController
@RequestMapping(value = "/api/auth",
        produces = MediaType.APPLICATION_JSON_VALUE)
@Tag(name = "Authentication")
public class AuthController {

    private static final String GENERIC_EMAIL_MESSAGE =
            "If an eligible account exists, an email will be sent.";

    private final AuthService auth;
    private final RefreshSessionService refresh;
    private final String cookieName;
    private final boolean secure;
    private final long ttl;

    /** Safe refresh failure contract; never carries a credential. */
    public record RefreshError(
            @Schema(example = "REFRESH_TOKEN_INVALID") String errorCode,
            @Schema(example = "Refresh authentication failed.")
            String message) { }

    /**
     * Creates the authentication controller.
     * @param auth the authentication service
     */
    public AuthController(AuthService auth, RefreshSessionService refresh,
            @Value("${app.jwt.refresh-cookie-name:eq_refresh_token}")
            String cookieName,
            @Value("${app.jwt.refresh-cookie-secure:false}") boolean secure,
            @Value("${app.jwt.refresh-ttl-seconds:28800}") long ttl) {
        this.auth = auth;
        this.refresh = refresh;
        this.cookieName = cookieName;
        this.secure = secure;
        this.ttl = ttl;
    }

    /**
     * Registers an individual customer awaiting email verification.
     * @param request the customer registration input
     * @return the next-step message
     */
    @PostMapping("/register")
    @Operation(summary = "Register an individual customer")
    @ResponseStatus(HttpStatus.CREATED)
    public MessageResponse register(@Valid @RequestBody RegisterRequest
            request) {
        auth.register(request);
        return message("Account created. Verify your email before signing in.");
    }

    /**
     * Authenticates an active account.
     * @param request the credentials
     * @return the access-token response
     */
    @PostMapping("/login")
    @Operation(summary = "Sign in to a verified active account",
            description = "Returns the existing access JSON plus an "
                    + "HttpOnly refresh cookie. Use credentials: include.")
    public ResponseEntity<LoginResponse> login(
            @Valid @RequestBody LoginRequest request) {
        return session(refresh.login(request));
    }

    /** @param raw private cookie @param role optional tab expectation
     * @return unchanged access-token JSON and rotated HttpOnly cookie */
    @PostMapping("/refresh")
    @Operation(summary = "Rotate an HttpOnly refresh credential",
            description = "No request body. Optional X-Expected-Role guards "
                    + "against cross-role cookie replacement between tabs.")
    @ApiResponse(responseCode = "200", description = "Credential rotated")
    @ApiResponse(responseCode = "401", description = "Refresh rejected",
            content = @io.swagger.v3.oas.annotations.media.Content(
                    schema = @Schema(implementation = RefreshError.class)))
    public ResponseEntity<LoginResponse> refresh(
            @Parameter(hidden = true)
            @CookieValue(name = "${app.jwt.refresh-cookie-name:"
                    + "eq_refresh_token}", required = false) String raw,
            @Parameter(schema = @Schema(example = "COORDINATOR"))
            @RequestHeader(name = "X-Expected-Role", required = false)
            String role) {
        return session(refresh.refresh(raw, role));
    }

    /** @param raw optional cookie @return always an empty logout receipt */
    @PostMapping("/logout")
    @Operation(summary = "Revoke this refresh family and clear its cookie")
    @ApiResponse(responseCode = "204", description = "Cookie cleared")
    public ResponseEntity<Void> logout(
            @Parameter(hidden = true)
            @CookieValue(name = "${app.jwt.refresh-cookie-name:"
                    + "eq_refresh_token}", required = false) String raw) {
        refresh.logout(raw);
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE,
                cookie("", 0)).header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    /** @param failure safe rejection @return stable 401 JSON */
    @ExceptionHandler(Failure.class)
    public ResponseEntity<RefreshError> refreshFailure(Failure failure) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(new RefreshError(failure.code(), failure.getMessage()));
    }

    private ResponseEntity<LoginResponse> session(
            RefreshSessionService.Session result) {
        return ResponseEntity.ok().header(HttpHeaders.SET_COOKIE,
                cookie(result.raw(), ttl))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .body(result.response());
    }

    private String cookie(String raw, long seconds) {
        return ResponseCookie.from(cookieName, raw).httpOnly(true)
                .secure(secure).sameSite("Strict").path("/api/auth")
                .maxAge(seconds).build().toString();
    }

    /**
     * Activates a customer using the emailed token.
     * @param request the verification token
     * @return the activation result
     */
    @PostMapping("/verify-email")
    @Operation(summary = "Verify email using a one-time token")
    public MessageResponse verify(@Valid @RequestBody TokenRequest request) {
        auth.verify(request.getToken());
        return message("Email verified. You can now sign in.");
    }

    /**
     * Requests a new verification link.
     * @param request the email address
     * @return a non-enumerating response
     */
    @PostMapping("/resend-verification")
    @Operation(summary = "Request a new verification email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse resend(@Valid @RequestBody EmailRequest request) {
        auth.resendVerification(request.getEmail());
        return message(GENERIC_EMAIL_MESSAGE);
    }

    /**
     * Requests a password-reset link.
     * @param request the email address
     * @return a non-enumerating response
     */
    @PostMapping("/forgot-password")
    @Operation(summary = "Request a password-reset email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MessageResponse forgot(@Valid @RequestBody EmailRequest request) {
        auth.forgotPassword(request.getEmail());
        return message(GENERIC_EMAIL_MESSAGE);
    }

    /**
     * Changes a password using a valid one-time reset token.
     * @param request the reset input
     * @return the reset result
     */
    @PostMapping("/reset-password")
    @Operation(summary = "Reset password using a one-time token")
    public MessageResponse reset(@Valid @RequestBody ResetPasswordRequest
            request) {
        auth.resetPassword(request);
        return message("Password reset. Sign in with your new password.");
    }

    private MessageResponse message(String value) {
        MessageResponse response = new MessageResponse();

        response.setMessage(value);
        return response;
    }
}
