package vn.edu.thesis.BE_subject_pathway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * To hop xet tuyen (VD: A00, D01...).
 */
@Entity
@Table(name = "admission_combinations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionCombination {

    @Id
    @Column(name = "combination_id", length = 255, nullable = false)
    private String combinationId;

    @Column(name = "combination_display", length = 255, nullable = false)
    private String combinationDisplay;

    @Column(name = "component_count", nullable = false)
    private Short componentCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
