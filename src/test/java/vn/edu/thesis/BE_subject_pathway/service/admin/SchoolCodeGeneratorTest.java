package vn.edu.thesis.BE_subject_pathway.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class SchoolCodeGeneratorTest {
    @Test
    void takesUnicodeInitialsAfterThptAndKeepsVietnameseD() {
        assertEquals("TĐT", SchoolCodeGenerator.suggest("Trường THPT Tôn Đức Thắng"));
    }

    @Test
    void requiresThptTokenAndValidCustomCodeCharacters() {
        assertThrows(IllegalArgumentException.class, () -> SchoolCodeGenerator.suggest("Trường Tôn Đức Thắng"));
        assertThrows(IllegalArgumentException.class, () -> SchoolCodeGenerator.validateCustomCode("TĐT 2026"));
        assertEquals("TĐT-1", SchoolCodeGenerator.validateCustomCode("tđt-1"));
    }
}
