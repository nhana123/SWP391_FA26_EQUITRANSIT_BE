/*
 * ComplianceHttpContractTest
 * Author: EquiTransit development team
 * Date: 2026-10-17
 * Copyright: EquiTransit project contributors.
 * Purpose: Verify HTTP contract, security routes, and multipart size limits.
 */
package com.equitransit.backend;

import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.web.servlet.MultipartProperties;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import com.equitransit.backend.dto.response.AuthorityProcedurePageResponse;
import com.equitransit.backend.service.ComplianceService;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Actual MVC, security, and configuration with mock service payload. */
@SpringBootTest(properties = "be2.compliance.api-enabled=true")
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ComplianceHttpContractTest {

    private static final String URL =
            "/api/horses/9/documents/versions/11/file";
    private static final String CASE_URL =
            "/api/transport-orders/20/compliance/requirements/100"
                    + "/versions/11/file";
    private static final byte[] BYTES =
            "%PDF-1.5 http control".getBytes(StandardCharsets.UTF_8);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private MultipartProperties multipart;

    @MockitoBean
    private ComplianceService service;

    @BeforeEach
    void setup() {
        when(service.getDocumentVersionFile(9L, 11L)).thenReturn(BYTES);
        when(service.getOrderDocumentVersionFile(20L, 100L, 11L))
                .thenReturn(BYTES);
    }

    /** Owner with wildcard Accept header can download binary. */
    @Test
    void ownerWithWildcardAcceptCanDownloadBinary() throws Exception {
        mvc.perform(get(URL).with(jwt().authorities(
                new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(content().bytes(BYTES));
    }

    /** Consumer can download binary requesting octet-stream Accept. */
    @Test
    void binaryConsumerMustBeAbleToRequestOctetStream() throws Exception {
        mvc.perform(get(URL).accept(MediaType.APPLICATION_OCTET_STREAM)
                .with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_CUSTOMER"))))
                .andExpect(status().isOk())
                .andExpect(content().bytes(BYTES));
    }

    /** Specialist review must have an authorized file read route. */
    @Test
    void specialistReviewMustHaveAFileReadRoute() throws Exception {
        mvc.perform(get(CASE_URL).with(jwt().authorities(
                new SimpleGrantedAuthority("ROLE_SPECIALIST"))))
                .andExpect(status().isOk())
                .andExpect(content().bytes(BYTES));
    }

    /** Bound servlet limit must permit at least 10 MiB uploads. */
    @Test
    void boundServletLimitMustAllowPromisedTenMiBUpload() {
        assertThat(multipart.getMaxFileSize().toBytes())
                .isGreaterThanOrEqualTo(10L * 1024 * 1024);
    }

    /** Specialist can list paginated authority procedures.
     * @throws Exception when MVC perform fails
     */
    @Test
    void specialistCanListPaginatedAuthorityProcedures() throws Exception {
        when(service.listProceduresPaged(20L, 0, 10))
                .thenReturn(new AuthorityProcedurePageResponse(
                        List.of(), 0, 10, 0L, 0));
        mvc.perform(get(
                "/api/staff/transport-orders/20/compliance/procedures")
                .param("page", "0")
                .param("size", "10")
                .with(jwt().authorities(
                        new SimpleGrantedAuthority("ROLE_SPECIALIST"))))
                .andExpect(status().isOk());
    }
}
