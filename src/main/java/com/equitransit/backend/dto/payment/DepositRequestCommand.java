package com.equitransit.backend.dto.payment;

import com.equitransit.backend.dto.ApiPatterns;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/** Manager-issued amounts, never evidence that money has been received.
 * @param totalAmount contract total @param depositAmount requested deposit
 * @param currency USD default @param note customer-visible message
 * @param recordVersion last observed order version */
public record DepositRequestCommand(
        @Schema(example = "1000.00")
        @NotNull @Positive @Digits(integer = 16, fraction = 2)
        BigDecimal totalAmount,
        @Schema(example = "300.00")
        @NotNull @Positive @Digits(integer = 16, fraction = 2)
        BigDecimal depositAmount,
        @Schema(example = "USD", defaultValue = "USD")
        @Pattern(regexp = "(?i)USD") String currency,
        @Schema(example = "Please pay the deposit.", nullable = true)
        @Size(max = 1000) String note,
        @Schema(example = "000000000001A2B3", pattern = ApiPatterns.VERSION)
        @NotBlank @Pattern(regexp = ApiPatterns.VERSION) String recordVersion) {
}
