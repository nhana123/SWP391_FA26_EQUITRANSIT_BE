/*
 * ReviewF08StaffFileAuthorizationTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify HTTP authorization for staff file access and case scoping.
 */
package com.equitransit.backend;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.equitransit.backend.entity.CaseRole;
import com.equitransit.backend.entity.DocumentRequirement;
import com.equitransit.backend.entity.DocumentSubmission;
import com.equitransit.backend.entity.DocumentVersion;
import com.equitransit.backend.entity.HorseDocument;
import com.equitransit.backend.exception.OrderException;
import com.equitransit.backend.repository.DocumentRequirementRepository;
import com.equitransit.backend.repository.DocumentSubmissionRepository;
import com.equitransit.backend.repository.DocumentVersionRepository;
import com.equitransit.backend.repository.HorseDocumentRepository;
import com.equitransit.backend.security.CurrentActor;
import com.equitransit.backend.security.CurrentActorService;
import com.equitransit.backend.service.CaseAssignmentAccess;
import com.equitransit.backend.service.ComplianceService;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Real MVC, filter chain, method security, F08 service and binary storage.
 */
@SpringBootTest(properties = "be2.compliance.api-enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReviewF08StaffFileAuthorizationTest {

    private static final String HORSE_URL =
            "/api/horses/9/documents/versions/11/file";
    private static final String CASE_URL =
            "/api/transport-orders/20/compliance/requirements/100"
                    + "/versions/11/file";
    private static final byte[] BYTES =
            "%PDF-1.5 private horse 9 passport"
                    .getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private ComplianceService service;

    @MockitoBean
    private CurrentActorService actors;

    @MockitoBean
    private CaseAssignmentAccess access;

    @MockitoBean
    private HorseDocumentRepository documents;

    @MockitoBean
    private DocumentVersionRepository versions;

    @MockitoBean
    private DocumentRequirementRepository requirements;

    @MockitoBean
    private DocumentSubmissionRepository submissions;

    /** Setup test mocks and sample document. */
    @BeforeEach
    void setup() {
        when(actors.current()).thenReturn(
                new CurrentActor(1L, "CUSTOMER", 4L, null));
        var doc = new HorseDocument();
        doc.setDocumentId(10L);
        doc.setHorseId(9L);
        doc.setDocumentType("PASSPORT");
        when(documents.findById(10L)).thenReturn(Optional.of(doc));
        var stored = service.storeDocumentFile(9L, "private-passport.pdf",
                "application/pdf", BYTES);
        var version = new DocumentVersion();
        version.setDocumentVersionId(11L);
        version.setDocumentId(10L);
        version.setFileKey(stored.fileKey());
        version.setFileHash(stored.fileHash());
        when(versions.findById(11L)).thenReturn(Optional.of(version));
    }

    /** Set current actor to staff role. */
    private void staff(String role) {
        when(actors.current()).thenReturn(
                new CurrentActor(2L, role, null, 2L));
    }

    /** Mock document requirement. */
    private DocumentRequirement requirement(long horse, String type) {
        var req = new DocumentRequirement();
        req.setRequirementId(100L);
        req.setRevisionId(30L);
        req.setScopeCode("HORSE");
        req.setHorseId(horse);
        req.setDocumentType(type);
        when(requirements.findById(100L)).thenReturn(Optional.of(req));
        return req;
    }

    /** Owning customer can still read own file. */
    @Test
    void owningCustomerCanStillReadOwnFile() throws Exception {
        mvc.perform(get(HORSE_URL).with(jwt().authorities(
                new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(content().bytes(BYTES));
    }

    /** Assigned specialist can read version submitted to requirement. */
    @Test
    void assignedSpecialistCanReadVersionSubmittedToRequirement()
            throws Exception {
        staff("SPECIALIST");
        when(access.lockRevision(20L, CaseRole.SPECIALIST)).thenReturn(30L);
        requirement(9L, "PASSPORT");
        var submission = new DocumentSubmission();
        submission.setSubmissionId(101L);
        submission.setRequirementId(100L);
        submission.setDocumentVersionId(11L);
        when(submissions.latestId(100L)).thenReturn(101L);
        when(submissions.findById(101L)).thenReturn(Optional.of(submission));
        mvc.perform(get(CASE_URL).with(jwt().authorities(
                new SimpleGrantedAuthority("ROLE_SPECIALIST"))))
                .andExpect(status().isOk())
                .andExpect(content().bytes(BYTES));
    }

    /** Staff without any assignment must not read arbitrary horse file. */
    @ParameterizedTest
    @ValueSource(strings = {"SPECIALIST", "COORDINATOR"})
    void staffWithoutAnyAssignmentMustNotReadArbitraryHorseFile(String role)
            throws Exception {
        staff(role);
        when(access.lockRevision(anyLong(), any(CaseRole.class)))
                .thenThrow(new OrderException(HttpStatus.NOT_FOUND,
                        "CASE_NOT_FOUND", "No case assignment"));
        mvc.perform(get(HORSE_URL).with(jwt().authorities(
                new SimpleGrantedAuthority("ROLE_" + role))))
                .andExpect(status().is4xxClientError());
    }

    /** Assigned requirement must not authorize foreign unsubmitted version. */
    @Test
    void assignedRequirementMustNotAuthorizeForeignUnsubmittedVersion()
            throws Exception {
        staff("SPECIALIST");
        when(access.lockRevision(20L, CaseRole.SPECIALIST)).thenReturn(30L);
        requirement(77L, "HEALTH_CERTIFICATE");
        // No submission exists. Version 11 is a PASSPORT for horse 9.
        when(submissions.latestId(100L)).thenReturn(0L);
        mvc.perform(get(CASE_URL).with(jwt().authorities(
                new SimpleGrantedAuthority("ROLE_SPECIALIST"))))
                .andExpect(status().is4xxClientError());
    }
}
