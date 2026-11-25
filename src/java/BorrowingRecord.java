/**
 * BorrowingRecord.java
 *
 * Stores a single borrowing transaction (active or historical).
 *
 * OOP concepts demonstrated:
 *   - Encapsulation of borrowing lifecycle fields.
 *   - Enumerated status to avoid invalid state strings.
 *   - Behavioural helper (isOverdue) that belongs to the entity itself.
 */
public class BorrowingRecord {

    public enum Status {
        ACTIVE,    // Currently borrowed, not yet returned
        RETURNED,  // Returned to the library
        OVERDUE,   // Active and past the due date (computed view of ACTIVE)
        LOST       // Borrower reported the book as lost; carries a P1000 penalty
    }

    private String recordId;       // Unique transaction ID (also referenced by receipts)
    private String studentId;      // Owning student
    private String bookId;        // Borrowed book
    private String borrowDate;    // ISO date borrowed
    private String dueDate;       // ISO date due back
    private boolean extended;     // Has the record been extended once already?
    private String returnDate;    // ISO date returned (null/empty if not returned)
    private Status status;        // ACTIVE or RETURNED
    private String damageCategory;// "none" | "minor" | "moderate" | "severe"
    private int damagePenalty;    // Peso amount charged for damage (0 if none)
    private int overduePenalty;   // Final overdue penalty at return time (0 if none)
    private String receiptNo;     // Receipt number associated with this record
    private boolean lost;         // True if the borrower reported this book as lost
    private int lostPenalty;      // Peso amount charged when the book is lost (0 if not lost)
    private String lostDate;      // ISO date the book was reported lost
    private boolean paidLost;     // True if the lost-book penalty has been settled

    public BorrowingRecord() {
    }

    public String getRecordId() {
        return recordId;
    }

    public void setRecordId(String recordId) {
        this.recordId = recordId;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public String getBookId() {
        return bookId;
    }

    public void setBookId(String bookId) {
        this.bookId = bookId;
    }

    public String getBorrowDate() {
        return borrowDate;
    }

    public void setBorrowDate(String borrowDate) {
        this.borrowDate = borrowDate;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        this.dueDate = dueDate;
    }

    public boolean isExtended() {
        return extended;
    }

    public void setExtended(boolean extended) {
        this.extended = extended;
    }

    public String getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(String returnDate) {
        this.returnDate = returnDate;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public String getDamageCategory() {
        return damageCategory;
    }

    public void setDamageCategory(String damageCategory) {
        this.damageCategory = damageCategory;
    }

    public int getDamagePenalty() {
        return damagePenalty;
    }

    public void setDamagePenalty(int damagePenalty) {
        this.damagePenalty = damagePenalty;
    }

    public int getOverduePenalty() {
        return overduePenalty;
    }

    public void setOverduePenalty(int overduePenalty) {
        this.overduePenalty = overduePenalty;
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = receiptNo;
    }

    public static Status statusFromString(String s) {
        if (s == null) return Status.ACTIVE;
        switch (s.trim().toUpperCase()) {
            case "RETURNED": return Status.RETURNED;
            case "OVERDUE": return Status.OVERDUE;
            case "LOST": return Status.LOST;
            default: return Status.ACTIVE;
        }
    }

    public boolean isLost() {
        return lost;
    }

    public void setLost(boolean lost) {
        this.lost = lost;
    }

    public int getLostPenalty() {
        return lostPenalty;
    }

    public void setLostPenalty(int lostPenalty) {
        this.lostPenalty = lostPenalty;
    }

    public String getLostDate() {
        return lostDate;
    }

    public void setLostDate(String lostDate) {
        this.lostDate = lostDate;
    }

    public boolean isPaidLost() {
        return paidLost;
    }

    public void setPaidLost(boolean paidLost) {
        this.paidLost = paidLost;
    }
}
