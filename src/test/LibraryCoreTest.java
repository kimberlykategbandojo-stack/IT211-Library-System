import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Dependency-free smoke tests for the library's core classes. */
public class LibraryCoreTest {

    private static int passed;

    public static void main(String[] args) {
        testStudent();
        testBook();
        testBorrowingRecord();
        testReservation();
        testDigitalReceipt();
        testFileStorage();
        testLibraryServiceFlow();
        System.out.println("Core class tests passed: " + passed);
    }

    private static void testStudent() {
        Student student = new Student("20261234", "Ruth Gonzales", "09123456789",
                true, "2026-11-27T10:00:00");
        assertEquals("Ruth Gonzales", student.getFullName());
        assertInvalid(() -> new Student("bad-id", "Ruth Gonzales", "09123456789",
                true, "2026-11-27T10:00:00"));
    }

    private static void testBook() {
        Book book = new Book("B001", "Sample Book", "A. Writer", "Fiction", "", "#123ABC",
                Book.Status.AVAILABLE, 2, 2);
        assertEquals(2, book.getAvailableCopies());
        assertInvalid(() -> book.setCoverColor("blue"));
        assertInvalid(() -> book.setAvailableCopies(3));
    }

    private static void testBorrowingRecord() {
        BorrowingRecord record = new BorrowingRecord();
        record.setRecordId("BR001");
        record.setStudentId("20261234");
        record.setBookId("B001");
        record.setBorrowDate("2026-11-20");
        record.setDueDate("2026-11-23");
        record.setStatus(BorrowingRecord.Status.ACTIVE);
        assertEquals("2026-11-23", record.getDueDate());
        assertInvalid(() -> record.setDueDate("2026-11-19"));
        assertInvalid(() -> record.setDamagePenalty(-1));
    }

    private static void testReservation() {
        Reservation reservation = new Reservation("RS001", "20261234", "B001",
                "2026-11-20", "2026-11-27", Reservation.Status.ACTIVE, 0, false);
        assertTrue(reservation.isActive());
        assertInvalid(() -> reservation.setExpiryDate("2026-11-19"));
        assertInvalid(() -> reservation.setExpiredPenalty(-1));
    }

    private static void testDigitalReceipt() {
        DigitalReceipt receipt = new DigitalReceipt("RC001", "20261234", "Ruth Gonzales",
                "B001", "Sample Book", "A. Writer", "2026-11-20T10:00:00",
                "2026-11-23", "3 days", "ACTIVE", "");
        assertEquals("RC001", receipt.getReceiptNo());
        assertInvalid(() -> receipt.setDuration("zero days"));
        assertInvalid(() -> receipt.setStatus("UNKNOWN"));
    }

    private static void testFileStorage() {
        String escapedRecord = "STUDENT|" + FileStorage.escape("Ada|Lovelace");
        String[] fields = FileStorage.splitRecord(escapedRecord);
        assertEquals("Ada|Lovelace", fields[1]);
        assertEquals("one\\two", FileStorage.unescape(FileStorage.escape("one\\two")));
    }

    private static void testLibraryServiceFlow() {
        LibraryService service = new LibraryService(new MemoryStorage());
        Map<String, Object> invalid = service.registerStudent("not-an-id", "Ruth Gonzales",
                "09123456789", true);
        assertEquals(false, invalid.get("success"));

        Map<String, Object> registered = service.registerStudent("20261234", "Ruth Gonzales",
                "09123456789", true);
        assertEquals(true, registered.get("success"));
        assertTrue(!service.listBooks("", "").isEmpty());

        Map<String, Object> borrowed = service.borrowBook("20261234", "B001");
        assertEquals(true, borrowed.get("success"));
        Map<String, Object> receiptResult = service.getReceipt("20261234",
                String.valueOf(borrowed.get("receiptNo")));
        assertEquals(true, receiptResult.get("success"));
        @SuppressWarnings("unchecked")
        Map<String, Object> receipt = (Map<String, Object>) receiptResult.get("receipt");
        assertEquals("1984", receipt.get("bookTitle"));
    }

    private static void assertInvalid(Runnable action) {
        try {
            action.run();
        } catch (IllegalArgumentException expected) {
            passed++;
            return;
        }
        throw new AssertionError("Expected IllegalArgumentException.");
    }

    private static void assertTrue(boolean value) {
        if (!value) throw new AssertionError("Expected condition to be true.");
        passed++;
    }

    private static void assertEquals(Object expected, Object actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            throw new AssertionError("Expected <" + expected + "> but got <" + actual + ">.");
        }
        passed++;
    }

    /** Keeps the service flow test in memory instead of changing library data files. */
    private static final class MemoryStorage extends FileStorage {
        @Override
        public synchronized List<String> readAllLines() {
            return Collections.emptyList();
        }

        @Override
        public synchronized void saveAll(StringBuilder content) throws IOException {
            // Intentionally do not write to disk during this test.
        }
    }
}
