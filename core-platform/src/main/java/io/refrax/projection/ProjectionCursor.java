package io.refrax.projection;

import io.quarkus.hibernate.reactive.panache.PanacheEntityBase;
import io.smallrye.mutiny.Uni;
import jakarta.persistence.*;

@Entity
@Table(name = "projection_cursor")
public class ProjectionCursor extends PanacheEntityBase {
    @Id
    @Column(name = "projection", nullable = false, columnDefinition = "text")
    private String projection;

    @Column(name = "position", nullable = false)
    private Long position;

    public ProjectionCursor() {}

    public static Uni<ProjectionCursor> findByProjection(String projection) {
        return find("projection", projection).firstResult();
    }

    public String getProjection() {
        return projection;
    }

    public void setProjection(String projection) {
        this.projection = projection;
    }

    public Long getPosition() {
        return position;
    }

    public void setPosition(Long position) {
        this.position = position;
    }
}
