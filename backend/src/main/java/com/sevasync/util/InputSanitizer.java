package com.sevasync.util;

import com.sevasync.exception.InvalidInputException;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Central place for cleaning and validating every piece of text that enters the system. */
public final class InputSanitizer {
    private static final Pattern PHONE = Pattern.compile("^[6-9]\\d{9}$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

    private InputSanitizer() {
    }

    /** Free text: strips control chars, angle brackets and CSV-formula prefixes, collapses whitespace. */
    public static String text(String raw, String field, int maxLen, boolean required) throws InvalidInputException {
        String s = raw == null ? "" : raw;
        s = s.replaceAll("\\p{Cntrl}", " ").replaceAll("<[^>]*>", "").replaceAll("[<>]", "").replaceAll("\\s+", " ").trim();
        s = s.replaceFirst("^[=+\\-@]+", "").trim();
        if (required && s.isEmpty()) {
            throw new InvalidInputException(field + " is required");
        }
        if (s.length() > maxLen) {
            throw new InvalidInputException(field + " must be at most " + maxLen + " characters");
        }
        return s;
    }

    /** Indian mobile number; accepts +91 / 91 / 0 prefixes, spaces and dashes. */
    public static String phone(String raw, String field) throws InvalidInputException {
        String d = raw == null ? "" : raw.replaceAll("[\\s-]", "");
        if (d.startsWith("+91")) {
            d = d.substring(3);
        } else if (d.startsWith("91") && d.length() == 12) {
            d = d.substring(2);
        } else if (d.startsWith("0") && d.length() == 11) {
            d = d.substring(1);
        }
        if (!PHONE.matcher(d).matches()) {
            throw new InvalidInputException(field + " must be a valid 10-digit mobile number");
        }
        return d;
    }

    /** Optional e-mail: blank is allowed, anything else must look like an address. */
    public static String email(String raw, String field) throws InvalidInputException {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) {
            return "";
        }
        if (s.length() > 100 || !EMAIL.matcher(s).matches()) {
            throw new InvalidInputException(field + " is not a valid e-mail address");
        }
        return s;
    }

    public static int integer(String raw, String field, int min, int max) throws InvalidInputException {
        int v;
        try {
            v = Integer.parseInt(raw == null ? "" : raw.trim());
        } catch (NumberFormatException e) {
            throw new InvalidInputException(field + " must be a whole number");
        }
        return range(v, field, min, max);
    }

    public static int range(int v, String field, int min, int max) throws InvalidInputException {
        if (v < min || v > max) {
            throw new InvalidInputException(field + " must be between " + min + " and " + max);
        }
        return v;
    }

    public static <E extends Enum<E>> E enumValue(Class<E> type, String raw, String field) throws InvalidInputException {
        String key = raw == null ? "" : raw.trim().toUpperCase(Locale.ROOT).replace(' ', '_').replace('-', '_');
        for (E e : type.getEnumConstants()) {
            if (e.name().equals(key)) {
                return e;
            }
        }
        throw new InvalidInputException(field + " must be one of " + java.util.Arrays.toString(type.getEnumConstants()));
    }

    /** Accepts ISO-8601 ("2026-10-09T12:30") or "2026-10-09 12:30". */
    public static LocalDateTime dateTime(String raw, String field) throws InvalidInputException {
        try {
            return LocalDateTime.parse((raw == null ? "" : raw.trim()).replace(' ', 'T'));
        } catch (DateTimeParseException e) {
            throw new InvalidInputException(field + " must look like 2026-10-09T12:30");
        }
    }

    public static boolean yesNo(String raw, String field) throws InvalidInputException {
        String s = raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT);
        return switch (s) {
            case "y", "yes", "true", "1" -> true;
            case "n", "no", "false", "0" -> false;
            default -> throw new InvalidInputException(field + " must be yes or no");
        };
    }
}
