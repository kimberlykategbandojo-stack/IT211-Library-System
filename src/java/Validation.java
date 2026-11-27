import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;

final class Validation {

    private Validation() {
    }

    static String requireText(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(field + " must not be empty.");
        }
        return value.trim();
    }

    static String requireStudentId(String value) {
        String id = requireText(value, "Student ID");
        if (!id.matches("\\d{8}")) {
            throw new IllegalArgumentException("Student ID must be exactly 8 digits.");
        }
        return id;
    }

    static String requireFullName(String value) {
        String name = requireText(value, "Full name");
        String[] parts = name.split("\\s+");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Full name must include a first name and a surname.");
        }
        for (String part : parts) {
            if (part.replace("-", "").replace("'", "").length() < 2
                    || !part.matches("[A-Za-z][A-Za-z'-]*")) {
                throw new IllegalArgumentException(
                        "Each part of the full name must contain letters, hyphens, or apostrophes.");
            }
        }
        return name;
    }

    static String requirePhone(String value) {
        String phone = requireText(value, "Phone number");
        String digits = phone.replaceAll("[^\\d]", "");
        if (digits.length() < 7 || !phone.matches("[\\d+()\\-\\s]+")) {
            throw new IllegalArgumentException("Phone number must contain at least 7 digits.");
        }
        return phone;
    }

    static String requireDate(String value, String field) {
        String date = requireText(value, field);
        try {
            if (!LocalDate.parse(date).toString().equals(date)) {
                throw new IllegalArgumentException(field + " must use YYYY-MM-DD format.");
            }
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(field + " must be a valid YYYY-MM-DD date.", ex);
        }
        return date;
    }

    static String optionalDate(String value, String field) {
        if (value == null || value.trim().isEmpty()) {
            return "";
        }
        return requireDate(value, field);
    }

    static String requireTimestamp(String value, String field) {
        String timestamp = requireText(value, field);
        try {
            LocalDateTime.parse(timestamp);
        } catch (DateTimeParseException ex) {
            throw new IllegalArgumentException(field + " must be a valid ISO-8601 timestamp.", ex);
        }
        return timestamp;
    }

    static int requireNonNegative(int value, String field) {
        if (value < 0) {
            throw new IllegalArgumentException(field + " must not be negative.");
        }
        return value;
    }

    static <T> T requireValue(T value, String field) {
        if (value == null) {
            throw new IllegalArgumentException(field + " must not be null.");
        }
        return value;
    }

    static void requireOrderedDates(String earlier, String later, String message) {
        if (earlier != null && !earlier.isEmpty() && later != null && !later.isEmpty()
                && LocalDate.parse(later).isBefore(LocalDate.parse(earlier))) {
            throw new IllegalArgumentException(message);
        }
    }
}
