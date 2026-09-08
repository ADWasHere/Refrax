package io.refrax.tenant.controller;

import jakarta.validation.constraints.NotBlank;

public record CreateTenantRequest(
        @NotBlank(message = "schema darf nicht leer sein") String schema
) {
}
