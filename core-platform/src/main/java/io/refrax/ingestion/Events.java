package io.refrax.ingestion;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.Generated;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;
import java.util.UUID;

@Entity
@Table(
        name = "events",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uq_events_event_id",
                        columnNames = {"event_id"}
                )
        }
)
public class Events extends PanacheEntityBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "seq", nullable = false, updatable = false)
    public Long seq;

    @Column(name = "event_type", nullable = false, columnDefinition = "text")
    public String eventType;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", nullable = false, columnDefinition = "jsonb")
    public String payload;

    @Column(name = "valid_time")
    public OffsetDateTime validTime;

    @Column(name = "event_id", nullable = false)
    public UUID eventId;

    @Column(name = "schema_version", nullable = false, columnDefinition = "text")
    public String schemaVersion;

    @Generated
    @ColumnDefault("now()")
    @Column(name = "recorded_at", nullable = false, insertable = false, updatable = false)
    public OffsetDateTime recordedAt;

    public Events() {
    }

    public static Uni<Events> findByEventId(UUID eventId) {
        return find("eventId = ?1", eventId).firstResult();
    }

    public Long getSeq() {
        return seq;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getPayload() {
        return payload;
    }

    public void setPayload(String payload) {
        this.payload = payload;
    }

    public OffsetDateTime getValidTime() {
        return validTime;
    }

    public void setValidTime(OffsetDateTime validTime) {
        this.validTime = validTime;
    }

    public UUID getEventId() {
        return eventId;
    }

    public void setEventId(UUID eventId) {
        this.eventId = eventId;
    }

    public String getSchemaVersion() {
        return schemaVersion;
    }

    public void setSchemaVersion(String schemaVersion) {
        this.schemaVersion = schemaVersion;
    }

    public OffsetDateTime getRecordedAt() {
        return recordedAt;
    }
}