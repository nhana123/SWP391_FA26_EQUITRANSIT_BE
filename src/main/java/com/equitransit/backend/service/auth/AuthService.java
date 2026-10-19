/*
 * AuthService
 * Author: EquiTransit development team
 * Date: 2026-10-12
 * Copyright: see repository license.
 * Purpose: Implement account registration, activation and recovery.
 */
package com.equitransit.backend.service.auth;


import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.equitransit.backend.dto.common.ValidationLimits;
import com.equitransit.backend.dto.auth.request.RegisterRequest;
import com.equitransit.backend.dto.auth.request.LoginRequest;
import com.equitransit.backend.dto.auth.request.ResetPasswordRequest;
import com.equitransit.backend.dto.auth.response.LoginResponse;
import com.equitransit.backend.entity.auth.AccountRole;
import com.equitransit.backend.entity.auth.UserAccount;
import com.equitransit.backend.entity.customer.Customer;
import com.equitransit.backend.entity.auth.AuthenticationToken;
import com.equitransit.backend.exception.ApiException;
import com.equitransit.backend.mail.EmailEvent;
import com.equitransit.backend.repository.auth.AccountRoleRepository;
import com.equitransit.backend.repository.auth.UserAccountRepository;
import com.equitransit.backend.repository.customer.CustomerRepository;
import com.equitransit.backend.repository.auth.AuthenticationTokenRepository;
import com.equitransit.backend.security.JwtTokenService;
import com.equitransit.backend.security.TokenTools;


/** Implements individual registration, verification and account recovery. */
@Service
public class AuthService {

    private static final String VERIFY_EMAIL = "VERIFY_EMAIL";
    private static final String RESET_PASSWORD = "RESET_PASSWORD";
    private static final long VERIFY_HOURS = 24;
    private static final long RESET_MINUTES = 30;

    private final AccountRoleRepository roles;
    private final UserAccountRepository users;
    private final CustomerRepository customers;
    private final AuthenticationTokenRepository tokens;
    private final PasswordEncoder passwords;
    private final JwtTokenService jwt;
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final String frontend;
    private final String dummyHash;

    /**
     * Creates the authentication service.
     * @param roles the role repository
     * @param users the account repository
     * @param customers the customer repository
     * @param tokens the token repository
     * @param passwords the password encoder
     * @param jwt the access-token issuer
     * @param clock the UTC clock
     * @param events the after-commit event publisher
     * @param frontend the frontend base URL
     */
    public AuthService(AccountRoleRepository roles,
            UserAccountRepository users, CustomerRepository customers,
            AuthenticationTokenRepository tokens, PasswordEncoder passwords,
            JwtTokenService jwt, Clock clock,
            ApplicationEventPublisher events,
            @Value("${app.frontend-url}") String frontend) {
        this.roles = roles;
        this.users = users;
        this.customers = customers;
        this.tokens = tokens;
        this.passwords = passwords;
        this.jwt = jwt;
        this.clock = clock;
        this.events = events;
        this.frontend = frontend;
        this.dummyHash = passwords.encode(TokenTools.randomToken());
    }

    /**
     * Creates an inactive customer and a verification token atomically.
     * @param request the validated registration input
     */
    @Transactional
    public void register(RegisterRequest request) {
        String email = normalizeEmail(request.getEmail());
        AccountRole role;
        UserAccount user = new UserAccount();
        Customer customer = new Customer();

        checkPassword(request.getPassword(), request.getConfirmPassword());
        if (users.findByEmailIgnoreCase(email).isPresent()) {
            throw new ApiException(HttpStatus.CONFLICT,
                    "Email is already registered. Sign in or reset password.");
        }
        role = roles.findByRoleCode("CUSTOMER").orElseThrow(() ->
                new IllegalStateException("CUSTOMER role is not configured."));
        user.setRole(role);
        user.setEmail(email);
        user.setPasswordHash(passwords.encode(request.getPassword()));
        user.setStatus("INACTIVE");
        user.setCreatedAt(now());
        users.saveAndFlush(user);
        customer.setUser(user);
        customer.setFullName(request.getFullName().trim());
        customer.setPhone(trimOptional(request.getPhone()));
        customer.setAddress(trimOptional(request.getAddress()));
        customers.saveAndFlush(customer);
        issueOneTimeToken(user, VERIFY_EMAIL);
    }

    /**
     * Authenticates an active account without disclosing failure details.
     * @param request the sign-in credentials
     * @return the access-token response
     */
    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        UserAccount user = users.findByEmailIgnoreCase(
                normalizeEmail(request.getEmail())).orElse(null);
        String hash = user == null ? dummyHash : user.getPasswordHash();
        boolean valid = request.getPassword()
                .getBytes(StandardCharsets.UTF_8).length
                <= ValidationLimits.PASSWORD_MAX_LENGTH
                && passwords.matches(request.getPassword(), hash);

        if (!valid || user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new ApiException(HttpStatus.UNAUTHORIZED,
                    "Invalid credentials or account unavailable.");
        }
        return jwt.issue(user);
    }

    /**
     * Activates an inactive customer using a one-time token.
     * @param rawToken the submitted verification token
     */
    @Transactional
    public void verify(String rawToken) {
        AuthenticationToken token = lockValidToken(rawToken, VERIFY_EMAIL);
        UserAccount user = token.getUser();

        if (!"INACTIVE".equals(user.getStatus())
                || !"CUSTOMER".equals(user.getRole().getRoleCode())
                || tokens.existsByUserUserIdAndPurposeAndUsedAtIsNotNull(
                        user.getUserId(), VERIFY_EMAIL)) {
            throw invalidToken();
        }
        user.setStatus("ACTIVE");
        token.setUsedAt(now());
    }

    /**
     * Issues a fresh verification link when an eligible account exists.
     * @param email the submitted email
     */
    @Transactional
    public void resendVerification(String email) {
        Long found = users.findIdByEmail(
                normalizeEmail(email)).orElse(null);
        UserAccount user;

        if (found == null) {
            return;
        }
        user = users.findLockedById(found).orElseThrow();
        if ("INACTIVE".equals(user.getStatus())
                && "CUSTOMER".equals(user.getRole().getRoleCode())
                && tokens.existsByUserUserIdAndPurpose(
                        user.getUserId(), VERIFY_EMAIL)
                && !tokens.existsByUserUserIdAndPurposeAndUsedAtIsNotNull(
                        user.getUserId(), VERIFY_EMAIL)) {
            issueOneTimeToken(user, VERIFY_EMAIL);
        }
    }

    /**
     * Issues a password-reset link without revealing account existence.
     * @param email the submitted email
     */
    @Transactional
    public void forgotPassword(String email) {
        Long found = users.findIdByEmail(
                normalizeEmail(email)).orElse(null);
        UserAccount user;

        if (found == null) {
            return;
        }
        user = users.findLockedById(found).orElseThrow();
        if ("ACTIVE".equals(user.getStatus())) {
            issueOneTimeToken(user, RESET_PASSWORD);
        }
    }

    /**
     * Resets a password and invalidates outstanding reset links.
     * @param request the validated reset input
     */
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        AuthenticationToken token;
        UserAccount user;

        checkPassword(request.getPassword(), request.getConfirmPassword());
        token = lockValidToken(request.getToken(), RESET_PASSWORD);
        user = token.getUser();
        if (!"ACTIVE".equals(user.getStatus())) {
            throw invalidToken();
        }
        user.setPasswordHash(passwords.encode(request.getPassword()));
        token.setUsedAt(now());
        tokens.invalidate(user.getUserId(), RESET_PASSWORD);
    }

    private AuthenticationToken lockValidToken(String raw, String purpose) {
        String hash = TokenTools.hash(raw);
        Long userId = tokens.findOwnerByHash(hash)
                .orElseThrow(this::invalidToken);
        UserAccount user = users.findLockedById(userId)
                .orElseThrow(this::invalidToken);
        AuthenticationToken token = tokens.findLockedByHash(hash)
                .orElseThrow(this::invalidToken);

        if (!purpose.equals(token.getPurpose()) || token.getUsedAt() != null
                || !token.getExpiresAt().isAfter(now())) {
            throw invalidToken();
        }
        token.setUser(user);
        return token;
    }

    private void issueOneTimeToken(UserAccount user, String purpose) {
        String raw = TokenTools.randomToken();
        LocalDateTime created = now();
        AuthenticationToken token = new AuthenticationToken();
        EmailEvent event = new EmailEvent();
        boolean verification = VERIFY_EMAIL.equals(purpose);
        String route = verification ? "/verify-email" : "/reset-password";

        tokens.invalidate(user.getUserId(), purpose);
        token.setUser(user);
        token.setTokenHash(TokenTools.hash(raw));
        token.setPurpose(purpose);
        token.setCreatedAt(created);
        token.setExpiresAt(verification ? created.plusHours(VERIFY_HOURS)
                : created.plusMinutes(RESET_MINUTES));
        tokens.saveAndFlush(token);
        event.setRecipient(user.getEmail());
        event.setSubject(verification ? "Verify your EquiTransit account"
                : "Reset your EquiTransit password");
        event.setBody("Open this link in the frontend: " + frontend + route
                + "?token=" + raw + "\nThis link can be used only once.");
        events.publishEvent(event);
    }

    /**
     * Internal provisioning hook; caller owns Manager authorization.
     * @param password initial credential, never persisted as plain text
     * @return adaptive hash using the same policy as ordinary registration
     */
    public String encodeStaffPassword(String password) {
        if (password == null || password.length() < 12) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Password must contain at least 12 characters.");
        }
        checkPassword(password, password);
        return passwords.encode(password);
    }

    private void checkPassword(String password, String confirmation) {
        if (!password.equals(confirmation)) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Password confirmation does not match.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length
                > ValidationLimits.PASSWORD_MAX_LENGTH) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Password must not exceed 72 UTF-8 bytes.");
        }
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String trimOptional(String value) {
        return value == null ? null : value.trim();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).withNano(0);
    }

    private ApiException invalidToken() {
        return new ApiException(HttpStatus.BAD_REQUEST,
                "The link is invalid, expired or already used.");
    }
}
