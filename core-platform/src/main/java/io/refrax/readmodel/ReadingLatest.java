package io.refrax.readmodel;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import jakarta.persistence.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "reading_latest")
public class ReadingLatest extends PanacheEntityBase {

    @EmbeddedId
    private ReadingLatestId id;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "exposed_json", nullable = false, columnDefinition = "jsonb")
    private String exposedJson;

    @Column(name = "observed_at")
    private OffsetDateTime observedAt;

    @Column(name = "seq", nullable = false)
    private Long seq;

    public ReadingLatest() {}

    public ReadingLatestId getId() {
        return id;
    }

    public void setId(ReadingLatestId id) {
        this.id = id;
    }

    public String getExposedJson() {
        return exposedJson;
    }

    public void setExposedJson(String exposedJson) {
        this.exposedJson = exposedJson;
    }

    public OffsetDateTime getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(OffsetDateTime observedAt) {
        this.observedAt = observedAt;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }
}
