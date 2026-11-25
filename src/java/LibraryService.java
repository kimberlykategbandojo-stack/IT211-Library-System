/**
 * LibraryService.java
 *
 * Central business-logic layer for the Online Library System.
 *
 * Responsibilities
 *   - Hold the in-memory state (students, books, records, reservations,
 *     receipts, counters, sessions) loaded from FileStorage at startup.
 *   - Enforce every borrowing / reservation / penalty rule.
 *   - Persist state back to FileStorage after every mutation.
 *   - Provide a simple in-memory session token store (NOT a secure
 *     authentication system - see the README for limitations).
 *
 * OOP concepts demonstrated
 *   - Composition: LibraryService owns FileStorage and all entity collections.
 *   - Encapsulation: every collection is private and mutated only through
 *     synchronized service methods.
 *   - Single responsibility: this class is the only place where the
 *     borrowing rules are enforced.
 *
 * Thread safety
 *   Every public mutation method is `synchronized`. The HttpServer uses
 *   multiple worker threads, so concurrent requests are serialised here
 *   before they can change book availability or create records.
 *
 * Borrowing rules (all enforced here, not in the browser)
 *   1. Each student may have at most TWO active borrowings.
 *   2. A student may NOT borrow a new book while they already have any
 *      UNRETURNED book.  Combined with rule 1 this effectively limits a
 *      student to ONE active borrowing at a time.  See the README for the
 *      full explanation.
 *   3. Initial borrowing period: 3 days.
 *   4. Each record may be extended ONCE, adding 2 days.
 *   5. A book that is BORROWED or RESERVED cannot be borrowed by anyone
 *      else, and cannot be reserved again.
 *   6. Returns are final; the historical record is preserved.
 *
 * Penalty rules (all calculated here, never trusted from the browser)
 *   - Overdue: P10 per completed overdue day.
 *   - Damage: minor=P50, moderate=P150, severe=P300 (configurable below).
 */
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class LibraryService {

    // ----- configurable policy values ------------------------------------
    public static final int MAX_ACTIVE_BORROWS         = 2;
    public static final int MAX_ACTIVE_RESERVATIONS    = 2;
    public static final int INITIAL_BORROW_DAYS        = 3;
    public static final int EXTENSION_DAYS             = 2;
    public static final int OVERDUE_PENALTY_PER_DAY    = 20;   // was 10
    public static final int DAMAGE_MINOR_PENALTY       = 50;
    public static final int DAMAGE_MODERATE_PENALTY    = 150;
    public static final int DAMAGE_SEVERE_PENALTY      = 300;
    public static final int LOST_BOOK_PENALTY          = 1000;
    public static final int RESERVATION_EXPIRY_DAYS    = 7;
    public static final int EXPIRED_RESERVATION_PENALTY = 50;

    private final FileStorage storage;

    // ----- in-memory state -----------------------------------------------
    private final Map<String, Student>          students     = new LinkedHashMap<>();
    private final Map<String, Book>             books        = new LinkedHashMap<>();
    private final Map<String, BorrowingRecord>  borrowings   = new LinkedHashMap<>();
    private final Map<String, Reservation>      reservations = new LinkedHashMap<>();
    private final Map<String, DigitalReceipt>   receipts     = new LinkedHashMap<>();

    // ----- sequence counters (persisted) ---------------------------------
    private int borrowSeq     = 0;
    private int receiptSeq    = 0;
    private int reservationSeq = 0;

    // ----- session tokens (in-memory only, NOT persisted) ----------------
    private final Map<String, String> sessions = new HashMap<>(); // token -> studentId

    public LibraryService(FileStorage storage) {
        this.storage = storage;
        load();
        if (books.isEmpty()) {
            seedSampleBooks();
            persist();
        }
        // On startup, expire any reservations whose 1-week window has elapsed.
        checkAndExpireReservations();
    }

    // =====================================================================
    //  Persistence
    // =====================================================================
    private void load() {
        try {
            List<String> lines = storage.readAllLines();
            for (String raw : lines) {
                if (raw == null) continue;
                String line = raw.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = FileStorage.splitRecord(line);
                if (p.length == 0) continue;
                String type = p[0];
                try {
                    switch (type) {
                        case "STUDENT": {
                            Student s = new Student(
                                    p.length > 1 ? p[1] : "",
                                    p.length > 2 ? p[2] : "",
                                    p.length > 3 ? p[3] : "",
                                    p.length > 4 && "true".equalsIgnoreCase(p[4]),
                                    p.length > 5 ? p[5] : "");
                            students.put(s.getStudentId(), s);
                            break;
                        }
                        case "BOOK": {
                            Book b = new Book();
                            b.setBookId(p.length > 1 ? p[1] : "");
                            b.setTitle(p.length > 2 ? p[2] : "");
                            b.setAuthor(p.length > 3 ? p[3] : "");
                            b.setCategory(p.length > 4 ? p[4] : "");
                            b.setDescription(p.length > 5 ? p[5] : "");
                            b.setCoverColor(p.length > 6 ? p[6] : "#1f2937");
                            b.setStatus(Book.statusFromString(p.length > 7 ? p[7] : "AVAILABLE"));
                            b.setTotalCopies(p.length > 8 ? parseIntSafe(p[8]) : 1);
                            // availableCopies defaults to totalCopies for old records that don't have it
                            b.setAvailableCopies(p.length > 9 ? parseIntSafe(p[9]) : b.getTotalCopies());
                            books.put(b.getBookId(), b);
                            break;
                        }
                        case "BORROW": {
                            BorrowingRecord r = new BorrowingRecord();
                            r.setRecordId(p.length > 1 ? p[1] : "");
                            r.setStudentId(p.length > 2 ? p[2] : "");
                            r.setBookId(p.length > 3 ? p[3] : "");
                            r.setBorrowDate(p.length > 4 ? p[4] : "");
                            r.setDueDate(p.length > 5 ? p[5] : "");
                            r.setExtended(p.length > 6 && "true".equalsIgnoreCase(p[6]));
                            r.setReturnDate(p.length > 7 ? p[7] : "");
                            r.setStatus(BorrowingRecord.statusFromString(p.length > 8 ? p[8] : "ACTIVE"));
                            r.setDamageCategory(p.length > 9 ? p[9] : "none");
                            r.setDamagePenalty(p.length > 10 ? parseIntSafe(p[10]) : 0);
                            r.setOverduePenalty(p.length > 11 ? parseIntSafe(p[11]) : 0);
                            r.setReceiptNo(p.length > 12 ? p[12] : "");
                            r.setLost(p.length > 13 && "true".equalsIgnoreCase(p[13]));
                            r.setLostPenalty(p.length > 14 ? parseIntSafe(p[14]) : 0);
                            r.setLostDate(p.length > 15 ? p[15] : "");
                            r.setPaidLost(p.length > 16 && "true".equalsIgnoreCase(p[16]));
                            borrowings.put(r.getRecordId(), r);
                            break;
                        }
                        case "RESERVE": {
                            Reservation r = new Reservation();
                            r.setReservationId(p.length > 1 ? p[1] : "");
                            r.setStudentId(p.length > 2 ? p[2] : "");
                            r.setBookId(p.length > 3 ? p[3] : "");
                            r.setReserveDate(p.length > 4 ? p[4] : "");
                            r.setExpiryDate(p.length > 5 ? p[5] : "");
                            r.setStatus(Reservation.statusFromString(p.length > 6 ? p[6] : "ACTIVE"));
                            r.setExpiredPenalty(p.length > 7 ? parseIntSafe(p[7]) : 0);
                            r.setPaidExpired(p.length > 8 && "true".equalsIgnoreCase(p[8]));
                            reservations.put(r.getReservationId(), r);
                            break;
                        }
                        case "RECEIPT": {
                            DigitalReceipt d = new DigitalReceipt();
                            d.setReceiptNo(p.length > 1 ? p[1] : "");
                            d.setStudentId(p.length > 2 ? p[2] : "");
                            d.setStudentName(p.length > 3 ? p[3] : "");
                            d.setBookId(p.length > 4 ? p[4] : "");
                            d.setBookTitle(p.length > 5 ? p[5] : "");
                            d.setAuthor(p.length > 6 ? p[6] : "");
                            d.setBorrowDateTime(p.length > 7 ? p[7] : "");
                            d.setDueDate(p.length > 8 ? p[8] : "");
                            d.setDuration(p.length > 9 ? p[9] : "");
                            d.setStatus(p.length > 10 ? p[10] : "ACTIVE");
                            d.setReturnDate(p.length > 11 ? p[11] : "");
                            receipts.put(d.getReceiptNo(), d);
                            break;
                        }
                        case "COUNTER": {
                            borrowSeq      = p.length > 1 ? parseIntSafe(p[1]) : 0;
                            receiptSeq     = p.length > 2 ? parseIntSafe(p[2]) : 0;
                            reservationSeq = p.length > 3 ? parseIntSafe(p[3]) : 0;
                            break;
                        }
                        default:
                            // ignore unknown record types - forward compatibility
                            break;
                    }
                } catch (Exception ex) {
                    System.err.println("[FileStorage] Skipping malformed line: " + ex.getMessage());
                }
            }
        } catch (IOException e) {
            System.err.println("[FileStorage] Failed to read data file: " + e.getMessage());
        }
    }

    /** Re-serialise every record to data/library-data.txt. */
    public synchronized void persist() {
        StringBuilder sb = new StringBuilder();
        sb.append("# Online Library System - persistent data file\n");
        sb.append("# Format: TYPE|field1|field2|...\n");
        sb.append("# Special chars are escaped (see FileStorage).\n\n");

        sb.append("#STUDENT\n");
        for (Student s : students.values()) {
            sb.append("STUDENT")
              .append('|').append(FileStorage.escape(s.getStudentId()))
              .append('|').append(FileStorage.escape(s.getFullName()))
              .append('|').append(FileStorage.escape(s.getPhone()))
              .append('|').append(s.isAgreedTerms())
              .append('|').append(FileStorage.escape(s.getRegisteredAt()))
              .append('\n');
        }
        sb.append('\n');

        sb.append("#BOOK\n");
        for (Book b : books.values()) {
            sb.append("BOOK")
              .append('|').append(FileStorage.escape(b.getBookId()))
              .append('|').append(FileStorage.escape(b.getTitle()))
              .append('|').append(FileStorage.escape(b.getAuthor()))
              .append('|').append(FileStorage.escape(b.getCategory()))
              .append('|').append(FileStorage.escape(b.getDescription()))
              .append('|').append(FileStorage.escape(b.getCoverColor()))
              .append('|').append(b.getStatus().name())
              .append('|').append(b.getTotalCopies())
              .append('|').append(b.getAvailableCopies())
              .append('\n');
        }
        sb.append('\n');

        sb.append("#BORROW\n");
        for (BorrowingRecord r : borrowings.values()) {
            sb.append("BORROW")
              .append('|').append(FileStorage.escape(r.getRecordId()))
              .append('|').append(FileStorage.escape(r.getStudentId()))
              .append('|').append(FileStorage.escape(r.getBookId()))
              .append('|').append(FileStorage.escape(r.getBorrowDate()))
              .append('|').append(FileStorage.escape(r.getDueDate()))
              .append('|').append(r.isExtended())
              .append('|').append(FileStorage.escape(safe(r.getReturnDate())))
              .append('|').append(r.getStatus().name())
              .append('|').append(FileStorage.escape(safe(r.getDamageCategory())))
              .append('|').append(r.getDamagePenalty())
              .append('|').append(r.getOverduePenalty())
              .append('|').append(FileStorage.escape(safe(r.getReceiptNo())))
              .append('|').append(r.isLost())
              .append('|').append(r.getLostPenalty())
              .append('|').append(FileStorage.escape(safe(r.getLostDate())))
              .append('|').append(r.isPaidLost())
              .append('\n');
        }
        sb.append('\n');

        sb.append("#RESERVE\n");
        for (Reservation r : reservations.values()) {
            sb.append("RESERVE")
              .append('|').append(FileStorage.escape(r.getReservationId()))
              .append('|').append(FileStorage.escape(r.getStudentId()))
              .append('|').append(FileStorage.escape(r.getBookId()))
              .append('|').append(FileStorage.escape(r.getReserveDate()))
              .append('|').append(FileStorage.escape(safe(r.getExpiryDate())))
              .append('|').append(r.getStatus().name())
              .append('|').append(r.getExpiredPenalty())
              .append('|').append(r.isPaidExpired())
              .append('\n');
        }
        sb.append('\n');

        sb.append("#RECEIPT\n");
        for (DigitalReceipt d : receipts.values()) {
            sb.append("RECEIPT")
              .append('|').append(FileStorage.escape(d.getReceiptNo()))
              .append('|').append(FileStorage.escape(d.getStudentId()))
              .append('|').append(FileStorage.escape(d.getStudentName()))
              .append('|').append(FileStorage.escape(d.getBookId()))
              .append('|').append(FileStorage.escape(d.getBookTitle()))
              .append('|').append(FileStorage.escape(d.getAuthor()))
              .append('|').append(FileStorage.escape(d.getBorrowDateTime()))
              .append('|').append(FileStorage.escape(d.getDueDate()))
              .append('|').append(FileStorage.escape(d.getDuration()))
              .append('|').append(FileStorage.escape(d.getStatus()))
              .append('|').append(FileStorage.escape(d.getReturnDate()))
              .append('\n');
        }
        sb.append('\n');

        sb.append("#COUNTERS\n");
        sb.append("COUNTER|").append(borrowSeq)
          .append('|').append(receiptSeq)
          .append('|').append(reservationSeq)
          .append('\n');

        try {
            storage.saveAll(sb);
        } catch (IOException e) {
            System.err.println("[FileStorage] Failed to save data: " + e.getMessage());
        }
    }

    private static String safe(String s) { return s == null ? "" : s; }
    private static int parseIntSafe(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Integer.parseInt(s.trim()); }
        catch (NumberFormatException e) { return 0; }
    }

    // =====================================================================
    //  Sample seed data
    // =====================================================================
    private void seedSampleBooks() {
        // ----- Fiction (3 copies each where noted) -----
        addBook("B001", "1984", "George Orwell", "Fiction",
                "A dystopian novel about totalitarian surveillance and thought control.",
                "#7c2d12", 2);
        addBook("B002", "Brave New World", "Aldous Huxley", "Fiction",
                "A dystopian vision of a future society engineered for happiness through conformity.",
                "#9a3412", 1);
        addBook("B003", "Pride and Prejudice", "Jane Austen", "Fiction",
                "A witty romance examining manners, marriage and social class in Regency England.",
                "#be123c", 2);
        addBook("B004", "To Kill a Mockingbird", "Harper Lee", "Fiction",
                "A powerful tale of racial injustice in the American South.",
                "#a16207", 1);
        addBook("B005", "The Great Gatsby", "F. Scott Fitzgerald", "Fiction",
                "A portrait of jazz-age ambition, wealth, and disillusionment.",
                "#15803d", 2);

        // ----- Science -----
        addBook("B006", "A Brief History of Time", "Stephen Hawking", "Science",
                "An accessible tour through cosmology, black holes and time.",
                "#1e40af", 2);
        addBook("B007", "Cosmos", "Carl Sagan", "Science",
                "A sweeping exploration of the universe and our place in it.",
                "#0e7490", 1);
        addBook("B008", "The Selfish Gene", "Richard Dawkins", "Science",
                "A gene-centred view of evolution that reshaped how we think about life.",
                "#155e75", 3);
        addBook("B009", "A Short History of Nearly Everything", "Bill Bryson", "Science",
                "An accessible journey through the major discoveries of modern science.",
                "#0c4a6e", 2);

        // ----- Programming -----
        addBook("B010", "Clean Code", "Robert C. Martin", "Programming",
                "A handbook of agile software craftsmanship and readable code.",
                "#166534", 2);
        addBook("B011", "Design Patterns", "Erich Gamma et al.", "Programming",
                "Reusable object-oriented design patterns catalogued by the Gang of Four.",
                "#14532d", 1);
        addBook("B012", "Introduction to Algorithms", "Cormen, Leiserson, Rivest, Stein", "Programming",
                "The standard textbook on algorithms and data structures.",
                "#3f6212", 3);
        addBook("B013", "Refactoring", "Martin Fowler", "Programming",
                "Improving the design of existing code through well-defined refactoring steps.",
                "#365314", 1);
        addBook("B014", "The Pragmatic Programmer", "Andrew Hunt & David Thomas", "Programming",
                "Practical advice for becoming a better, more thoughtful programmer.",
                "#166534", 2);

        // ----- Networking (NEW category) -----
        addBook("B015", "Computer Networking: A Top-Down Approach", "Kurose & Ross", "Networking",
                "A modern introduction to computer networks, from the application layer down to the physical layer.",
                "#0d9488", 2);
        addBook("B016", "TCP/IP Illustrated, Volume 1", "W. Richard Stevens", "Networking",
                "The definitive guide to the protocols that drive the Internet.",
                "#0f766e", 1);
        addBook("B017", "Network Warrior", "Gary A. Donahue", "Networking",
                "Practical, real-world advice for designing and troubleshooting networks.",
                "#115e59", 2);
        addBook("B018", "Cisco CCNA Routing and Switching", "Wendell Odom", "Networking",
                "The official certification guide for aspiring network engineers.",
                "#134e4a", 1);

        // ----- Astronomy (NEW category) -----
        addBook("B019", "Astronomy: A Beginner's Guide to the Universe", "Eric Chaisson & Steve McMillan", "Astronomy",
                "An inviting introduction to planets, stars, galaxies, and the cosmos.",
                "#4338ca", 2);
        addBook("B020", "The Universe in a Nutshell", "Stephen Hawking", "Astronomy",
                "A richly illustrated tour of the cutting edge of theoretical physics and cosmology.",
                "#3730a3", 1);
        addBook("B021", "Pale Blue Dot", "Carl Sagan", "Astronomy",
                "Reflections on humanity's place in the cosmos and the future of space exploration.",
                "#1e1b4b", 2);
        addBook("B022", "Astrophysics for People in a Hurry", "Neil deGrasse Tyson", "Astronomy",
                "A compact, witty guide to the universe for readers short on time.",
                "#312e81", 3);

        // ----- History -----
        addBook("B023", "1491", "Charles C. Mann", "History",
                "New revelations of the Americas before Columbus arrived.",
                "#92400e", 1);
        addBook("B024", "Guns, Germs, and Steel", "Jared Diamond", "History",
                "How geography shaped the fates of human societies.",
                "#78350f", 2);
        addBook("B025", "Sapiens", "Yuval Noah Harari", "History",
                "A brief history of humankind from foraging to the modern age.",
                "#a16207", 3);
        addBook("B026", "The Silk Roads", "Peter Frankopan", "History",
                "A reassessment of world history through the lens of the networks connecting East and West.",
                "#854d0e", 1);

        // ----- Philosophy -----
        addBook("B027", "Beyond Good and Evil", "Friedrich Nietzsche", "Philosophy",
                "A philosophical investigation of morality and the will to truth.",
                "#6b21a8", 2);
        addBook("B028", "Meditations", "Marcus Aurelius", "Philosophy",
                "Stoic reflections from a Roman emperor's private notebook.",
                "#7e22ce", 1);
        addBook("B029", "The Republic", "Plato", "Philosophy",
                "A foundational dialogue on justice, the soul, and the ideal state.",
                "#581c87", 2);
        addBook("B030", "Tao Te Ching", "Laozi", "Philosophy",
                "A classic Chinese text on harmony, simplicity, and the nature of the Tao.",
                "#86198f", 1);

        // ----- Psychology -----
        addBook("B031", "Emotional Intelligence", "Daniel Goleman", "Psychology",
                "Why EQ can matter more than IQ, with practical guidance for developing it.",
                "#be185d", 2);
        addBook("B032", "Flow: The Psychology of Optimal Experience", "Mihaly Csikszentmihalyi", "Psychology",
                "How happiness and fulfilment arise from full engagement in challenging activity.",
                "#9d174d", 1);
        addBook("B033", "Influence: The Psychology of Persuasion", "Robert Cialdini", "Psychology",
                "The classic study of the six universal principles that move people to say yes.",
                "#831843", 3);
        addBook("B034", "Man's Search for Meaning", "Viktor Frankl", "Psychology",
                "A psychiatrist's account of survival in Nazi concentration camps and the case for logotherapy.",
                "#9f1239", 1);
        addBook("B035", "Mindset: The New Psychology of Success", "Carol Dweck", "Psychology",
                "How a growth mindset unlocks achievement, contrasted with a fixed mindset.",
                "#a21caf", 2);
        addBook("B036", "Predictably Irrational", "Dan Ariely", "Psychology",
                "How hidden forces shape our everyday decisions in surprising and predictable ways.",
                "#c026d3", 1);
        addBook("B037", "Thinking, Fast and Slow", "Daniel Kahneman", "Psychology",
                "A landmark tour of the two systems that drive human thought: fast intuition and slow reasoning.",
                "#a21caf", 2);
        addBook("B038", "The Power of Habit", "Charles Duhigg", "Psychology",
                "Why habits exist and how they can be changed to transform business and life.",
                "#be185d", 1);

        // ----- Filipino (NEW category) -----
        addBook("B039", "Noli Me Tangere", "Jose Rizal", "Filipino",
                "Rizal's masterpiece exposing the abuses of Spanish colonial rule in the Philippines.",
                "#b91c1c", 3);
        addBook("B040", "El Filibusterismo", "Jose Rizal", "Filipino",
                "The sequel to Noli Me Tangere, following Simoun's campaign of vengeance and reform.",
                "#991b1b", 2);
        addBook("B041", "Ibong Adarna", "Anonymous (Traditional)", "Filipino",
                "A classic Filipino epic about three princes and a magical bird whose song heals the sick.",
                "#dc2626", 2);
        addBook("B042", "Florante at Laura", "Francisco Balagtas", "Filipino",
                "A masterpiece of Tagalog poetry weaving love, justice, and political allegory.",
                "#b45309", 1);
        addBook("B043", "Mga Kwento ni Lola Basyang", "Severino Reyes", "Filipino",
                "Beloved Filipino fairy tales full of magic, morality, and adventure.",
                "#c2410c", 2);
        addBook("B044", "Banaag at Sikat", "Lope K. Santos", "Filipino",
                "A seminal Tagalog novel that helped shape Filipino labor and socialist thought.",
                "#a16207", 1);
    }

    private void addBook(String id, String title, String author, String cat,
                         String desc, String color, int copies) {
        Book b = new Book(id, title, author, cat, desc, color, Book.Status.AVAILABLE,
                copies, copies);
        books.put(id, b);
    }

    // =====================================================================
    //  Session management (very simple - see README for limitations)
    // =====================================================================
    public synchronized String createSession(String studentId) {
        String token = UUID.randomUUID().toString().replace("-", "");
        sessions.put(token, studentId);
        return token;
    }

    public synchronized String studentIdForToken(String token) {
        if (token == null) return null;
        return sessions.get(token);
    }

    public synchronized void destroySession(String token) {
        if (token != null) sessions.remove(token);
    }

    // =====================================================================
    //  Registration / login
    // =====================================================================
    public synchronized Map<String, Object> registerStudent(String studentId, String fullName,
                                                             String phone, boolean agreed) {
        Map<String, Object> res = new LinkedHashMap<>();
        // ---- Field presence checks (none of the 3 may be empty) ----
        if (studentId == null || studentId.trim().isEmpty()) {
            return error(res, "Student ID Number is required.");
        }
        if (fullName == null || fullName.trim().isEmpty()) {
            return error(res, "Full Name is required.");
        }
        if (phone == null || phone.trim().isEmpty()) {
            return error(res, "Phone Number is required.");
        }
        if (!agreed) {
            return error(res, "You must agree to the Terms and Conditions before registering.");
        }

        String id = studentId.trim();

        // ---- Student ID Number must be exactly 8 digits (no letters, no email) ----
        if (!id.matches("\\d{8}")) {
            return error(res, "Student ID Number must be exactly 8 digits "
                    + "(numbers only, e.g. 20231234). Letters, spaces, '@' and other "
                    + "characters are not allowed.");
        }

        // ---- Full Name must include both a given name and a surname ----
        String[] nameParts = fullName.trim().split("\\s+");
        if (nameParts.length < 2) {
            return error(res, "Full Name must include both a first name and a surname. "
                    + "Please enter your complete name as it appears on your school records.");
        }
        for (String np : nameParts) {
            if (np.replace("-", "").replace("'", "").length() < 2
                    || !np.matches("[A-Za-z][A-Za-z'-]*")) {
                return error(res, "Each part of your Full Name must start with a letter "
                        + "and contain only letters, hyphens or apostrophes.");
            }
        }

        if (students.containsKey(id)) {
            return error(res, "A student with ID '" + id + "' is already registered. Please log in instead.");
        }
        Student s = new Student(id, fullName.trim(), phone.trim(), true, nowIso());
        students.put(id, s);
        persist();

        String token = createSession(id);
        res.put("success", true);
        res.put("token", token);
        res.put("studentId", id);
        res.put("fullName", s.getFullName());
        return res;
    }

    public synchronized Map<String, Object> loginStudent(String studentId) {
        Map<String, Object> res = new LinkedHashMap<>();
        if (studentId == null || studentId.trim().isEmpty()) {
            return error(res, "Student ID Number is required.");
        }
        String id = studentId.trim();
        // Apply the same 8-digit format rule on login for clarity.
        if (!id.matches("\\d{8}")) {
            return error(res, "Student ID Number must be exactly 8 digits "
                    + "(numbers only, e.g. 20231234).");
        }
        Student s = students.get(id);
        if (s == null) {
            return error(res, "No account found for Student ID '" + id
                    + "'. Please register first.");
        }
        String token = createSession(id);
        res.put("success", true);
        res.put("token", token);
        res.put("studentId", id);
        res.put("fullName", s.getFullName());
        return res;
    }

    // =====================================================================
    //  Book catalog
    // =====================================================================
    public synchronized List<Map<String, Object>> listBooks(String search, String category) {
        // First, expire any reservations that have overstayed their 1-week window.
        // This refreshes book availability for the catalog view.
        checkAndExpireReservations();

        List<Map<String, Object>> result = new ArrayList<>();
        String q = search == null ? "" : search.trim().toLowerCase();
        String c = category == null ? "" : category.trim().toLowerCase();
        for (Book b : books.values()) {
            if (!q.isEmpty()) {
                String hay = (b.getTitle() + " " + b.getAuthor() + " " + b.getCategory())
                        .toLowerCase();
                if (!hay.contains(q)) continue;
            }
            if (!c.isEmpty() && !"all".equals(c)
                    && !b.getCategory().toLowerCase().equals(c)) {
                continue;
            }
            result.add(bookToView(b));
        }
        // Sort the result alphabetically by title (A-Z), case-insensitive.
        result.sort((a, b1) -> {
            String ta = String.valueOf(a.getOrDefault("title", "")).toLowerCase();
            String tb = String.valueOf(b1.getOrDefault("title", "")).toLowerCase();
            return ta.compareTo(tb);
        });
        return result;
    }

    public synchronized Book getBook(String bookId) {
        return books.get(bookId);
    }

    public synchronized List<String> listCategories() {
        List<String> cats = new ArrayList<>();
        for (Book b : books.values()) {
            if (!cats.contains(b.getCategory())) cats.add(b.getCategory());
        }
        Collections.sort(cats);
        return cats;
    }

    /**
     * Build the JSON-safe view of a book. When the book has at least one
     * active borrowing, the expected return date (i.e. the soonest due
     * date among the active borrowings on this book), whether that
     * borrowing has been extended, and the number of overdue days are
     * also exposed so any user browsing the catalog can see when the
     * book is expected back.
     */
    private Map<String, Object> bookToView(Book b) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("bookId", b.getBookId());
        v.put("title", b.getTitle());
        v.put("author", b.getAuthor());
        v.put("category", b.getCategory());
        v.put("description", b.getDescription());
        v.put("coverColor", b.getCoverColor());
        v.put("status", b.getStatus().name());
        v.put("totalCopies", b.getTotalCopies());
        v.put("availableCopies", b.getAvailableCopies());

        // For BORROWED books, surface the soonest expected return date
        // among the active borrowings on this book, plus extended/overdue info.
        String expectedReturnDate = "";
        boolean isExtended = false;
        int daysOverdue = 0;
        for (BorrowingRecord r : borrowings.values()) {
            if (!r.getBookId().equals(b.getBookId())) continue;
            if (r.getStatus() != BorrowingRecord.Status.ACTIVE) continue;
            // Pick the soonest due date (the one most likely to free up next).
            if (expectedReturnDate.isEmpty() || r.getDueDate().compareTo(expectedReturnDate) < 0) {
                expectedReturnDate = r.getDueDate();
                isExtended = r.isExtended();
                daysOverdue = computeOverdueDays(r, LocalDate.now());
            }
        }
        v.put("expectedReturnDate", expectedReturnDate);
        v.put("isExtended", isExtended);
        v.put("daysOverdue", daysOverdue);
        return v;
    }

    // =====================================================================
    //  Borrowing
    // =====================================================================
    public synchronized Map<String, Object> borrowBook(String studentId, String bookId) {
        Map<String, Object> res = new LinkedHashMap<>();
        Student s = students.get(studentId);
        if (s == null) return error(res, "Student not found.");
        Book b = books.get(bookId);
        if (b == null) return error(res, "Book not found.");

        // Refresh reservation expiries before checking anything else.
        checkAndExpireReservations();

        // Block users who still have unreturned overdue books or unpaid penalties.
        String blockReason = borrowingBlockedReason(studentId);
        if (blockReason != null) return error(res, blockReason);

        // The book must have at least one copy available.
        if (b.getAvailableCopies() <= 0) {
            return error(res, "This book is currently " + b.getStatus().name().toLowerCase()
                    + " and no copies are available for borrowing.");
        }

        // Rule: max 2 ACTIVE borrowings per student. Reservations do NOT
        // count toward this limit because the reserved book has not yet
        // been actually borrowed.
        long activeCount = 0;
        for (BorrowingRecord r : borrowings.values()) {
            if (r.getStudentId().equals(studentId) && r.getStatus() == BorrowingRecord.Status.ACTIVE) {
                activeCount++;
            }
        }
        if (activeCount >= MAX_ACTIVE_BORROWS) {
            return error(res, "You have reached the borrowing limit of "
                    + MAX_ACTIVE_BORROWS + " active books. Please return one before borrowing another.");
        }

        // Create the record + receipt
        String recordId = nextBorrowId();
        String receiptNo = nextReceiptNo();
        String borrowDate = todayIso();
        String dueDate = todayPlusDays(INITIAL_BORROW_DAYS);
        String borrowDateTime = nowIso();

        BorrowingRecord rec = new BorrowingRecord();
        rec.setRecordId(recordId);
        rec.setStudentId(studentId);
        rec.setBookId(bookId);
        rec.setBorrowDate(borrowDate);
        rec.setDueDate(dueDate);
        rec.setExtended(false);
        rec.setReturnDate("");
        rec.setStatus(BorrowingRecord.Status.ACTIVE);
        rec.setDamageCategory("none");
        rec.setDamagePenalty(0);
        rec.setOverduePenalty(0);
        rec.setReceiptNo(receiptNo);
        rec.setLost(false);
        rec.setLostPenalty(0);
        rec.setLostDate("");
        rec.setPaidLost(false);
        borrowings.put(recordId, rec);

        DigitalReceipt receipt = new DigitalReceipt(
                receiptNo, studentId, s.getFullName(), bookId, b.getTitle(),
                b.getAuthor(), borrowDateTime, dueDate,
                INITIAL_BORROW_DAYS + " days", "ACTIVE", "");
        receipts.put(receiptNo, receipt);

        // Decrease available copies. If none are left, mark the book BORROWED.
        b.setAvailableCopies(b.getAvailableCopies() - 1);
        if (b.getAvailableCopies() <= 0) {
            // If there is at least one active reservation on this book, it is
            // considered RESERVED (someone is waiting); otherwise BORROWED.
            boolean hasActiveRes = false;
            for (Reservation rv : reservations.values()) {
                if (rv.getBookId().equals(bookId) && rv.isActive()) {
                    hasActiveRes = true;
                    break;
                }
            }
            b.setStatus(hasActiveRes ? Book.Status.RESERVED : Book.Status.BORROWED);
        }
        persist();

        res.put("success", true);
        res.put("recordId", recordId);
        res.put("receiptNo", receiptNo);
        res.put("dueDate", dueDate);
        res.put("borrowDate", borrowDate);
        res.put("message", "Book borrowed successfully. A digital receipt has been generated.");
        return res;
    }

    public synchronized Map<String, Object> returnBook(String studentId, String recordId,
                                                       String damageCategory) {
        Map<String, Object> res = new LinkedHashMap<>();
        BorrowingRecord rec = borrowings.get(recordId);
        if (rec == null) return error(res, "Borrowing record not found.");
        if (!rec.getStudentId().equals(studentId)) {
            return error(res, "This borrowing record does not belong to your account.");
        }
        if (rec.getStatus() == BorrowingRecord.Status.RETURNED) {
            return error(res, "This book has already been returned.");
        }

        // Damage penalty (configurable in this class)
        String cat = damageCategory == null ? "none" : damageCategory.trim().toLowerCase();
        int damagePenalty = 0;
        switch (cat) {
            case "minor":    damagePenalty = DAMAGE_MINOR_PENALTY;    break;
            case "moderate": damagePenalty = DAMAGE_MODERATE_PENALTY; break;
            case "severe":   damagePenalty = DAMAGE_SEVERE_PENALTY;  break;
            case "":
            case "none":     cat = "none"; damagePenalty = 0;        break;
            default:
                return error(res, "Invalid damage category. Use: none, minor, moderate, severe.");
        }

        // Final overdue penalty (computed on the server)
        int overdueDays = computeOverdueDays(rec, LocalDate.now());
        int overduePenalty = overdueDays * OVERDUE_PENALTY_PER_DAY;

        rec.setStatus(BorrowingRecord.Status.RETURNED);
        rec.setReturnDate(todayIso());
        rec.setDamageCategory(cat);
        rec.setDamagePenalty(damagePenalty);
        rec.setOverduePenalty(overduePenalty);

        // Return one copy to the shelf and refresh the book's status.
        Book b = books.get(rec.getBookId());
        if (b != null) {
            b.setAvailableCopies(Math.min(b.getTotalCopies(), b.getAvailableCopies() + 1));
            // If at least one copy is now available, the book is AVAILABLE.
            // Otherwise, if there is an active reservation, it is RESERVED.
            // Otherwise, it stays BORROWED.
            if (b.getAvailableCopies() > 0) {
                b.setStatus(Book.Status.AVAILABLE);
            } else {
                boolean hasActiveRes = false;
                for (Reservation r : reservations.values()) {
                    if (r.getBookId().equals(rec.getBookId()) && r.isActive()) {
                        hasActiveRes = true;
                        break;
                    }
                }
                b.setStatus(hasActiveRes ? Book.Status.RESERVED : Book.Status.BORROWED);
            }
        }

        // Update the receipt's status & return date (it is NOT deleted)
        DigitalReceipt d = receipts.get(rec.getReceiptNo());
        if (d != null) {
            d.setStatus(overdueDays > 0 ? "RETURNED-OVERDUE" : "RETURNED");
            d.setReturnDate(todayIso());
        }

        persist();
        res.put("success", true);
        res.put("recordId", recordId);
        res.put("returnDate", rec.getReturnDate());
        res.put("damageCategory", cat);
        res.put("damagePenalty", damagePenalty);
        res.put("overdueDays", overdueDays);
        res.put("overduePenalty", overduePenalty);
        res.put("totalPenalty", damagePenalty + overduePenalty);
        res.put("message", "Book returned. "
                + (overdueDays > 0 ? "Overdue penalty applied. " : "")
                + (damagePenalty > 0 ? "Damage penalty applied." : "No damage penalty."));
        return res;
    }

    public synchronized Map<String, Object> extendBorrowing(String studentId, String recordId) {
        Map<String, Object> res = new LinkedHashMap<>();
        BorrowingRecord rec = borrowings.get(recordId);
        if (rec == null) return error(res, "Borrowing record not found.");
        if (!rec.getStudentId().equals(studentId)) {
            return error(res, "This borrowing record does not belong to your account.");
        }
        if (rec.getStatus() != BorrowingRecord.Status.ACTIVE) {
            return error(res, "Only active borrowings can be extended.");
        }
        if (rec.isExtended()) {
            return error(res, "This borrowing has already been extended once. "
                    + "Further extensions are not allowed.");
        }
        // Add EXTENSION_DAYS to the current due date
        LocalDate cur = parseDate(rec.getDueDate());
        if (cur == null) cur = LocalDate.now();
        LocalDate newDue = cur.plusDays(EXTENSION_DAYS);
        rec.setDueDate(newDue.format(DateTimeFormatter.ISO_DATE));
        rec.setExtended(true);

        DigitalReceipt d = receipts.get(rec.getReceiptNo());
        if (d != null) {
            d.setDueDate(rec.getDueDate());
            d.setDuration((INITIAL_BORROW_DAYS + EXTENSION_DAYS) + " days (extended)");
        }
        persist();

        res.put("success", true);
        res.put("recordId", recordId);
        res.put("newDueDate", rec.getDueDate());
        res.put("extended", true);
        res.put("message", "Borrowing extended by " + EXTENSION_DAYS
                + " days. New due date: " + rec.getDueDate());
        return res;
    }

    // =====================================================================
    //  Reservations
    // =====================================================================
    public synchronized Map<String, Object> createReservation(String studentId, String bookId) {
        Map<String, Object> res = new LinkedHashMap<>();
        Student s = students.get(studentId);
        if (s == null) return error(res, "Student not found.");
        Book b = books.get(bookId);
        if (b == null) return error(res, "Book not found.");

        // Refresh reservation expiries first.
        checkAndExpireReservations();

        // Block users with unreturned overdue books or unpaid penalties.
        String blockReason = borrowingBlockedReason(studentId);
        if (blockReason != null) return error(res, blockReason);

        // A reservation can only be made if there are no copies immediately
        // available to walk-in borrowers; otherwise the student should
        // simply borrow the book.  We also allow reserving when the book
        // is currently AVAILABLE but only has 1 copy and the student wants
        // to lock it for later pickup - in that case we still reduce
        // availableCopies by 1.
        if (b.getAvailableCopies() <= 0) {
            return error(res, "Only books with at least one available copy can be reserved. "
                    + "This book is currently " + b.getStatus().name().toLowerCase() + ".");
        }

        // Limit: max 2 active reservations per student.
        long activeResCount = 0;
        for (Reservation r : reservations.values()) {
            if (r.getStudentId().equals(studentId) && r.isActive()) {
                activeResCount++;
            }
        }
        if (activeResCount >= MAX_ACTIVE_RESERVATIONS) {
            return error(res, "You have reached the reservation limit of "
                    + MAX_ACTIVE_RESERVATIONS + " active reservations. "
                    + "Please cancel or borrow one of your existing reservations first.");
        }

        // Prevent same student from reserving the same book twice
        for (Reservation r : reservations.values()) {
            if (r.getStudentId().equals(studentId)
                    && r.getBookId().equals(bookId)
                    && r.isActive()) {
                return error(res, "You have already reserved this book.");
            }
        }

        String reservationId = nextReservationId();
        String expiryDate = todayPlusDays(RESERVATION_EXPIRY_DAYS);
        Reservation r = new Reservation();
        r.setReservationId(reservationId);
        r.setStudentId(studentId);
        r.setBookId(bookId);
        r.setReserveDate(todayIso());
        r.setExpiryDate(expiryDate);
        r.setStatus(Reservation.Status.ACTIVE);
        r.setExpiredPenalty(0);
        r.setPaidExpired(false);
        reservations.put(reservationId, r);

        // A reservation locks one copy. If that drops availableCopies to 0,
        // the book becomes RESERVED.
        b.setAvailableCopies(b.getAvailableCopies() - 1);
        if (b.getAvailableCopies() <= 0) {
            b.setStatus(Book.Status.RESERVED);
        }
        persist();

        res.put("success", true);
        res.put("reservationId", reservationId);
        res.put("bookId", bookId);
        res.put("reserveDate", r.getReserveDate());
        res.put("expiryDate", expiryDate);
        res.put("message", "Book reserved successfully. The reservation will expire on "
                + expiryDate + " (7 days from today).");
        return res;
    }

    public synchronized Map<String, Object> cancelReservation(String studentId, String reservationId) {
        Map<String, Object> res = new LinkedHashMap<>();
        Reservation r = reservations.get(reservationId);
        if (r == null) return error(res, "Reservation not found.");
        if (!r.getStudentId().equals(studentId)) {
            return error(res, "This reservation does not belong to your account.");
        }
        if (r.getStatus() == Reservation.Status.CANCELED) {
            return error(res, "This reservation has already been canceled.");
        }
        if (r.getStatus() == Reservation.Status.COMPLETED) {
            return error(res, "This reservation has already been converted into a borrowing "
                    + "and cannot be canceled.");
        }
        if (r.getStatus() == Reservation.Status.EXPIRED) {
            return error(res, "This reservation has expired and cannot be canceled. "
                    + "Please settle the expiry penalty to regain borrowing privileges.");
        }

        r.setStatus(Reservation.Status.CANCELED);

        // Return the locked copy to the shelf and refresh the book's status.
        Book b = books.get(r.getBookId());
        if (b != null) {
            b.setAvailableCopies(Math.min(b.getTotalCopies(), b.getAvailableCopies() + 1));
            if (b.getAvailableCopies() > 0) {
                b.setStatus(Book.Status.AVAILABLE);
            } else {
                // Other reservations still hold all copies.
                boolean otherActiveRes = false;
                for (Reservation other : reservations.values()) {
                    if (other == r) continue;
                    if (other.getBookId().equals(r.getBookId()) && other.isActive()) {
                        otherActiveRes = true;
                        break;
                    }
                }
                b.setStatus(otherActiveRes ? Book.Status.RESERVED : Book.Status.BORROWED);
            }
        }
        persist();

        res.put("success", true);
        res.put("reservationId", reservationId);
        res.put("newBookStatus", b == null ? "UNKNOWN" : b.getStatus().name());
        res.put("message", "Reservation canceled. The held copy has been returned to the shelf.");
        return res;
    }

    /**
     * Convert one of the student's ACTIVE reservations into a borrowing
     * transaction.  The reservation is marked COMPLETED (it is preserved
     * in history - not deleted), the book becomes BORROWED, and a digital
     * receipt is generated exactly as in a normal borrowing.
     *
     * All standard borrowing rules still apply - including the rule that
     * a student may not borrow a new book while they already have any
     * unreturned book.
     */
    public synchronized Map<String, Object> borrowReservedBook(String studentId,
                                                              String reservationId) {
        Map<String, Object> res = new LinkedHashMap<>();
        Student s = students.get(studentId);
        if (s == null) return error(res, "Student not found.");
        Reservation r = reservations.get(reservationId);
        if (r == null) return error(res, "Reservation not found.");
        if (!r.getStudentId().equals(studentId)) {
            return error(res, "This reservation does not belong to your account.");
        }
        if (r.getStatus() != Reservation.Status.ACTIVE) {
            return error(res, "Only ACTIVE reservations can be converted into a borrowing. "
                    + "This reservation is " + r.getStatus().name() + ".");
        }

        // Block users with unreturned overdue books or unpaid penalties.
        String blockReason = borrowingBlockedReason(studentId);
        if (blockReason != null) return error(res, blockReason);

        // Max 2 active borrowings per student (reservations don't count
        // until they are converted into a borrowing).
        long activeCount = 0;
        for (BorrowingRecord rec : borrowings.values()) {
            if (rec.getStudentId().equals(studentId)
                    && rec.getStatus() == BorrowingRecord.Status.ACTIVE) {
                activeCount++;
            }
        }
        if (activeCount >= MAX_ACTIVE_BORROWS) {
            return error(res, "You have reached the borrowing limit of "
                    + MAX_ACTIVE_BORROWS + " active books. Please return one before "
                    + "converting another reservation into a borrowing.");
        }

        Book b = books.get(r.getBookId());
        if (b == null) return error(res, "Book no longer exists.");
        // The reservation locked one copy; that copy now becomes a borrowing.
        // availableCopies does not change here (it was already reduced when
        // the reservation was created).  We just need to make sure the
        // book's status is BORROWED if no more copies are available.
        if (b.getAvailableCopies() > 0) {
            // Edge case: the reservation's locked copy needs to come from
            // availableCopies, not the locked count.  We decrement it now.
            b.setAvailableCopies(b.getAvailableCopies() - 1);
        }

        // Create the borrowing + receipt (same flow as borrowBook).
        String recordId = nextBorrowId();
        String receiptNo = nextReceiptNo();
        String borrowDate = todayIso();
        String dueDate = todayPlusDays(INITIAL_BORROW_DAYS);
        String borrowDateTime = nowIso();

        BorrowingRecord rec = new BorrowingRecord();
        rec.setRecordId(recordId);
        rec.setStudentId(studentId);
        rec.setBookId(r.getBookId());
        rec.setBorrowDate(borrowDate);
        rec.setDueDate(dueDate);
        rec.setExtended(false);
        rec.setReturnDate("");
        rec.setStatus(BorrowingRecord.Status.ACTIVE);
        rec.setDamageCategory("none");
        rec.setDamagePenalty(0);
        rec.setOverduePenalty(0);
        rec.setReceiptNo(receiptNo);
        rec.setLost(false);
        rec.setLostPenalty(0);
        rec.setLostDate("");
        rec.setPaidLost(false);
        borrowings.put(recordId, rec);

        DigitalReceipt receipt = new DigitalReceipt(
                receiptNo, studentId, s.getFullName(), r.getBookId(), b.getTitle(),
                b.getAuthor(), borrowDateTime, dueDate,
                INITIAL_BORROW_DAYS + " days", "ACTIVE", "");
        receipts.put(receiptNo, receipt);

        // Mark the reservation as COMPLETED (it stays in history).
        r.setStatus(Reservation.Status.COMPLETED);
        // The book stays in its current status (BORROWED or RESERVED) since
        // availableCopies is unchanged - the locked copy just moved from
        // "reserved" to "borrowed".
        if (b.getAvailableCopies() <= 0) {
            boolean hasOtherActiveRes = false;
            for (Reservation rv : reservations.values()) {
                if (rv == r) continue;
                if (rv.getBookId().equals(r.getBookId()) && rv.isActive()) {
                    hasOtherActiveRes = true;
                    break;
                }
            }
            b.setStatus(hasOtherActiveRes ? Book.Status.RESERVED : Book.Status.BORROWED);
        }
        persist();

        res.put("success", true);
        res.put("recordId", recordId);
        res.put("receiptNo", receiptNo);
        res.put("dueDate", dueDate);
        res.put("borrowDate", borrowDate);
        res.put("reservationId", reservationId);
        res.put("message", "Reservation converted to borrowing. A digital receipt has been generated.");
        return res;
    }

    // =====================================================================
    //  Profile / history queries
    // =====================================================================
    public synchronized Map<String, Object> getProfile(String studentId) {
        Map<String, Object> res = new LinkedHashMap<>();
        Student s = students.get(studentId);
        if (s == null) {
            res.put("success", false);
            res.put("message", "Student not found.");
            return res;
        }
        res.put("success", true);
        res.put("studentId", s.getStudentId());
        res.put("fullName", s.getFullName());
        res.put("phone", s.getPhone());
        res.put("registeredAt", s.getRegisteredAt());
        res.put("activeBorrowings", countActiveBorrowings(studentId));
        res.put("activeReservations", countActiveReservations(studentId));
        res.put("maxBorrowings", MAX_ACTIVE_BORROWS);
        res.put("maxReservations", MAX_ACTIVE_RESERVATIONS);
        // Expose any blocking reason so the UI can warn the student.
        String block = borrowingBlockedReason(studentId);
        res.put("blocked", block != null);
        res.put("blockReason", block == null ? "" : block);
        return res;
    }

    public synchronized List<Map<String, Object>> getBorrowedBooks(String studentId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (BorrowingRecord r : borrowings.values()) {
            if (!r.getStudentId().equals(studentId)) continue;
            if (r.getStatus() != BorrowingRecord.Status.ACTIVE) continue;
            out.add(recordToView(r));
        }
        return out;
    }

    public synchronized List<Map<String, Object>> getReservations(String studentId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Reservation r : reservations.values()) {
            if (!r.getStudentId().equals(studentId)) continue;
            out.add(reservationToView(r));
        }
        return out;
    }

    public synchronized List<Map<String, Object>> getTransactionHistory(String studentId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (BorrowingRecord r : borrowings.values()) {
            if (!r.getStudentId().equals(studentId)) continue;
            out.add(recordToView(r));
        }
        for (Reservation r : reservations.values()) {
            if (!r.getStudentId().equals(studentId)) continue;
            out.add(reservationToView(r));
        }
        return out;
    }

    public synchronized List<Map<String, Object>> getReceipts(String studentId) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (DigitalReceipt d : receipts.values()) {
            if (!d.getStudentId().equals(studentId)) continue;
            out.add(receiptToView(d));
        }
        return out;
    }

    public synchronized Map<String, Object> getReceipt(String studentId, String receiptNo) {
        Map<String, Object> res = new LinkedHashMap<>();
        DigitalReceipt d = receipts.get(receiptNo);
        if (d == null) return error(res, "Receipt not found.");
        if (!d.getStudentId().equals(studentId)) {
            return error(res, "This receipt does not belong to your account.");
        }
        res.put("success", true);
        res.put("receipt", receiptToView(d));
        return res;
    }

    public synchronized List<Map<String, Object>> getPenalties(String studentId) {
        // Refresh reservation expiries so the penalty list is up to date.
        checkAndExpireReservations();
        List<Map<String, Object>> out = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (BorrowingRecord r : borrowings.values()) {
            if (!r.getStudentId().equals(studentId)) continue;
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("type", "borrowing");
            p.put("recordId", r.getRecordId());
            Book b = books.get(r.getBookId());
            p.put("bookTitle", b == null ? r.getBookId() : b.getTitle());
            p.put("dueDate", r.getDueDate());
            p.put("returnDate", r.getReturnDate());
            p.put("status", r.getStatus().name());

            int overdueDays;
            int overduePenalty;
            if (r.getStatus() == BorrowingRecord.Status.RETURNED
                    || r.getStatus() == BorrowingRecord.Status.LOST) {
                overdueDays = r.getOverduePenalty() / OVERDUE_PENALTY_PER_DAY;
                overduePenalty = r.getOverduePenalty();
            } else {
                overdueDays = computeOverdueDays(r, today);
                overduePenalty = overdueDays * OVERDUE_PENALTY_PER_DAY;
            }
            p.put("overdueDays", overdueDays);
            p.put("overduePenalty", overduePenalty);
            p.put("damageCategory", r.getDamageCategory());
            p.put("damagePenalty", r.getDamagePenalty());

            // Lost-book penalty (separate line item)
            p.put("lost", r.isLost());
            p.put("lostPenalty", r.getLostPenalty());
            p.put("lostDate", r.getLostDate() == null ? "" : r.getLostDate());
            p.put("lostPaid", r.isPaidLost());

            int total = overduePenalty + r.getDamagePenalty() + (r.isLost() ? r.getLostPenalty() : 0);
            p.put("totalPenalty", total);
            // A penalty is "paid" only if there are no unpaid lost-book charges
            // (overdue / damage penalties are settled implicitly when the book
            // is returned; we never claim they are paid in cash).
            p.put("paid", !r.isLost() || r.isPaidLost());
            p.put("canPay", r.isLost() && !r.isPaidLost());
            out.add(p);
        }
        // Expired reservations appear as their own penalty entries.
        for (Reservation r : reservations.values()) {
            if (!r.getStudentId().equals(studentId)) continue;
            if (r.getStatus() != Reservation.Status.EXPIRED) continue;
            Map<String, Object> p = new LinkedHashMap<>();
            Book b = books.get(r.getBookId());
            p.put("type", "expired-reservation");
            p.put("reservationId", r.getReservationId());
            p.put("bookTitle", b == null ? r.getBookId() : b.getTitle());
            p.put("reserveDate", r.getReserveDate());
            p.put("expiryDate", r.getExpiryDate() == null ? "" : r.getExpiryDate());
            p.put("status", r.getStatus().name());
            p.put("expiredPenalty", r.getExpiredPenalty());
            p.put("totalPenalty", r.getExpiredPenalty());
            p.put("paid", r.isPaidExpired());
            p.put("canPay", !r.isPaidExpired());
            out.add(p);
        }
        return out;
    }

    // =====================================================================
    //  View mappers
    // =====================================================================
    private Map<String, Object> recordToView(BorrowingRecord r) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("recordId", r.getRecordId());
        v.put("studentId", r.getStudentId());
        v.put("bookId", r.getBookId());
        Book b = books.get(r.getBookId());
        v.put("bookTitle", b == null ? r.getBookId() : b.getTitle());
        v.put("bookAuthor", b == null ? "" : b.getAuthor());
        v.put("borrowDate", r.getBorrowDate());
        v.put("dueDate", r.getDueDate());
        v.put("extended", r.isExtended());
        v.put("returnDate", r.getReturnDate());
        v.put("status", r.getStatus().name());
        v.put("damageCategory", r.getDamageCategory());
        v.put("damagePenalty", r.getDamagePenalty());
        v.put("receiptNo", r.getReceiptNo());
        v.put("lost", r.isLost());
        v.put("lostPenalty", r.getLostPenalty());
        v.put("lostDate", r.getLostDate() == null ? "" : r.getLostDate());
        v.put("lostPaid", r.isPaidLost());

        // Live overdue flag for active records
        if (r.getStatus() == BorrowingRecord.Status.ACTIVE) {
            int od = computeOverdueDays(r, LocalDate.now());
            v.put("overdueDays", od);
            v.put("overduePenalty", od * OVERDUE_PENALTY_PER_DAY);
            v.put("computedStatus", od > 0 ? "OVERDUE" : "ACTIVE");
        } else {
            v.put("overdueDays", r.getOverduePenalty() / OVERDUE_PENALTY_PER_DAY);
            v.put("overduePenalty", r.getOverduePenalty());
            v.put("computedStatus", r.getStatus().name());
        }
        return v;
    }

    private Map<String, Object> reservationToView(Reservation r) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("reservationId", r.getReservationId());
        v.put("studentId", r.getStudentId());
        v.put("bookId", r.getBookId());
        Book b = books.get(r.getBookId());
        v.put("bookTitle", b == null ? r.getBookId() : b.getTitle());
        v.put("bookAuthor", b == null ? "" : b.getAuthor());
        v.put("reserveDate", r.getReserveDate());
        v.put("expiryDate", r.getExpiryDate() == null ? "" : r.getExpiryDate());
        v.put("status", r.getStatus().name());
        v.put("expiredPenalty", r.getExpiredPenalty());
        v.put("expiredPaid", r.isPaidExpired());
        return v;
    }

    private Map<String, Object> receiptToView(DigitalReceipt d) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("receiptNo", d.getReceiptNo());
        v.put("studentId", d.getStudentId());
        v.put("studentName", d.getStudentName());
        v.put("bookId", d.getBookId());
        v.put("bookTitle", d.getBookTitle());
        v.put("author", d.getAuthor());
        v.put("borrowDateTime", d.getBorrowDateTime());
        v.put("dueDate", d.getDueDate());
        v.put("duration", d.getDuration());
        v.put("status", d.getStatus());
        v.put("returnDate", d.getReturnDate());
        return v;
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    /**
     * Auto-expire any ACTIVE reservation whose 7-day window has elapsed.
     * Each expired reservation is marked EXPIRED, the held copy is returned
     * to the shelf, and the owning student is charged a penalty that
     * blocks future borrowing / reservation until it is paid.
     */
    public synchronized void checkAndExpireReservations() {
        LocalDate today = LocalDate.now();
        boolean changed = false;
        for (Reservation r : reservations.values()) {
            if (r.getStatus() != Reservation.Status.ACTIVE) continue;
            LocalDate expiry = parseDate(r.getExpiryDate());
            if (expiry == null) {
                // Backfill expiry date for old records.
                LocalDate created = parseDate(r.getReserveDate());
                if (created == null) created = today;
                expiry = created.plusDays(RESERVATION_EXPIRY_DAYS);
                r.setExpiryDate(expiry.format(DateTimeFormatter.ISO_DATE));
            }
            if (today.isAfter(expiry)) {
                r.setStatus(Reservation.Status.EXPIRED);
                r.setExpiredPenalty(EXPIRED_RESERVATION_PENALTY);
                r.setPaidExpired(false);
                // Return the locked copy.
                Book b = books.get(r.getBookId());
                if (b != null) {
                    b.setAvailableCopies(Math.min(b.getTotalCopies(), b.getAvailableCopies() + 1));
                    if (b.getAvailableCopies() > 0) {
                        b.setStatus(Book.Status.AVAILABLE);
                    } else {
                        boolean otherActiveRes = false;
                        for (Reservation other : reservations.values()) {
                            if (other == r) continue;
                            if (other.getBookId().equals(r.getBookId()) && other.isActive()) {
                                otherActiveRes = true;
                                break;
                            }
                        }
                        b.setStatus(otherActiveRes ? Book.Status.RESERVED : Book.Status.BORROWED);
                    }
                }
                changed = true;
            }
        }
        if (changed) persist();
    }

    /**
     * Returns a non-null reason string if the student is currently blocked
     * from borrowing / reserving, otherwise null.  A student is blocked
     * when they have:
     *   - any active borrowing that is overdue (book not yet returned), OR
     *   - any unpaid lost-book penalty, OR
     *   - any unpaid expired-reservation penalty.
     */
    public synchronized String borrowingBlockedReason(String studentId) {
        // 1. Active overdue borrowings
        for (BorrowingRecord r : borrowings.values()) {
            if (!r.getStudentId().equals(studentId)) continue;
            if (r.getStatus() == BorrowingRecord.Status.ACTIVE) {
                int od = computeOverdueDays(r, LocalDate.now());
                if (od > 0) {
                    return "You have an overdue book ('"
                            + (books.get(r.getBookId()) == null ? r.getBookId()
                                : books.get(r.getBookId()).getTitle())
                            + "') that is " + od + " day(s) overdue. "
                            + "Please return it and settle the penalty before borrowing or reserving again.";
                }
            }
        }
        // 2. Unpaid lost-book penalty
        for (BorrowingRecord r : borrowings.values()) {
            if (!r.getStudentId().equals(studentId)) continue;
            if (r.isLost() && !r.isPaidLost()) {
                return "You have an unpaid lost-book penalty of \u20B1"
                        + r.getLostPenalty()
                        + ". Please settle it before borrowing or reserving again.";
            }
        }
        // 3. Unpaid expired-reservation penalty
        for (Reservation r : reservations.values()) {
            if (!r.getStudentId().equals(studentId)) continue;
            if (r.getStatus() == Reservation.Status.EXPIRED && !r.isPaidExpired()) {
                return "You have an unpaid expired-reservation penalty of \u20B1"
                        + r.getExpiredPenalty()
                        + ". Please settle it before borrowing or reserving again.";
            }
        }
        return null;
    }

    /**
     * Mark a borrowed book as lost.  The borrowing record is closed, the
     * borrower is charged a fixed lost-book penalty (P1000), and the
     * book's totalCopies is decreased by one (the copy is gone).
     * The student cannot borrow or reserve anything else until the
     * lost-book penalty is paid via {@link #payPenalty}.
     */
    public synchronized Map<String, Object> markBookAsLost(String studentId, String recordId) {
        Map<String, Object> res = new LinkedHashMap<>();
        BorrowingRecord rec = borrowings.get(recordId);
        if (rec == null) return error(res, "Borrowing record not found.");
        if (!rec.getStudentId().equals(studentId)) {
            return error(res, "This borrowing record does not belong to your account.");
        }
        if (rec.getStatus() == BorrowingRecord.Status.RETURNED) {
            return error(res, "This book has already been returned.");
        }
        if (rec.isLost()) {
            return error(res, "This book has already been reported as lost.");
        }
        rec.setLost(true);
        rec.setLostPenalty(LOST_BOOK_PENALTY);
        rec.setLostDate(todayIso());
        rec.setStatus(BorrowingRecord.Status.LOST);
        rec.setReturnDate("");

        // The lost copy is removed from the library's inventory.
        Book b = books.get(rec.getBookId());
        if (b != null) {
            b.setTotalCopies(Math.max(0, b.getTotalCopies() - 1));
            // availableCopies was already 0 for this borrower's copy (or
            // reduced when the book was borrowed).  We do not increase it.
            if (b.getAvailableCopies() > 0) {
                b.setStatus(Book.Status.AVAILABLE);
            } else {
                boolean hasActiveRes = false;
                for (Reservation rv : reservations.values()) {
                    if (rv.getBookId().equals(rec.getBookId()) && rv.isActive()) {
                        hasActiveRes = true;
                        break;
                    }
                }
                b.setStatus(hasActiveRes ? Book.Status.RESERVED : Book.Status.BORROWED);
            }
        }
        // Update the receipt status to LOST.
        DigitalReceipt d = receipts.get(rec.getReceiptNo());
        if (d != null) {
            d.setStatus("LOST");
            d.setReturnDate("");
        }
        persist();

        res.put("success", true);
        res.put("recordId", recordId);
        res.put("lostPenalty", LOST_BOOK_PENALTY);
        res.put("lostDate", rec.getLostDate());
        res.put("message", "Book reported as lost. A penalty of \u20B1" + LOST_BOOK_PENALTY
                + " has been applied to your account. You must settle this penalty "
                + "before you can borrow or reserve another book.");
        return res;
    }

    /**
     * Settle (pay) one penalty.  Accepts a "type" of "lost" or "expired"
     * plus the related record id.  Marks the matching penalty as paid.
     * This is a simulation of payment - no real money changes hands.
     */
    public synchronized Map<String, Object> payPenalty(String studentId, String type,
                                                       String relatedId) {
        Map<String, Object> res = new LinkedHashMap<>();
        String t = type == null ? "" : type.trim().toLowerCase();
        if ("lost".equals(t)) {
            BorrowingRecord rec = borrowings.get(relatedId);
            if (rec == null) return error(res, "Borrowing record not found.");
            if (!rec.getStudentId().equals(studentId)) {
                return error(res, "This penalty does not belong to your account.");
            }
            if (!rec.isLost()) return error(res, "This borrowing record has no lost-book penalty.");
            if (rec.isPaidLost()) return error(res, "This lost-book penalty has already been paid.");
            rec.setPaidLost(true);
            persist();
            res.put("success", true);
            res.put("type", "lost");
            res.put("amount", rec.getLostPenalty());
            res.put("message", "Lost-book penalty of \u20B1" + rec.getLostPenalty() + " settled.");
            return res;
        }
        if ("expired".equals(t)) {
            Reservation r = reservations.get(relatedId);
            if (r == null) return error(res, "Reservation not found.");
            if (!r.getStudentId().equals(studentId)) {
                return error(res, "This penalty does not belong to your account.");
            }
            if (r.getStatus() != Reservation.Status.EXPIRED) {
                return error(res, "This reservation has no expiry penalty.");
            }
            if (r.isPaidExpired()) return error(res, "This expiry penalty has already been paid.");
            r.setPaidExpired(true);
            persist();
            res.put("success", true);
            res.put("type", "expired");
            res.put("amount", r.getExpiredPenalty());
            res.put("message", "Expired-reservation penalty of \u20B1"
                    + r.getExpiredPenalty() + " settled.");
            return res;
        }
        return error(res, "Unknown penalty type. Use 'lost' or 'expired'.");
    }

    private long countActiveBorrowings(String studentId) {
        long n = 0;
        for (BorrowingRecord r : borrowings.values()) {
            if (r.getStudentId().equals(studentId)
                    && r.getStatus() == BorrowingRecord.Status.ACTIVE) n++;
        }
        return n;
    }

    private long countActiveReservations(String studentId) {
        long n = 0;
        for (Reservation r : reservations.values()) {
            if (r.getStudentId().equals(studentId) && r.isActive()) n++;
        }
        return n;
    }

    /**
     * Compute completed overdue days. A book that becomes one full day
     * overdue incurs P10. Days are counted from the day AFTER the due
     * date up to (and including) today.
     */
    private int computeOverdueDays(BorrowingRecord r, LocalDate today) {
        LocalDate due = parseDate(r.getDueDate());
        if (due == null) return 0;
        if (!today.isAfter(due)) return 0;
        long days = ChronoUnit.DAYS.between(due, today);
        return days > 0 ? (int) days : 0;
    }

    private LocalDate parseDate(String iso) {
        if (iso == null || iso.isEmpty()) return null;
        try { return LocalDate.parse(iso.trim(), DateTimeFormatter.ISO_DATE); }
        catch (Exception e) { return null; }
    }

    private static String todayIso() {
        return LocalDate.now().format(DateTimeFormatter.ISO_DATE);
    }

    private static String todayPlusDays(int days) {
        return LocalDate.now().plusDays(days).format(DateTimeFormatter.ISO_DATE);
    }

    private static String nowIso() {
        return LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss"));
    }

    private synchronized String nextBorrowId() {
        borrowSeq++;
        return String.format("BRW-%05d", borrowSeq);
    }

    private synchronized String nextReceiptNo() {
        receiptSeq++;
        // Embedding a date prefix + persistent counter guarantees uniqueness
        // across application restarts.
        return String.format("RCP-%s-%05d",
                LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")),
                receiptSeq);
    }

    private synchronized String nextReservationId() {
        reservationSeq++;
        return String.format("RSV-%05d", reservationSeq);
    }

    private static Map<String, Object> error(Map<String, Object> res, String msg) {
        res.put("success", false);
        res.put("message", msg);
        return res;
    }
}
