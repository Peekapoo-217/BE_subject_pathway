package vn.edu.thesis.BE_subject_pathway.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Mot offer xet tuyen cua truong cho nganh theo nam
 */
@Entity
@Table(name = "admission_offers")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionOffer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false)
    private Long id;

    @Column(name = "university_code", length = 20, nullable = false)
    private String universityCode;

    @Column(name = "admission_year", nullable = false)
    private Short admissionYear;

    @Column(name = "program_code", length = 30, nullable = false)
    private String programCode;

    @Column(name = "method_codes", nullable = false)
    private String methodCodes;

    @Column(name = "quota")
    private Integer quota;

    @Column(name = "source_url")
    private String sourceUrl;

    @Column(name = "retrieved_at")
    private OffsetDateTime retrievedAt;

    @Column(name = "loaded_at", nullable = false, updatable = false)
    private OffsetDateTime loadedAt;
}
