package vn.edu.thesis.BE_subject_pathway.dto.response;

public record SchoolCodeSuggestionDto(
        String suggestedCode,
        boolean available,
        boolean canReuseExistingSchool,
        String existingSchoolCode,
        String existingSchoolName,
        String existingSchoolLocation) {}
