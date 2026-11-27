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
        this.recordId = Validation.requireText(recordId, "Borrowing record ID");
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = Validation.requireStudentId(studentId);
    }

    public String getBookId() {
        return bookId;
    }

    public void setBookId(String bookId) {
        this.bookId = Validation.requireText(bookId, "Book ID");
    }

    public String getBorrowDate() {
        return borrowDate;
    }

    public void setBorrowDate(String borrowDate) {
        String date = Validation.requireDate(borrowDate, "Borrow date");
        Validation.requireOrderedDates(date, dueDate, "Due date cannot be before borrow date.");
        Validation.requireOrderedDates(date, returnDate, "Return date cannot be before borrow date.");
        Validation.requireOrderedDates(date, lostDate, "Lost date cannot be before borrow date.");
        this.borrowDate = date;
    }

    public String getDueDate() {
        return dueDate;
    }

    public void setDueDate(String dueDate) {
        String date = Validation.requireDate(dueDate, "Due date");
        Validation.requireOrderedDates(borrowDate, date, "Due date cannot be before borrow date.");
        this.dueDate = date;
    }

    public boolean isExtended() {
        return extended;
    }

    public void setExtended(boolean extended) {
        if (this.extended && !extended) {
            throw new IllegalArgumentException("A borrowing extension cannot be undone.");
        }
        this.extended = extended;
    }

    public String getReturnDate() {
        return returnDate;
    }

    public void setReturnDate(String returnDate) {
        String date = Validation.optionalDate(returnDate, "Return date");
        Validation.requireOrderedDates(borrowDate, date, "Return date cannot be before borrow date.");
        if (status == Status.RETURNED && date.isEmpty()) {
            throw new IllegalArgumentException("A returned borrowing must keep its return date.");
        }
        if (!date.isEmpty() && lost) {
            throw new IllegalArgumentException("A lost borrowing cannot also have a return date.");
        }
        this.returnDate = date;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        Status value = Validation.requireValue(status, "Borrowing status");
        if ((this.status == Status.RETURNED || this.status == Status.LOST) && value != this.status) {
            throw new IllegalArgumentException("A completed borrowing cannot be reopened or changed.");
        }
        if (value == Status.LOST && !lost) {
            throw new IllegalArgumentException("Set the lost flag before marking a borrowing as lost.");
        }
        if (value == Status.LOST && (lostDate == null || lostDate.isEmpty())) {
            throw new IllegalArgumentException("A lost borrowing must have a lost date.");
        }
        if (value == Status.RETURNED && (returnDate == null || returnDate.isEmpty())) {
            throw new IllegalArgumentException("A returned borrowing must have a return date.");
        }
        this.status = value;
    }

    public String getDamageCategory() {
        return damageCategory;
    }

    public void setDamageCategory(String damageCategory) {
        String category = Validation.requireText(damageCategory, "Damage category").toLowerCase();
        if (!category.matches("none|minor|moderate|severe")) {
            throw new IllegalArgumentException("Damage category must be none, minor, moderate, or severe.");
        }
        this.damageCategory = category;
    }

    public int getDamagePenalty() {
        return damagePenalty;
    }

    public void setDamagePenalty(int damagePenalty) {
        this.damagePenalty = Validation.requireNonNegative(damagePenalty, "Damage penalty");
    }

    public int getOverduePenalty() {
        return overduePenalty;
    }

    public void setOverduePenalty(int overduePenalty) {
        this.overduePenalty = Validation.requireNonNegative(overduePenalty, "Overdue penalty");
    }

    public String getReceiptNo() {
        return receiptNo;
    }

    public void setReceiptNo(String receiptNo) {
        this.receiptNo = Validation.requireText(receiptNo, "Receipt number");
    }

    public static Status statusFromString(String s) {
        if (s == null || s.trim().isEmpty()) {
            throw new IllegalArgumentException("Borrowing status must not be empty.");
        }
        try {
            return Status.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid borrowing status: " + s, ex);
        }
    }

    public boolean isLost() {
        return lost;
    }

    public void setLost(boolean lost) {
        if (this.lost && !lost) {
            throw new IllegalArgumentException("A lost-book report cannot be undone.");
        }
        if (lost && returnDate != null && !returnDate.isEmpty()) {
            throw new IllegalArgumentException("A returned borrowing cannot be marked as lost.");
        }
        this.lost = lost;
    }

    public int getLostPenalty() {
        return lostPenalty;
    }

    public void setLostPenalty(int lostPenalty) {
        this.lostPenalty = Validation.requireNonNegative(lostPenalty, "Lost-book penalty");
    }

    public String getLostDate() {
        return lostDate;
    }

    public void setLostDate(String lostDate) {
        String date = Validation.optionalDate(lostDate, "Lost date");
        Validation.requireOrderedDates(borrowDate, date, "Lost date cannot be before borrow date.");
        if (status == Status.LOST && date.isEmpty()) {
            throw new IllegalArgumentException("A lost borrowing must keep its lost date.");
        }
        if (!date.isEmpty() && !lost) {
            throw new IllegalArgumentException("Set the lost flag before recording a lost date.");
        }
        this.lostDate = date;
    }

    public boolean isPaidLost() {
        return paidLost;
    }

    public void setPaidLost(boolean paidLost) {
        if (this.paidLost && !paidLost) {
            throw new IllegalArgumentException("A settled lost-book penalty cannot be unpaid.");
        }
        this.paidLost = paidLost;
    }
}
