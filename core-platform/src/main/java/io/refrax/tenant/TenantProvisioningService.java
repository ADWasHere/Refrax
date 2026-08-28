package io.refrax.tenant;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.flywaydb.core.Flyway;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.Statement;

/**
 * Service for provisioning a new tenant schema and running migrations on it.
 * <br/><br/>
 * <b>Important:</b> It uses an blocking JDBC connection because Flyway does not support reactive clients.
 * This is acceptable for provisioning, which is a rare operation.
 */
@ApplicationScoped
public class TenantProvisioningService {

    @Inject
    DataSource defaultDataSource;

    public void provisionTenant(String tenantSchemaName) throws Exception {
        try (Connection conn = defaultDataSource.getConnection()) {
            DatabaseMetaData meta = conn.getMetaData();
            String quote = meta.getIdentifierQuoteString();
            if (quote == null) {
                quote = "";
            }
            String quotedSchema = quote + tenantSchemaName + quote;

            try (Statement stmt = conn.createStatement()) {
                stmt.execute("CREATE SCHEMA IF NOT EXISTS " + quotedSchema);
            }
        }

        Flyway flyway = Flyway.configure()
                .dataSource(defaultDataSource)
                .schemas(tenantSchemaName)
                .locations("db/migration")
                .load();

        flyway.migrate();
    }
}
