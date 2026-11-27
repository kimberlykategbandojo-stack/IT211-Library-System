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
        setReservationId(reservationId);
        setStudentId(studentId);
        setBookId(bookId);
        setReserveDate(reserveDate);
        setExpiryDate(expiryDate);
        setStatus(status);
        setExpiredPenalty(expiredPenalty);
        setPaidExpired(paidExpired);
    }

    public String getReservationId() {
        return reservationId;
    }

    public void setReservationId(String reservationId) {
        this.reservationId = Validation.requireText(reservationId, "Reservation ID");
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

    public String getReserveDate() {
        return reserveDate;
    }

    public void setReserveDate(String reserveDate) {
        String date = Validation.requireDate(reserveDate, "Reservation date");
        Validation.requireOrderedDates(date, expiryDate, "Expiry date cannot be before reservation date.");
        this.reserveDate = date;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        Status value = Validation.requireValue(status, "Reservation status");
        if (this.status == Status.CANCELED && value != Status.CANCELED) {
            throw new IllegalArgumentException("A canceled reservation cannot be reopened.");
        }
        if (this.status == Status.COMPLETED && value != Status.COMPLETED) {
            throw new IllegalArgumentException("A completed reservation cannot be reopened.");
        }
        if (this.status == Status.EXPIRED && value != Status.EXPIRED) {
            throw new IllegalArgumentException("An expired reservation cannot be reopened.");
        }
        this.status = value;
    }

    public boolean isActive() {
        return status == Status.ACTIVE;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        String date = Validation.optionalDate(expiryDate, "Expiry date");
        Validation.requireOrderedDates(reserveDate, date, "Expiry date cannot be before reservation date.");
        this.expiryDate = date;
    }

    public int getExpiredPenalty() {
        return expiredPenalty;
    }

    public void setExpiredPenalty(int expiredPenalty) {
        this.expiredPenalty = Validation.requireNonNegative(expiredPenalty, "Expired-reservation penalty");
    }

    public boolean isPaidExpired() {
        return paidExpired;
    }

    public void setPaidExpired(boolean paidExpired) {
        if (this.paidExpired && !paidExpired) {
            throw new IllegalArgumentException("A settled expiry penalty cannot be unpaid.");
        }
        this.paidExpired = paidExpired;
    }

    public static Status statusFromString(String s) {
        if (s == null || s.trim().isEmpty()) {
            throw new IllegalArgumentException("Reservation status must not be empty.");
        }
        String value = s.trim().toUpperCase();
        if ("CANCELLED".equals(value)) {
            value = "CANCELED";
        }
        try {
            return Status.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid reservation status: " + s, ex);
        }
    }
}
