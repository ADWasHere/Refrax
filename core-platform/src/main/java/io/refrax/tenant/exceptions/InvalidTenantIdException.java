package io.refrax.tenant.exceptions;

public class InvalidTenantIdException extends RuntimeException {
    public InvalidTenantIdException(String tenantId) {
        super("Tenant id is not a valid schema identifier: " + tenantId);
    }
}
