package io.refrax.readmodel;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reading_series")
public class ReadingSeries extends PanacheEntityBase {

    @EmbeddedId
    private ReadingSeriesId id;

    @Column(name = "entity_id", nullable = false, columnDefinition = "text")
    private String entityId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exposed_json", nullable = false, columnDefinition = "jsonb")
    private String exposedJson;

    public ReadingSeries() {}

    public ReadingSeriesId getId() {
        return id;
    }

    public void setId(ReadingSeriesId id) {
        this.id = id;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getExposedJson() {
        return exposedJson;
    }

    public void setExposedJson(String exposedJson) {
        this.exposedJson = exposedJson;
    }
}
