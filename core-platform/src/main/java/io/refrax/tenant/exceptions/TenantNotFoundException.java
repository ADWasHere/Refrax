package io.refrax.tenant.exceptions;

public class TenantNotFoundException extends RuntimeException {
    public TenantNotFoundException(String tenantId) {
        super("Tenant schema '" + tenantId + "' does not exist");
    }
}