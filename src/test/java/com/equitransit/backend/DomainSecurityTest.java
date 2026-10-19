/*
 * DomainSecurityTest
 * Author: EquiTransit development team
 * Date: 2026-10-13
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify live domain identity and service method authorization.
 */
package com.equitransit.backend;


import java.time.LocalDateTime;
import java.time.ZoneOffset;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.
        AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.
        AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import com.equitransit.backend.entity.auth.AccountRole;
import com.equitransit.backend.entity.customer.Customer;
import com.equitransit.backend.entity.auth.UserAccount;
import com.equitransit.backend.repository.auth.AccountRoleRepository;
import com.equitransit.backend.repository.customer.CustomerRepository;
import com.equitransit.backend.repository.auth.UserAccountRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.security.DomainAccessPolicy;
import com.equitransit.backend.security.JwtTokenService;
import com.equitransit.backend.security.LiveJwtConverter;
import com.equitransit.backend.security.TokenTools;
import com.equitransit.backend.service.customer.CustomerService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.
        MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.
        MockMvcResultMatchers.status;


/** Uses rollback fixtures and actual service proxies, not test-only gates. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class DomainSecurityTest {

    @Autowired
    private UserAccountRepository users;
    @Autowired
    private CustomerRepository customers;
    @Autowired
    private AccountRoleRepository roles;
    @Autowired
    private PasswordEncoder passwords;
    @Autowired
    private JwtTokenService issuer;
    @Autowired
    private JwtDecoder decoder;
    @Autowired
    private LiveJwtConverter converter;
    @Autowired
    private CurrentActorService actors;
    @Autowired
    private DomainAccessPolicy policy;
    @Autowired
    private CustomerService profiles;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private DataSource dataSource;
    @Autowired
    private MockMvc mvc;

    /**
     * Refuses fixture writes outside the dedicated test databases.
     * @throws Exception if the datasource cannot be inspected
     */
    @BeforeEach
    public void guardTestDatabase() throws Exception {
        String url;
        String database;

        SecurityContextHolder.clearContext();
        try (var connection = dataSource.getConnection()) {
            url = connection.getMetaData().getURL();
            database = connection.getCatalog();
        }
        assertThat(url.split(";")[0].equals("jdbc:h2:mem:auth")
                || (url.startsWith("jdbc:sqlserver:")
                        && "EquiTransit_AuthTest".equals(database))).isTrue();
    }

    /** Removes thread identity while Spring rolls back the test fixtures. */
    @AfterEach
    public void clearIdentity() {
        SecurityContextHolder.clearContext();
    }

    /** Verifies the real service proxy rejects anonymous callers. */
    @Test
    public void anonymousCannotBypassMethodSecurity() {
        assertThatThrownBy(() -> profiles.getCurrentCustomer())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    /** Checks ownership against the customer key, not a supplied account ID. */
    @Test
    public void customerIdentityAndOwnershipComeFromDatabase() {
        UserAccount user = account("CUSTOMER");
        Customer customer = customer(user);
        CurrentActor actor;

        authenticate(user);
        actor = actors.current();
        assertThat(actor.userId()).isEqualTo(user.getUserId());
        assertThat(actor.customerId()).isEqualTo(customer.getCustomerId());
        assertThat(actor.staffId()).isNull();
        policy.requireCustomerOwner(customer.getCustomerId());
        assertThat(profiles.getCurrentCustomer().getEmail())
                .isEqualTo(user.getEmail());
        assertThatThrownBy(() -> policy.requireCustomerOwner(
                customer.getCustomerId() + 1))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> policy.requireManager())
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> policy.requireAssignedStaff(
                "DRIVER", user.getUserId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    /**
     * Denies the profile at method level as well as through the HTTP filter.
     * @throws Exception if the request cannot be executed
     */
    @Test
    public void managerCannotUseCustomerServiceOrEndpoint() throws Exception {
        UserAccount user = staffAccount("MANAGER");
        String access = authenticate(user);

        policy.requireManager();
        assertThatThrownBy(() -> profiles.getCurrentCustomer())
                .isInstanceOf(AccessDeniedException.class);
        mvc.perform(get("/api/customers/me")
                .header("Authorization", "Bearer " + access))
                .andExpect(status().isForbidden());
    }

    /** Rejects missing profiles instead of using UserID as StaffID. */
    @Test
    public void missingStaffProfileFailsClosed() {
        UserAccount user = account("DRIVER");

        authenticate(user);
        assertThatThrownBy(() -> actors.current())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    /** Confirms staff assignments require both the staff key and role. */
    @Test
    public void staffKeyIsDistinctAndAssignmentDoesNotGrantAnotherRole() {
        UserAccount user = staffAccount("DRIVER");
        CurrentActor actor;

        authenticate(user);
        actor = actors.current();
        if (actor.staffId().equals(actor.userId())) {
            account("CUSTOMER");
            user = staffAccount("DRIVER");
            authenticate(user);
            actor = actors.current();
        }
        assertThat(actor.staffId()).isNotEqualTo(actor.userId());
        assertThat(actor.customerId()).isNull();
        policy.requireAssignedStaff("DRIVER", actor.staffId());
        assertThatThrownBy(() -> policy.requireAssignedStaff(
                "DRIVER", actors.current().userId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> policy.requireAssignedStaff(
                "SPECIALIST", actors.current().staffId()))
                .isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(() -> policy.requireAssignedStaff("DRIVER", null))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** Rejects disabled accounts even if thread authentication still exists. */
    @Test
    public void disabledOrChangedCredentialsInvalidateCurrentActor() {
        UserAccount user = account("CUSTOMER");

        customer(user);
        authenticate(user);
        user.setPasswordHash(passwords.encode("ChangedPassword456!"));
        users.saveAndFlush(user);
        assertThatThrownBy(() -> actors.current())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
        authenticate(user);
        user.setStatus("LOCKED");
        users.saveAndFlush(user);
        assertThatThrownBy(() -> actors.current())
                .isInstanceOf(AuthenticationCredentialsNotFoundException.class);
    }

    private UserAccount account(String roleCode) {
        AccountRole role = roles.findByRoleCode(roleCode).orElse(null);
        UserAccount user = new UserAccount();

        if (role == null) {
            role = new AccountRole();
            role.setRoleCode(roleCode);
            roles.saveAndFlush(role);
        }
        user.setRole(role);
        user.setEmail(TokenTools.randomToken() + "@example.invalid");
        user.setPasswordHash(passwords.encode("ExamplePassword123!"));
        user.setStatus("ACTIVE");
        user.setCreatedAt(LocalDateTime.now(ZoneOffset.UTC).withNano(0));
        return users.saveAndFlush(user);
    }

    private UserAccount staffAccount(String roleCode) {
        UserAccount user = account(roleCode);

        jdbc.update("INSERT INTO dbo.StaffProfile (UserID, FullName) "
                + "VALUES (?, ?)", user.getUserId(), "Test staff");
        return user;
    }

    private Customer customer(UserAccount user) {
        Customer customer = new Customer();

        customer.setUser(user);
        customer.setFullName("Test customer");
        return customers.saveAndFlush(customer);
    }

    private String authenticate(UserAccount user) {
        String access = issuer.issue(user).getAccessToken();

        SecurityContextHolder.getContext().setAuthentication(
                converter.convert(decoder.decode(access)));
        return access;
    }
}
