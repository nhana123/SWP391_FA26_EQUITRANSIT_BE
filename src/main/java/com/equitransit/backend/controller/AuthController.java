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
import com.equitransit.backend.dto.request.RegisterRequest;
import com.equitransit.backend.dto.request.LoginRequest;
import com.equitransit.backend.dto.request.EmailRequest;
import com.equitransit.backend.dto.request.TokenRequest;
import com.equitransit.backend.dto.request.ResetPasswordRequest;
import com.equitransit.backend.dto.response.MessageResponse;
import com.equitransit.backend.dto.response.LoginResponse;
import com.equitransit.backend.service.AuthService;


/** Exposes account APIs corresponding to Figma A01, A02 and A03. */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final String GENERIC_EMAIL_MESSAGE =
            "If an eligible account exists, an email will be sent.";

    private final AuthService auth;

    /**
     * Creates the authentication controller.
     * @param auth the authentication service
     */
    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    /**
     * Registers an individual customer awaiting email verification.
     * @param request the customer registration input
     * @return the next-step message
     */
    @PostMapping("/register")
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
    public LoginResponse login(@Valid @RequestBody LoginRequest request) {
        return auth.login(request);
    }

    /**
     * Activates a customer using the emailed token.
     * @param request the verification token
     * @return the activation result
     */
    @PostMapping("/verify-email")
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
