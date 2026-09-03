package io.refrax.readmodel;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.Objects;

@Embeddable
public class ReadingSeriesId implements Serializable {

    @Column(name = "event_type", nullable = false, columnDefinition = "text")
    private String eventType;

    @Column(name = "seq", nullable = false)
    private Long seq;

    @Column(name = "observed_at", nullable = false)
    private OffsetDateTime observedAt;

    public ReadingSeriesId() {}

    public ReadingSeriesId(String eventType, Long seq, OffsetDateTime observedAt) {
        this.eventType = eventType;
        this.seq = seq;
        this.observedAt = observedAt;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }

    public Long getSeq() {
        return seq;
    }

    public void setSeq(Long seq) {
        this.seq = seq;
    }

    public OffsetDateTime getObservedAt() {
        return observedAt;
    }

    public void setObservedAt(OffsetDateTime observedAt) {
        this.observedAt = observedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ReadingSeriesId that)) return false;
        return Objects.equals(eventType, that.eventType)
                && Objects.equals(seq, that.seq)
                && Objects.equals(observedAt, that.observedAt);
    }

    @Override
    public int hashCode() {
        return Objects.hash(eventType, seq, observedAt);
    }
}
