package com.equitransit.backend.controller;

import com.equitransit.backend.mail.AccountMailOutbox;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** Internal aggregate observability without account or challenge disclosure. */
@RestController
public class AccountMailStatusController {
    private final AccountMailOutbox outbox;

    /** @param outbox delivery counters */
    public AccountMailStatusController(AccountMailOutbox outbox) {
        this.outbox = outbox;
    }

    /** @return state counts, never proof of Inbox delivery */
    @GetMapping(value = "/api/manager/account-mail/status",
            produces = "application/json")
    @PreAuthorize("@domainAccessPolicy.isManager()")
    public Map<String, Map<String, Long>> status() {
        return Map.of("states", outbox.counts(),
                "errors", outbox.errorCounts());
    }
}
