/**
 * FileStorage.java
 *
 * Persistent plain-text storage for the Online Library System.
 *
 * Storage file: data/library-data.txt
 * Format      : pipe-delimited records grouped under section headers.
 *
 *   #STUDENT
 *   STUDENT|studentId|fullName|phone|agreedTerms|registeredAt
 *
 *   #BOOK
 *   BOOK|bookId|title|author|category|description|coverColor|status
 *
 *   #BORROW
 *   BORROW|recordId|studentId|bookId|borrowDate|dueDate|extended|returnDate|status|damageCategory|damagePenalty|overduePenalty|receiptNo
 *
 *   #RESERVE
 *   RESERVE|reservationId|studentId|bookId|reserveDate|status
 *
 *   #RECEIPT
 *   RECEIPT|receiptNo|studentId|studentName|bookId|bookTitle|author|borrowDateTime|dueDate|duration|status|returnDate
 *
 *   #COUNTERS
 *   COUNTER|borrowSeq|receiptSeq|reservationSeq
 *
 * Special characters in field values are escaped so that the file can be
 * safely split on the pipe character and reloaded reliably:
 *
 *   \  ->  \\        (backslash first to avoid double-escaping)
 *   |  ->  \p
 *   \n ->  \n  (literal backslash + 'n' for newlines inside fields)
 *   \r ->  \r  (literal backslash + 'r' for carriage returns)
 *
 * Writes are atomic: data is written to <file>.tmp and then renamed to
 * the final path, so a crashed write cannot corrupt the existing file.
 */
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class FileStorage {

    public static final String DATA_FILE = "data" + File.separator + "library-data.txt";
    public static final String TMP_FILE  = "data" + File.separator + "library-data.tmp";

    // ---------------------------------------------------------------- escape
    /** Escape a field value so it can safely be stored between pipe characters. */
    public static String escape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length() + 8);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': sb.append("\\\\"); break;
                case '|':  sb.append("\\p");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                default:   sb.append(c);
            }
        }
        return sb.toString();
    }

    /** Reverse of {@link #escape}. */
    public static String unescape(String s) {
        if (s == null) return "";
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                switch (n) {
                    case '\\': sb.append('\\'); i += 2; break;
                    case 'p':  sb.append('|');  i += 2; break;
                    case 'n':  sb.append('\n'); i += 2; break;
                    case 'r':  sb.append('\r'); i += 2; break;
                    default:   sb.append(c); i++; break;
                }
            } else {
                sb.append(c);
                i++;
            }
        }
        return sb.toString();
    }

    /** Split a stored record line on the unescaped pipe delimiter. */
    public static String[] splitRecord(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int i = 0;
        while (i < line.length()) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                char n = line.charAt(i + 1);
                if (n == '\\' || n == 'p' || n == 'n' || n == 'r') {
                    sb.append(c).append(n);
                    i += 2;
                    continue;
                }
            }
            if (c == '|') {
                parts.add(unescape(sb.toString()));
                sb.setLength(0);
                i++;
            } else {
                sb.append(c);
                i++;
            }
        }
        parts.add(unescape(sb.toString()));
        return parts.toArray(new String[0]);
    }

    // ----------------------------------------------------------------- write
    /**
     * Atomic write: build the full file content in memory then write to a
     * temp file and rename. The existing file is left untouched if the
     * write fails for any reason.
     */
    public synchronized void saveAll(StringBuilder content) throws IOException {
        File dataDir = new File("data");
        if (!dataDir.exists() && !dataDir.mkdirs()) {
            throw new IOException("Could not create data directory: " + dataDir.getAbsolutePath());
        }
        File tmp = new File(TMP_FILE);
        File dst = new File(DATA_FILE);

        try (BufferedWriter w = new BufferedWriter(
                new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8))) {
            w.write(content.toString());
            w.flush();
        }
        // Atomic rename (on most filesystems) replaces the destination.
        if (dst.exists() && !dst.delete()) {
            throw new IOException("Could not remove old data file: " + dst.getAbsolutePath());
        }
        if (!tmp.renameTo(dst)) {
            throw new IOException("Could not rename " + tmp.getAbsolutePath()
                    + " to " + dst.getAbsolutePath());
        }
    }

    // ------------------------------------------------------------------ read
    /**
     * Read every non-empty, non-comment line of the data file.
     * Returns an empty list if the file does not yet exist (first run).
     */
    public synchronized List<String> readAllLines() throws IOException {
        List<String> lines = new ArrayList<>();
        File f = new File(DATA_FILE);
        if (!f.exists()) return lines;
        try (BufferedReader r = new BufferedReader(
                new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                lines.add(line);
            }
        }
        return lines;
    }

    /** True if the data file currently exists on disk. */
    public boolean exists() {
        return new File(DATA_FILE).exists();
    }
}
