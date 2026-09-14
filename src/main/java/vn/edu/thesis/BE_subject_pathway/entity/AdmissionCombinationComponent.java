package vn.edu.thesis.BE_subject_pathway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Thanh phan cua mot to hop xet tuyen (bang admission_combination_components).
 * Composite key (combination_id, component_code) map qua @EmbeddedId;
 * combination_id dong thoi la FK den admission_combinations (@MapsId).
 * subject_code co the NULL voi thanh phan nang khieu (component_type = APTITUDE).
 */
@Entity
@Table(name = "admission_combination_components")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionCombinationComponent {

    @EmbeddedId
    private AdmissionCombinationComponentId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("combinationId")
    @JoinColumn(name = "combination_id", referencedColumnName = "combination_id")
    private AdmissionCombination combination;

    @Column(name = "component_name", length = 150, nullable = false)
    private String componentName;

    @Column(name = "component_type", length = 20, nullable = false)
    private String componentType;

    @Column(name = "subject_code", length = 30)
    private String subjectCode;

    @Column(name = "weight", precision = 6, scale = 3, nullable = false)
    private BigDecimal weight;
}
