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
 * Truong THPT (bang high_schools) - dau vao cua luong tra cuu
 * nhom mon hoc theo truong. Dung @Getter/@Setter thay cho
 * @Data/@EqualsAndHashCode theo rule code-quality cho JPA Entity.
 */
@Entity
@Table(name = "high_schools")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HighSchool {

    @Id
    @Column(name = "code", length = 30, nullable = false)
    private String code;

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "location", length = 255)
    private String location;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
