
package com.project.wallet_service.account.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateAccountRequest(

        @NotBlank(message = "Currency is required")
        @Pattern(
                regexp = "[A-Z]{3}",
                message = "Currency must be a 3-letter uppercase code"
        )
        String currency

) {
}
