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
 * Composite key (offer_id, canonical_combination_id) cho bang
 * offer_combinations - bang trung gian giua offer va to hop.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class OfferCombinationId implements Serializable {

    @Column(name = "offer_id")
    private Long offerId;

    @Column(name = "canonical_combination_id", length = 255)
    private String canonicalCombinationId;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof OfferCombinationId other)) {
            return false;
        }
        return Objects.equals(offerId, other.offerId)
                && Objects.equals(canonicalCombinationId, other.canonicalCombinationId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(offerId, canonicalCombinationId);
    }
}
