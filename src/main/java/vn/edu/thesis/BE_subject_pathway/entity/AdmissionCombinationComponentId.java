package vn.edu.thesis.BE_subject_pathway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Composite key (combination_id, component_code) cho bang
 * admission_combination_components. Bat buoc Serializable +
 * equals/hashCode (rule jpa-database-performance).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionCombinationComponentId implements Serializable {

    @Column(name = "combination_id", length = 255)
    private String combinationId;

    @Column(name = "component_code", length = 100)
    private String componentCode;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof AdmissionCombinationComponentId other)) {
            return false;
        }
        return Objects.equals(combinationId, other.combinationId)
                && Objects.equals(componentCode, other.componentCode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(combinationId, componentCode);
    }
}
