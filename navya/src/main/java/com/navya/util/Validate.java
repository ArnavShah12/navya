package com.navya.util;

import com.navya.exception.InvalidInputException;
import java.util.regex.Pattern;

/** Input checks used at every trust boundary. All failures raise InvalidInputException. */
public final class Validate {
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private Validate() {}

    public static String email(String v) {
        String s = required(v, "email").trim().toLowerCase();
        if (s.length() > 254 || !EMAIL.matcher(s).matches()) throw new InvalidInputException("Email address is not valid");
        return s;
    }

    /** Password rule: 8 to 64 characters. 64 keeps well under BCrypt's 72-byte limit. */
    public static String password(String v) {
        if (v == null || v.length() < 8 || v.length() > 64) throw new InvalidInputException("Password must be 8 to 64 characters");
        return v;
    }

    public static String text(String v, String field, int min, int max) {
        String s = required(v, field).trim();
        if (s.length() < min || s.length() > max) throw new InvalidInputException(field + " must be " + min + " to " + max + " characters");
        return s;
    }

    public static String optionalText(String v, String field, int max) {
        if (v == null) return "";
        String s = v.trim();
        if (s.length() > max) throw new InvalidInputException(field + " must be at most " + max + " characters");
        return s;
    }

    public static int intRange(int v, String field, int min, int max) {
        if (v < min || v > max) throw new InvalidInputException(field + " must be between " + min + " and " + max);
        return v;
    }

    public static long nonNegative(long v, String field) {
        if (v < 0) throw new InvalidInputException(field + " must not be negative");
        return v;
    }

    public static <T> T required(T v, String field) {
        if (v == null || (v instanceof String s && s.isBlank())) throw new InvalidInputException(field + " is required");
        return v;
    }
}
