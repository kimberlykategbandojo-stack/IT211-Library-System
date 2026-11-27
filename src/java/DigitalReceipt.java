/**
 * DigitalReceipt.java
 *
 * Immutable-ish value object representing a digital borrowing receipt.
 *
 * OOP concepts demonstrated:
 *   - Encapsulation of receipt data.
 *   - Single responsibility: only stores receipt information.
 *
 * IMPORTANT: Closing the receipt in the UI never deletes the underlying
 * record stored by FileStorage.  Receipts persist across restarts.
 */
public class DigitalReceipt {

    private String receiptNo;        // Unique receipt / transaction ID
    private String studentId;
    private String studentName;
    private String bookId;
    private String bookTitle;
    private String author;
    private String borrowDateTime;   // ISO timestamp borrow was completed
    private String dueDate;          // ISO date book is due back
    private String duration;         // Human-readable borrow duration (e.g. "3 days")
    private String status;           // ACTIVE / RETURNED / OVERDUE
    private String returnDate;       // ISO date the book was returned (empty if not yet)

    public DigitalReceipt() {
    }

    public DigitalReceipt(String receiptNo, String studentId, String studentName,
                          String bookId, String bookTitle, String author,
                          String borrowDateTime, String dueDate, String duration,
                          String status, String returnDate) {
        setReceiptNo(receiptNo);
        setStudentId(studentId);
        setStudentName(studentName);
        setBookId(bookId);
        setBookTitle(bookTitle);
        setAuthor(author);
        setBorrowDateTime(borrowDateTime);
        setDueDate(dueDate);
        setDuration(duration);
        setStatus(status);
        setReturnDate(returnDate);
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = Validation.requireText(receiptNo, "Receipt number");
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = Validation.requireStudentId(studentId);
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = Validation.requireFullName(studentName);
    }

    public String getBookId() {
        return bookId;
    }

    public void setBookId(String bookId) {
        this.bookId = Validation.requireText(bookId, "Book ID");
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
        this.bookTitle = Validation.requireText(bookTitle, "Book title");
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = Validation.requireText(author, "Book author");
    }

    public String getBorrowDateTime() {
        return borrowDateTime;
    }

    public void setBorrowDateTime(String borrowDateTime) {
        String timestamp = Validation.requireTimestamp(borrowDateTime, "Borrow timestamp");
        validateReturnDate(returnDate, timestamp);
        validateDueDate(dueDate, timestamp);
        this.borrowDateTime = timestamp;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        String date = Validation.requireDate(dueDate, "Due date");
        validateDueDate(date, borrowDateTime);
        this.dueDate = date;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        String value = Validation.requireText(duration, "Borrow duration");
        if (!value.matches("[1-9]\\d* days(?: \\(extended\\))?")) {
            throw new IllegalArgumentException("Borrow duration must be a positive number of days.");
        }
        this.duration = value;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        String value = Validation.requireText(status, "Receipt status").toUpperCase();
        if (!value.matches("ACTIVE|RETURNED|RETURNED-OVERDUE|LOST")) {
            throw new IllegalArgumentException("Invalid receipt status: " + status);
        }
        this.status = value;
    }

    public String getReturnDate() {
        return returnDate == null ? "" : returnDate;
    }

    public void setReturnDate(String returnDate) {
        String date = Validation.optionalDate(returnDate, "Return date");
        validateReturnDate(date, borrowDateTime);
        this.returnDate = date;
    }

    private static void validateReturnDate(String returnDate, String borrowDateTime) {
        if (returnDate != null && !returnDate.isEmpty()
                && borrowDateTime != null && !borrowDateTime.isEmpty()
                && java.time.LocalDate.parse(returnDate).isBefore(
                        java.time.LocalDateTime.parse(borrowDateTime).toLocalDate())) {
            throw new IllegalArgumentException("Return date cannot be before borrow date.");
        }
    }

    private static void validateDueDate(String dueDate, String borrowDateTime) {
        if (dueDate != null && !dueDate.isEmpty()
                && borrowDateTime != null && !borrowDateTime.isEmpty()
                && java.time.LocalDate.parse(dueDate).isBefore(
                        java.time.LocalDateTime.parse(borrowDateTime).toLocalDate())) {
            throw new IllegalArgumentException("Due date cannot be before borrow date.");
        }
    }
}
