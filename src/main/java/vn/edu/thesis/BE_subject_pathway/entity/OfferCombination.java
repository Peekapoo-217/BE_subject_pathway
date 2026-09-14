package vn.edu.thesis.BE_subject_pathway.entity;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MapsId;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 */
@Entity
@Table(name = "offer_combinations")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OfferCombination {

    @EmbeddedId
    private OfferCombinationId id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @MapsId("offerId")
    @JoinColumn(name = "offer_id", referencedColumnName = "id")
    private AdmissionOffer offer;
}
