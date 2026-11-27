/**
 * Small dependency-free tests for Validation.java.
 * Run with: java -cp out ValidationTest
 */
public class ValidationTest {

    private static int passed;

    public static void main(String[] args) {
        testRequireText();
        testStudentId();
        testFullName();
        testPhone();
        testDatesAndTimestamp();
        testValuesAndNumbers();
        testOrderedDates();
        System.out.println("Validation tests passed: " + passed);
    }

    private static void testRequireText() {
        assertEquals("Ruth", Validation.requireText(" Ruth ", "Name"));
        assertInvalid(() -> Validation.requireText(null, "Name"));
        assertInvalid(() -> Validation.requireText("  ", "Name"));
    }

    private static void testStudentId() {
        assertEquals("20261234", Validation.requireStudentId("20261234"));
        assertInvalid(() -> Validation.requireStudentId("1234"));
        assertInvalid(() -> Validation.requireStudentId("1234567A"));
    }

    private static void testFullName() {
        assertEquals("Ruth Gonzales", Validation.requireFullName("Ruth Gonzales"));
        assertInvalid(() -> Validation.requireFullName("Ruth"));
        assertInvalid(() -> Validation.requireFullName("Ruth G0nzales"));
    }

    private static void testPhone() {
        assertEquals("+63 912 345 6789", Validation.requirePhone("+63 912 345 6789"));
        assertInvalid(() -> Validation.requirePhone("123456"));
        assertInvalid(() -> Validation.requirePhone("1234567abc"));
    }

    private static void testDatesAndTimestamp() {
        assertEquals("2026-11-27", Validation.requireDate("2026-11-27", "Date"));
        assertInvalid(() -> Validation.requireDate("2026-02-30", "Date"));
        assertInvalid(() -> Validation.requireDate("27/11/2026", "Date"));
        assertEquals("", Validation.optionalDate("  ", "Date"));
        assertInvalid(() -> Validation.optionalDate("not-a-date", "Date"));
        assertEquals("2026-11-27T10:30:00",
                Validation.requireTimestamp("2026-11-27T10:30:00", "Timestamp"));
        assertInvalid(() -> Validation.requireTimestamp("not-a-timestamp", "Timestamp"));
    }

    private static void testValuesAndNumbers() {
        assertEquals(0, Validation.requireNonNegative(0, "Count"));
        assertInvalid(() -> Validation.requireNonNegative(-1, "Count"));
        assertEquals("book", Validation.requireValue("book", "Book"));
        assertInvalid(() -> Validation.requireValue(null, "Book"));
    }

    private static void testOrderedDates() {
        Validation.requireOrderedDates("2026-11-01", "2026-11-02", "Dates are out of order.");
        assertInvalid(() -> Validation.requireOrderedDates(
                "2026-11-02", "2026-11-01", "Dates are out of order."));
    }

    private static void assertInvalid(Runnable action) {
        try {
            action.run();
            throw new AssertionError("Expected IllegalArgumentException.");
        } catch (IllegalArgumentException expected) {
            passed++;
        }
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected <" + expected + "> but got <" + actual + ">.");
        }
        passed++;
    }
}
