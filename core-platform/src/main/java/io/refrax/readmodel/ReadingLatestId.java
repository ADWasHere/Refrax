package io.refrax.readmodel;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.Objects;

@Embeddable
public class ReadingLatestId implements Serializable {

    @Column(name = "event_type", nullable = false, columnDefinition = "text")
    private String eventType;

    @Column(name = "entity_id", nullable = false, columnDefinition = "text")
    private String entityId;

    public ReadingLatestId() {}

    public ReadingLatestId(String eventType, String entityId) {
        this.eventType = eventType;
        this.entityId = entityId;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadingLatestId that)) return false;
        return Objects.equals(eventType, that.eventType) && Objects.equals(entityId, that.entityId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventType, entityId);
    }
}
