package vn.edu.thesis.BE_subject_pathway.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.projection.SpelAwareProxyProjectionFactory;
import vn.edu.thesis.BE_subject_pathway.entity.Subject;
import vn.edu.thesis.BE_subject_pathway.exception.ResourceNotFoundException;
import vn.edu.thesis.BE_subject_pathway.repository.AdmissionCombinationRepository;
import vn.edu.thesis.BE_subject_pathway.repository.SubjectRepository;
import vn.edu.thesis.BE_subject_pathway.repository.projection.CombinationCatalogueLabelProjection;
import vn.edu.thesis.BE_subject_pathway.repository.projection.CombinationComponentProjection;
import vn.edu.thesis.BE_subject_pathway.repository.projection.CombinationProjection;

class AdmissionCombinationLabelServiceTest {
    private final AdmissionCombinationRepository repository = mock(AdmissionCombinationRepository.class);
    private final SubjectRepository subjects = mock(SubjectRepository.class);
    private final AdmissionCombinationLabelService service =
            new AdmissionCombinationLabelService(repository, subjects);

    @BeforeEach
    void setUpSubjects() {
        when(subjects.findAll()).thenReturn(List.of(
                subject("MATH", null), subject("MATH_ALIAS", "MATH"),
                subject("PHYSICS", null), subject("ENGLISH", null)));
    }

    @Test
    void returnsAllLatestLabelsAndPreservesUnverifiedCanonicalMappings() {
        CombinationCatalogueLabelProjection verified = label("A01", "Toán, Vật lí, Tiếng Anh", "A01");
        CombinationCatalogueLabelProjection unverified = label("A01", "Toán, Tiếng Anh, Tin học", null);
        when(repository.findLatestLabelsByCode("A01")).thenReturn(List.of(verified, unverified));

        var response = service.findLatestLabels(" a01 ");

        assertEquals("A01", response.requestedCode());
        assertEquals(2, response.labels().size());
        assertEquals("A01", response.labels().get(0).canonicalCombinationId());
        assertNull(response.labels().get(1).canonicalCombinationId());
    }

    @Test
    void rejectsMalformedCodeBeforeDatabaseQuery() {
        assertThrows(IllegalArgumentException.class, () -> service.findLatestLabels("A01/%"));
        verify(repository, never()).findLatestLabelsByCode("A01/%");
    }

    @Test
    void reportsUnknownCodeAsNotFound() {
        when(repository.findLatestLabelsByCode("Z99")).thenReturn(List.of());

        assertThrows(ResourceNotFoundException.class, () -> service.findLatestLabels("Z99"));
    }

    @Test
    void convertsNativeInstantProjectionToUtcResponse() {
        var projection = new SpelAwareProxyProjectionFactory().createProjection(
                CombinationCatalogueLabelProjection.class,
                Map.of("code", "A00", "subjectDetails", "Math, Physics, Chemistry",
                        "canonicalCombinationId", "MATH_PHYSICS_CHEMISTRY",
                        "sourceUrl", "https://example.test/source", "sourcePeriod", "2026",
                        "retrievedAt", Instant.parse("2026-10-19T03:30:00Z")));
        when(repository.findLatestLabelsByCode("A00")).thenReturn(List.of(projection));

        var response = service.findLatestLabels("A00");

        assertEquals(OffsetDateTime.parse("2026-10-19T03:30:00Z"),
                response.labels().get(0).retrievedAt());
    }

    @Test
    void resolvesExactSubjectSetAcrossAliasesAndReturnsCatalogueProvenance() {
        CombinationProjection candidate = mock(CombinationProjection.class);
        when(candidate.getCombinationId()).thenReturn("MATH_PHYSICS_ENGLISH");
        when(candidate.getCombinationDisplay()).thenReturn("Toan, Vat li, Tieng Anh");
        when(candidate.getComponentCount()).thenReturn((short) 3);
        when(repository.findExactSubjectCombinations(
                List.of("ENGLISH", "MATH", "MATH_ALIAS", "PHYSICS"), 3)).thenReturn(List.of(candidate));
        List<CombinationComponentProjection> exactComponents = List.of(
                component("MATH", "MATH"), component("PHYSICS", "PHYSICS"), component("ENGLISH", "ENGLISH"));
        when(repository.findComponentsByCombinationIds(List.of("MATH_PHYSICS_ENGLISH")))
                .thenReturn(exactComponents);
        CombinationCatalogueLabelProjection catalogueLabel = labelWithProvenance();
        when(repository.findLatestCatalogueLabelsByCombinationIds(List.of("MATH_PHYSICS_ENGLISH")))
                .thenReturn(List.of(catalogueLabel));

        var response = service.resolveExactSubjectSet(List.of("MATH_ALIAS", "PHYSICS", "ENGLISH"));

        assertEquals(1, response.matches().size());
        assertEquals("MATH_PHYSICS_ENGLISH", response.matches().get(0).combinationId());
        assertEquals("X26", response.matches().get(0).labels().get(0).code());
        assertEquals("https://example.test/source", response.matches().get(0).labels().get(0).sourceUrl());
        verify(repository).findExactSubjectCombinations(
                List.of("ENGLISH", "MATH", "MATH_ALIAS", "PHYSICS"), 3);
    }

    @Test
    void rejectsDuplicateCanonicalSubjectsBeforeQueryingCombinations() {
        assertThrows(IllegalArgumentException.class,
                () -> service.resolveExactSubjectSet(List.of("MATH", "MATH_ALIAS", "PHYSICS")));
        verify(repository, never()).findExactSubjectCombinations(org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void doesNotResolveACombinationContainingAnAptitudeComponentAsSubjectOnly() {
        CombinationProjection candidate = mock(CombinationProjection.class);
        when(candidate.getCombinationId()).thenReturn("SPECIAL");
        when(candidate.getCombinationDisplay()).thenReturn("Math and aptitude");
        when(candidate.getComponentCount()).thenReturn((short) 3);
        when(repository.findExactSubjectCombinations(org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.eq(3)))
                .thenReturn(List.of(candidate));
        List<CombinationComponentProjection> components = List.of(
                component("MATH", "MATH"), component("PHYSICS", "PHYSICS"), aptitudeComponent("DRAWING"));
        when(repository.findComponentsByCombinationIds(List.of("SPECIAL"))).thenReturn(components);

        var response = service.resolveExactSubjectSet(List.of("MATH", "PHYSICS", "ENGLISH"));

        assertEquals(List.of(), response.matches());
        verify(repository, never()).findLatestCatalogueLabelsByCombinationIds(org.mockito.ArgumentMatchers.anyList());
    }

    private static CombinationCatalogueLabelProjection label(String code, String details, String canonical) {
        CombinationCatalogueLabelProjection projection = mock(CombinationCatalogueLabelProjection.class);
        when(projection.getCode()).thenReturn(code);
        when(projection.getSubjectDetails()).thenReturn(details);
        when(projection.getCanonicalCombinationId()).thenReturn(canonical);
        return projection;
    }

    private static CombinationCatalogueLabelProjection labelWithProvenance() {
        CombinationCatalogueLabelProjection projection = label("X26", "Toan, Ly, Anh", "MATH_PHYSICS_ENGLISH");
        when(projection.getSourceUrl()).thenReturn("https://example.test/source");
        when(projection.getSourcePeriod()).thenReturn("2026");
        when(projection.getRetrievedAt()).thenReturn(Instant.parse("2026-10-19T00:00:00Z"));
        return projection;
    }

    private static Subject subject(String code, String canonical) {
        return Subject.builder().code(code).name(code).canonicalSubjectCode(canonical)
                .subjectType("ACADEMIC").isMandatory(false).build();
    }

    private static CombinationComponentProjection component(String code, String subjectCode) {
        CombinationComponentProjection row = mock(CombinationComponentProjection.class);
        when(row.getCombinationId()).thenReturn("MATH_PHYSICS_ENGLISH");
        when(row.getComponentType()).thenReturn("SUBJECT");
        when(row.getComponentCode()).thenReturn(code);
        when(row.getComponentName()).thenReturn(code);
        when(row.getSubjectCode()).thenReturn(subjectCode);
        return row;
    }

    private static CombinationComponentProjection aptitudeComponent(String code) {
        CombinationComponentProjection row = mock(CombinationComponentProjection.class);
        when(row.getCombinationId()).thenReturn("SPECIAL");
        when(row.getComponentType()).thenReturn("APTITUDE");
        when(row.getComponentCode()).thenReturn(code);
        when(row.getComponentName()).thenReturn("Drawing");
        when(row.getSubjectCode()).thenReturn(null);
        return row;
    }
}
