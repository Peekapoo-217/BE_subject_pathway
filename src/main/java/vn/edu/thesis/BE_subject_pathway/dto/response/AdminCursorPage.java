package vn.edu.thesis.BE_subject_pathway.dto.response;

import java.util.List;

public record AdminCursorPage<T>(List<T> items, boolean hasNext, String nextCursor) {}
