package vn.edu.thesis.BE_subject_pathway.service.admin;

import java.text.Normalizer;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class SchoolCodeGenerator {
    private static final Pattern THPT_TOKEN = Pattern.compile("(?iu)\\bTHPT\\b");
    private static final Pattern WORDS = Pattern.compile("[\\p{L}\\p{N}]+");

    private SchoolCodeGenerator() {}

    public static String suggest(String schoolName) {
        if (schoolName == null || schoolName.isBlank()) {
            throw new IllegalArgumentException("Tên trường không được để trống");
        }
        String normalized = Normalizer.normalize(schoolName, Normalizer.Form.NFC);
        Matcher prefix = THPT_TOKEN.matcher(normalized);
        if (!prefix.find()) {
            throw new IllegalArgumentException("Tên trường cần có cụm THPT để tạo mã tự động");
        }

        String suffix = normalized.substring(prefix.end());
        Matcher words = WORDS.matcher(suffix);
        StringBuilder code = new StringBuilder();
        while (words.find()) {
            int firstCodePoint = words.group().codePointAt(0);
            code.appendCodePoint(Character.toUpperCase(firstCodePoint));
        }
        if (code.isEmpty() || code.length() > 30) {
            throw new IllegalArgumentException("Không thể tạo mã trường hợp lệ từ tên đã nhập");
        }
        return code.toString().toUpperCase(Locale.forLanguageTag("vi-VN"));
    }

    public static String validateCustomCode(String code) {
        if (code == null || code.isBlank()) {
            throw new IllegalArgumentException("Vui lòng nhập mã trường");
        }
        String normalized = Normalizer.normalize(code.trim(), Normalizer.Form.NFC)
                .toUpperCase(Locale.forLanguageTag("vi-VN"));
        if (normalized.length() > 30 || !normalized.matches("[\\p{L}\\p{N}_-]+")) {
            throw new IllegalArgumentException("Mã trường chỉ gồm chữ cái, chữ số, dấu gạch dưới hoặc gạch ngang và tối đa 30 ký tự");
        }
        return normalized;
    }
}
