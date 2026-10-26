package com.equitransit.backend;

import com.equitransit.backend.dto.route.AddressValidationResponse;
import com.equitransit.backend.exception.order.OrderException;
import com.equitransit.backend.service.AddressGeocodingService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet
        .AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request
        .MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result
        .MockMvcResultMatchers.status;

/** Public API contracts through the actual security/validation stack. */
@SpringBootTest
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@ActiveProfiles("test")
public class AddressHttpTest {
    @Autowired private MockMvc mvc;
    @MockitoBean private AddressGeocodingService service;

    /** @throws Exception if search is not public or query bounds drift */
    @Test
    public void publicSearchDefaultsAndBounds() throws Exception {
        when(service.search("asdfgh", "VN", 5)).thenReturn(List.of());
        mvc.perform(get("/api/routes/address/search")
                .param("query", "asdfgh").param("countryCode", "VN"))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        verify(service).search("asdfgh", "VN", 5);
        mvc.perform(get("/api/routes/address/search").param("query", "a"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/routes/address/search").param("query", "text")
                .param("limit", "11")).andExpect(status().isBadRequest());
        mvc.perform(get("/api/routes/address/search").param("query", "text")
                .param("countryCode", "VNN"))
                .andExpect(status().isBadRequest());
    }

    /** @throws Exception if outage is confused with invalid input */
    @Test
    public void publicValidationFalseAndProviderFailures() throws Exception {
        when(service.validate("asdfgh", "VN")).thenReturn(
                new AddressValidationResponse(false, null, "Not found."));
        mvc.perform(post("/api/routes/address/validate")
                .contentType("application/json")
                .content("{\"address\":\"asdfgh\",\"countryCode\":\"VN\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isValid").value(false));
        mvc.perform(post("/api/routes/address/validate")
                .contentType("application/json").content("{\"address\":\"\"}"))
                .andExpect(status().isBadRequest());
        when(service.search("unavailable", "VN", 5)).thenThrow(
                new OrderException(HttpStatus.SERVICE_UNAVAILABLE,
                        "GEOCODING_UNAVAILABLE", "Unavailable."));
        mvc.perform(get("/api/routes/address/search")
                .param("query", "unavailable").param("countryCode", "VN"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.code").value("GEOCODING_UNAVAILABLE"));
    }
}
