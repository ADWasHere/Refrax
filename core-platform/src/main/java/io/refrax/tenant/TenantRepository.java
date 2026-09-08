package io.refrax.tenant;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.List;

@ApplicationScoped
public class TenantRepository {

    public Uni<List<String>> findAllTenantSchemas() {
        String sql = """
            SELECT DISTINCT table_schema
            FROM information_schema.tables
            WHERE table_name = 'projection_cursor'
              AND table_schema != 'public'
           \s""";

        return Panache.withSession(() -> Panache.getSession()
                .flatMap(session -> session.createNativeQuery(sql, String.class).getResultList()));
    }
}