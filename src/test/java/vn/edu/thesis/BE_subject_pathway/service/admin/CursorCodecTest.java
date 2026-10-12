package vn.edu.thesis.BE_subject_pathway.service.admin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class CursorCodecTest {
    private final CursorCodec cursors = new CursorCodec("01234567890123456789012345678901");

    @Test
    void signsAndVerifiesCursor() {
        String value = "2026-10-12T10:00:00Z|00000000-0000-0000-0000-000000000001";
        assertEquals(value, cursors.decode(cursors.encode(value)));
    }

    @Test
    void rejectsTamperedCursor() {
        String cursor = cursors.encode("record-id");
        String changed = (cursor.startsWith("a") ? "b" : "a") + cursor.substring(1);
        assertThrows(IllegalArgumentException.class, () -> cursors.decode(changed));
    }
}
