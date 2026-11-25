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
        this.receiptNo = receiptNo;
        this.studentId = studentId;
        this.studentName = studentName;
        this.bookId = bookId;
        this.bookTitle = bookTitle;
        this.author = author;
        this.borrowDateTime = borrowDateTime;
        this.dueDate = dueDate;
        this.duration = duration;
        this.status = status;
        this.returnDate = returnDate == null ? "" : returnDate;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getStudentName() {
        return studentName;
    }

    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }

    public String getBookId() {
        return bookId;
    }

    public void setBookId(String bookId) {
        this.bookId = bookId;
    }

    public String getBookTitle() {
        return bookTitle;
    }

    public void setBookTitle(String bookTitle) {
        this.bookTitle = bookTitle;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getBorrowDateTime() {
        return borrowDateTime;
    }

    public void setBorrowDateTime(String borrowDateTime) {
        this.borrowDateTime = borrowDateTime;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public String getDuration() {
        return duration;
    }

    public void setDuration(String duration) {
        this.duration = duration;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getReturnDate() {
        return returnDate == null ? "" : returnDate;
    }

    public void setReturnDate(String returnDate) {
        this.returnDate = returnDate == null ? "" : returnDate;
    }
}
