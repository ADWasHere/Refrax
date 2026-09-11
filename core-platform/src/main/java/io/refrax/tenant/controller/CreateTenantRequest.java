package io.refrax.tenant.controller;

import jakarta.validation.constraints.NotBlank;

public record CreateTenantRequest(
        @NotBlank(message = "Schema is required") String schema
) {
}
