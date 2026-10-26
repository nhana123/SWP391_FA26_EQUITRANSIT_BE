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
import com.equitransit.backend.security.TokenTools;
import com.equitransit.backend.service.RefreshSessionService;


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
    private final Clock clock;
    private final ApplicationEventPublisher events;
    private final String frontend;
    private final RefreshSessionService refresh;

    /**
     * Creates the authentication service.
     * @param roles the role repository
     * @param users the account repository
     * @param customers the customer repository
     * @param tokens the token repository
     * @param passwords the password encoder
     * @param clock the UTC clock
     * @param events the after-commit event publisher
     * @param frontend the frontend base URL
     */
    public AuthService(AccountRoleRepository roles,
            UserAccountRepository users, CustomerRepository customers,
            AuthenticationTokenRepository tokens, PasswordEncoder passwords,
            Clock clock,
            ApplicationEventPublisher events,
            @Value("${app.frontend-url}") String frontend,
            RefreshSessionService refresh) {
        this.roles = roles;
        this.users = users;
        this.customers = customers;
        this.tokens = tokens;
        this.passwords = passwords;
        this.clock = clock;
        this.events = events;
        this.frontend = frontend;
        this.refresh = refresh;
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
    @Transactional
    public LoginResponse login(LoginRequest request) {
        return refresh.login(request).response();
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
        refresh.revokeUser(user.getUserId(), "PASSWORD_CHANGED");
    }

    /** @param user account key @param reason safe revocation code */
    @Transactional(propagation =
            org.springframework.transaction.annotation.Propagation.MANDATORY)
    public void revokeSessions(long user, String reason) {
        refresh.revokeUser(user, reason);
    }

    private AuthenticationToken lockValidToken(String raw, String purpose) {
        String normalized = raw.trim().replace(" ", "");
        String hash = TokenTools.hash(normalized);
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
        boolean verification = VERIFY_EMAIL.equals(purpose);
        String raw = generateOtp();
        LocalDateTime created = now();
        AuthenticationToken token = new AuthenticationToken();
        EmailEvent event = new EmailEvent();
        String route = verification ? "/verify-email" : "/reset-password";
        String link = frontend + route + "?token=" + raw;

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
        event.setBody(buildEmailBody(link, raw, verification));
        event.setHtmlBody(buildEmailHtml(link, raw, verification));
        events.publishEvent(event);
    }

    private String buildEmailBody(
            String link, String raw, boolean verification) {
        String title = verification
                ? "EquiTransit Account Verification"
                : "EquiTransit Password Reset";
        String intro = verification
                ? "Thank you for registering. Please verify your email using "
                        + "one of the two methods below to complete activation:"
                : "We received a password reset request. Please proceed using "
                        + "one of the two methods below:";
        String m1 = verification
                ? "METHOD 1 - CONFIRM VIA LINK:\n"
                        + "Click the link below to verify your account:\n"
                : "METHOD 1 - RESET VIA LINK:\n"
                        + "Click the link below to reset your password:\n";
        String m2 = verification
                ? "METHOD 2 - ENTER OTP CODE ON THE WEBSITE:\n"
                        + "Enter this 6-digit code on the registration page:\n"
                : "METHOD 2 - ENTER OTP CODE ON THE WEBSITE:\n"
                        + "Enter this 6-digit code on the reset page:\n";

        return title + "\n\nHello,\n\n" + intro + "\n\n"
                + m1 + link + "\n\n--- OR ---\n\n"
                + m2 + formatOtp(raw) + "\n\n"
                + "Note: This one-time code expires soon. "
                + "Never share this code with anyone.\n\n"
                + "(c) 2026 EquiTransit. All rights reserved.";
    }

    private String buildEmailHtml(
            String link, String raw, boolean verification) {
        String title = verification
                ? "Account Verification"
                : "Password Reset Request";
        String intro = verification
                ? "Thank you for registering. Please verify your email using "
                        + "one of the two methods below to complete activation:"
                : "We received a password reset request. Please proceed using "
                        + "one of the two methods below:";
        String btnText = verification
                ? "CONFIRM ACCOUNT NOW"
                : "RESET PASSWORD NOW";
        String otpLabel = verification
                ? "Enter this 6-digit verification code on your screen"
                : "Enter this 6-digit verification code on the reset page";

        return "<div style=\"font-family:-apple-system,BlinkMacSystemFont,"
                + "'Segoe UI',Roboto,Helvetica,Arial,sans-serif;"
                + "max-width:480px;margin:0 auto;padding:28px 24px;"
                + "border:1px solid #dce5de;border-radius:12px;"
                + "background:#ffffff;text-align:center;\">"
                + "<h2 style=\"color:#1e4a30;letter-spacing:1px;"
                + "margin:0 0 6px;\">EQUITRANSIT</h2>"
                + "<h3 style=\"color:#2c4737;margin:0 0 16px;\">"
                + title + "</h3>"
                + "<p style=\"color:#496656;font-size:14px;line-height:1.6;"
                + "text-align:left;margin:0 0 20px;\">Hello,<br><br>"
                + intro + "</p>"
                + "<div style=\"margin:20px 0;\">"
                + "<a href=\"" + link + "\" style=\"background-color:#1e4a30;"
                + "color:#ffffff;text-decoration:none;font-weight:bold;"
                + "font-size:14px;padding:12px 28px;border-radius:8px;"
                + "display:inline-block;\">" + btnText + "</a></div>"
                + "<div style=\"color:#88998e;font-size:12px;font-weight:bold;"
                + "margin:20px 0;\">─── OR ───</div>"
                + "<p style=\"font-size:13.5px;color:#496656;margin:0 0 8px;\">"
                + otpLabel + ":</p>"
                + "<div style=\"display:inline-block;background:#f4f8f5;"
                + "border:1.5px dashed #1e4a30;border-radius:8px;"
                + "padding:10px 24px;font-size:28px;font-weight:bold;"
                + "letter-spacing:6px;color:#1e4a30;font-family:monospace;\">"
                + formatOtp(raw) + "</div>"
                + "<p style=\"font-size:12px;color:#64756d;margin:24px 0 0;"
                + "border-top:1px solid #eaefe9;padding-top:14px;"
                + "line-height:1.5;\">"
                + "Note: This one-time code expires soon. Never share it with "
                + "anyone.<br>&copy; 2026 EquiTransit. All rights reserved.</p>"
                + "</div>";
    }

    private String formatOtp(String raw) {
        if (raw != null && raw.length() == 6) {
            return raw.substring(0, 3) + " " + raw.substring(3);
        }
        return raw;
    }

    private String generateOtp() {
        for (int i = 0; i < 5; i++) {
            String candidate = TokenTools.randomOtp();
            String hash = TokenTools.hash(candidate);
            if (tokens.findByTokenHash(hash).isEmpty()) {
                return candidate;
            }
        }
        return TokenTools.randomOtp();
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
