/**
 * Reservation.java
 *
 * Represents a book reservation made by a student.
 *
 * OOP concepts demonstrated:
 *   - Encapsulation of reservation lifecycle.
 *   - Enumerated status (ACTIVE / CANCELED / COMPLETED).
 *   - Behavioural helper (isActive) used by the service layer.
 */
public class Reservation {

    public enum Status {
        ACTIVE,     // Currently holds the book
        CANCELED,   // Cancelled by the student
        COMPLETED,  // Converted into a borrowing transaction
        EXPIRED     // Auto-expired after 1 week with no action; carries a penalty
    }

    private String reservationId;
    private String studentId;
    private String bookId;
    private String reserveDate;   // ISO date the reservation was created
    private String expiryDate;  // ISO date the reservation expires (reserveDate + 7 days)
    private Status status;
    private int expiredPenalty;  // Peso penalty if the reservation expired without action
    private boolean paidExpired; // True if the expiry penalty has been settled

    public Reservation() {
    }

    public Reservation(String reservationId, String studentId, String bookId,
                       String reserveDate, String expiryDate, Status status,
                       int expiredPenalty, boolean paidExpired) {
        this.reservationId = reservationId;
        this.studentId = studentId;
        this.bookId = bookId;
        this.reserveDate = reserveDate;
        this.expiryDate = expiryDate;
        this.status = status;
        this.expiredPenalty = expiredPenalty;
        this.paidExpired = paidExpired;
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = reservationId;
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

    public String getReserveDate() {
        return reserveDate;
    }

    public void setReserveDate(String reserveDate) {
        this.reserveDate = reserveDate;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    public int getExpiredPenalty() {
        return expiredPenalty;
    }

    public void setExpiredPenalty(int expiredPenalty) {
        this.expiredPenalty = expiredPenalty;
    }

    public boolean isPaidExpired() {
        return paidExpired;
    }

    public void setPaidExpired(boolean paidExpired) {
        this.paidExpired = paidExpired;
    }

    public static Status statusFromString(String s) {
        if (s == null) return Status.ACTIVE;
        switch (s.trim().toUpperCase()) {
            case "CANCELED": case "CANCELLED": return Status.CANCELED;
            case "COMPLETED": return Status.COMPLETED;
            case "EXPIRED": return Status.EXPIRED;
            default: return Status.ACTIVE;
        }
    }
}
