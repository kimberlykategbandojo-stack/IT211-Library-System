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
        setBookId(bookId);
        setTitle(title);
        setAuthor(author);
        setCategory(category);
        setDescription(description);
        setCoverColor(coverColor);
        setStatus(status);
        setTotalCopies(totalCopies);
        setAvailableCopies(availableCopies);
    }

    public String getBookId() {
        return bookId;
    }

    public void setBookId(String bookId) {
        this.bookId = Validation.requireText(bookId, "Book ID");
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = Validation.requireText(title, "Book title");
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = Validation.requireText(author, "Book author");
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = Validation.requireText(category, "Book category");
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description.trim();
    }

    public String getCoverColor() {
        return coverColor;
    }

    public void setCoverColor(String coverColor) {
        String color = Validation.requireText(coverColor, "Cover color");
        if (!color.matches("#[0-9A-Fa-f]{6}")) {
            throw new IllegalArgumentException("Cover color must be a six-digit hexadecimal color.");
        }
        this.coverColor = color;
    }

    public Status getStatus() {
        return status;
    }

    public void setStatus(Status status) {
        this.status = Validation.requireValue(status, "Book status");
    }

    public int getTotalCopies() {
        return totalCopies;
    }

    public void setTotalCopies(int totalCopies) {
        Validation.requireNonNegative(totalCopies, "Total copies");
        if (totalCopies < availableCopies) {
            throw new IllegalArgumentException("Total copies cannot be less than available copies.");
        }
        this.totalCopies = totalCopies;
    }

    public int getAvailableCopies() {
        return availableCopies;
    }

    public void setAvailableCopies(int availableCopies) {
        Validation.requireNonNegative(availableCopies, "Available copies");
        if (availableCopies > totalCopies) {
            throw new IllegalArgumentException("Available copies cannot exceed total copies.");
        }
        this.availableCopies = availableCopies;
    }

    /** Convenience helper used by the file loader. */
    public static Status statusFromString(String s) {
        if (s == null || s.trim().isEmpty()) {
            throw new IllegalArgumentException("Book status must not be empty.");
        }
        try {
            return Status.valueOf(s.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Invalid book status: " + s, ex);
        }
    }
}
