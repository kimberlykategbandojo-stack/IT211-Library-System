/**
 * Book.java
 *
 * Represents a book in the library catalog.
 *
 * OOP concepts demonstrated:
 *   - Encapsulation: private fields with controlled accessors.
 *   - Enumerated status: a strong-typed status instead of magic strings.
 */
public class Book {

    /** Availability states used across the system. */
    public enum Status {
        AVAILABLE,   // At least one copy can be borrowed or reserved
        BORROWED,    // All copies are currently loaned out
        RESERVED     // Reserved and no copies left for new borrowers
    }

    private String bookId;        // Unique book identifier (e.g. B001)
    private String title;
    private String author;
    private String category;      // Genre / subject area
    private String description;   // Short blurb shown in the catalog card
    private String coverColor;    // Hex color used to render the CSS cover block
    private Status status;
    private int totalCopies;       // Total number of copies owned by the library
    private int availableCopies;   // Copies currently sitting on the shelf

    public Book() {
    }

    public Book(String bookId, String title, String author, String category,
                String description, String coverColor, Status status,
                int totalCopies, int availableCopies) {
        this.bookId = bookId;
        this.title = title;
        this.author = author;
        this.category = category;
        this.description = description;
        this.coverColor = coverColor;
        this.status = status;
        this.totalCopies = totalCopies;
        this.availableCopies = availableCopies;
    }

    public String getBookId() {
        return bookId;
    }

    public void setBookId(String bookId) {
        this.bookId = bookId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getCoverColor() {
        return coverColor;
    }

    public void setCoverColor(String coverColor) {
        this.coverColor = coverColor;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = status;
    }

    public int getTotalCopies() {
        return totalCopies;
    }

    public void setTotalCopies(int totalCopies) {
        this.totalCopies = totalCopies;
    }

    public int getAvailableCopies() {
        return availableCopies;
    }

    public void setAvailableCopies(int availableCopies) {
        this.availableCopies = availableCopies;
    }

    /** Convenience helper used by the file loader. */
    public static Status statusFromString(String s) {
        if (s == null) return Status.AVAILABLE;
        switch (s.trim().toUpperCase()) {
            case "BORROWED": return Status.BORROWED;
            case "RESERVED": return Status.RESERVED;
            default: return Status.AVAILABLE;
        }
    }
}
