/**
 * LibraryServer.java
 *
 * Main entry point. Starts a tiny HTTP server using only the built-in
 * `com.sun.net.httpserver.HttpServer` class (no Spring Boot, no external
 * libraries).
 *
 * Routes:
 *   GET  /                       -> public/index.html
 *   GET  /style.css              -> public/style.css
 *   GET  /app.js                 -> public/app.js
 *   POST /api/register           -> create a student account
 *   POST /api/login              -> log in (returns a session token)
 *   POST /api/logout             -> end the current session
 *   GET  /api/books              -> list / search / filter books
 *   GET  /api/categories         -> list distinct categories
 *   GET  /api/profile            -> current student profile
 *   GET  /api/borrowed           -> current active borrowings
 *   GET  /api/reservations       -> all reservations for the student
 *   GET  /api/history            -> full transaction history
 *   GET  /api/receipts           -> all receipts for the student
 *   GET  /api/receipt?id=...     -> single receipt (must belong to caller)
 *   GET  /api/penalties          -> all penalties for the student
 *   POST /api/borrow              -> borrow a book
 *   POST /api/return             -> return a book (optionally with damage)
 *   POST /api/extend             -> extend a borrowing once
 *   POST /api/reserve            -> reserve an available book
 *   POST /api/cancel-reservation -> cancel an active reservation
 *   POST /api/borrow-reserved    -> convert an active reservation into a borrowing
 *   POST /api/lost               -> report a borrowed book as lost (P1000 penalty)
 *   POST /api/pay-penalty        -> settle (pay) a lost or expired-reservation penalty
 *
 * Request / response bodies are JSON for convenience, but the persistent
 * storage file (data/library-data.txt) is plain pipe-delimited text - JSON
 * is NEVER used as the storage format.
 *
 * NOTE on authentication: the simple in-memory token returned by /api/login
 * is NOT a secure authentication system.  Any client that knows a Student
 * ID can log in.  See the README for the full list of limitations and why
 * this is acceptable for a college demonstration.
 *
 * Run:
 *   javac -d out src\*.java
 *   java -cp out LibraryServer
 * Then open http://localhost:2350 in a browser.
 */
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Executors;

public class LibraryServer {

    private static final int PORT = 2350;
    private static final String PUBLIC_DIR = "src/web";

    private static final FileStorage storage = new FileStorage();
    private static final LibraryService service = new LibraryService(storage);

    public static void main(String[] args) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);

        // API endpoints
        server.createContext("/api/register",           wrap(LibraryServer::handleRegister));
        server.createContext("/api/login",              wrap(LibraryServer::handleLogin));
        server.createContext("/api/logout",            wrap(LibraryServer::handleLogout));
        server.createContext("/api/books",             wrap(LibraryServer::handleBooks));
        server.createContext("/api/categories",       wrap(LibraryServer::handleCategories));
        server.createContext("/api/profile",           wrap(LibraryServer::handleProfile));
        server.createContext("/api/borrowed",          wrap(LibraryServer::handleBorrowed));
        server.createContext("/api/reservations",      wrap(LibraryServer::handleReservations));
        server.createContext("/api/history",           wrap(LibraryServer::handleHistory));
        server.createContext("/api/receipts",          wrap(LibraryServer::handleReceipts));
        server.createContext("/api/receipt",           wrap(LibraryServer::handleReceipt));
        server.createContext("/api/penalties",         wrap(LibraryServer::handlePenalties));
        server.createContext("/api/borrow",            wrap(LibraryServer::handleBorrow));
        server.createContext("/api/return",            wrap(LibraryServer::handleReturn));
        server.createContext("/api/extend",            wrap(LibraryServer::handleExtend));
        server.createContext("/api/reserve",           wrap(LibraryServer::handleReserve));
        server.createContext("/api/cancel-reservation",wrap(LibraryServer::handleCancelReservation));
        server.createContext("/api/borrow-reserved",   wrap(LibraryServer::handleBorrowReserved));
        server.createContext("/api/lost",              wrap(LibraryServer::handleLost));
        server.createContext("/api/pay-penalty",       wrap(LibraryServer::handlePayPenalty));

        // Static files
        server.createContext("/", new StaticHandler());

        server.setExecutor(Executors.newFixedThreadPool(8));
        server.start();

        System.out.println("===========================================================");
        System.out.println("  Online Library System");
        System.out.println("  Server running at http://localhost:" + PORT);
        System.out.println("  Press Ctrl+C in this window to stop the server.");
        System.out.println("===========================================================");
    }

    // ---------------------------------------------------------------------
    //  Small handler wrapper - frees each handler from try/catch boilerplate
    // ---------------------------------------------------------------------
    private static HttpHandler wrap(ApiHandler h) {
        return ex -> {
            try {
                if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                    addCorsHeaders(ex);
                    ex.sendResponseHeaders(204, -1);
                    return;
                }
                h.handle(ex);
            } catch (Exception e) {
                sendJson(ex, 500, jsonError("Internal server error: " + e.getMessage()));
            } finally {
                ex.close();
            }
        };
    }

    @FunctionalInterface
    private interface ApiHandler {
        void handle(HttpExchange ex) throws IOException;
    }

    // =====================================================================
    //  API handlers
    // =====================================================================

    // ---- registration ----------------------------------------------------
    private static void handleRegister(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, jsonError("Use POST."));
            return;
        }
        Map<String, String> body = parseFormBody(readBody(ex));
        boolean agreed = "true".equalsIgnoreCase(body.get("agreeTerms"))
                || "on".equalsIgnoreCase(body.get("agreeTerms"))
                || "1".equals(body.get("agreeTerms"));
        Map<String, Object> res = service.registerStudent(
                body.get("studentId"), body.get("fullName"),
                body.get("phone"), agreed);
        sendJson(ex, res.containsKey("success") && (boolean) res.get("success") ? 200 : 400,
                JsonUtil.toJson(res));
    }

    // ---- login -----------------------------------------------------------
    private static void handleLogin(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, jsonError("Use POST."));
            return;
        }
        Map<String, String> body = parseFormBody(readBody(ex));
        Map<String, Object> res = service.loginStudent(body.get("studentId"));
        sendJson(ex, res.containsKey("success") && (boolean) res.get("success") ? 200 : 400,
                JsonUtil.toJson(res));
    }

    // ---- logout ----------------------------------------------------------
    private static void handleLogout(HttpExchange ex) throws IOException {
        if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, jsonError("Use POST."));
            return;
        }
        String token = tokenFrom(ex);
        service.destroySession(token);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("message", "Signed out.");
        sendJson(ex, 200, JsonUtil.toJson(res));
    }

    // ---- books -----------------------------------------------------------
    private static void handleBooks(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, jsonError("Use GET."));
            return;
        }
        Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
        var books = service.listBooks(q.get("search"), q.get("category"));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("count", books.size());
        res.put("books", books);
        sendJson(ex, 200, JsonUtil.toJson(res));
    }

    private static void handleCategories(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            sendJson(ex, 405, jsonError("Use GET."));
            return;
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("categories", service.listCategories());
        sendJson(ex, 200, JsonUtil.toJson(res));
    }

    // ---- profile / student-scoped reads ---------------------------------
    private static void handleProfile(HttpExchange ex) throws IOException {
        String sid = requireStudent(ex);
        if (sid == null) return;
        sendJson(ex, 200, JsonUtil.toJson(service.getProfile(sid)));
    }

    private static void handleBorrowed(HttpExchange ex) throws IOException {
        String sid = requireStudent(ex);
        if (sid == null) return;
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("borrowed", service.getBorrowedBooks(sid));
        sendJson(ex, 200, JsonUtil.toJson(res));
    }

    private static void handleReservations(HttpExchange ex) throws IOException {
        String sid = requireStudent(ex);
        if (sid == null) return;
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("reservations", service.getReservations(sid));
        sendJson(ex, 200, JsonUtil.toJson(res));
    }

    private static void handleHistory(HttpExchange ex) throws IOException {
        String sid = requireStudent(ex);
        if (sid == null) return;
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("history", service.getTransactionHistory(sid));
        sendJson(ex, 200, JsonUtil.toJson(res));
    }

    private static void handleReceipts(HttpExchange ex) throws IOException {
        String sid = requireStudent(ex);
        if (sid == null) return;
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("receipts", service.getReceipts(sid));
        sendJson(ex, 200, JsonUtil.toJson(res));
    }

    private static void handleReceipt(HttpExchange ex) throws IOException {
        String sid = requireStudent(ex);
        if (sid == null) return;
        Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
        String receiptNo = q.get("id");
        if (receiptNo == null || receiptNo.isEmpty()) {
            sendJson(ex, 400, jsonError("Missing receipt id."));
            return;
        }
        Map<String, Object> res = service.getReceipt(sid, receiptNo);
        sendJson(ex, res.containsKey("success") && (boolean) res.get("success") ? 200 : 404,
                JsonUtil.toJson(res));
    }

    private static void handlePenalties(HttpExchange ex) throws IOException {
        String sid = requireStudent(ex);
        if (sid == null) return;
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("success", true);
        res.put("penalties", service.getPenalties(sid));
        sendJson(ex, 200, JsonUtil.toJson(res));
    }

    // ---- mutations ------------------------------------------------------
    private static void handleBorrow(HttpExchange ex) throws IOException {
        String sid = requireStudent(ex);
        if (sid == null) return;
        Map<String, String> body = parseFormBody(readBody(ex));
        String bookId = body.get("bookId");
        if (bookId == null || bookId.isEmpty()) {
            sendJson(ex, 400, jsonError("Missing bookId."));
            return;
        }
        Map<String, Object> res = service.borrowBook(sid, bookId);
        sendJson(ex, res.containsKey("success") && (boolean) res.get("success") ? 200 : 400,
                JsonUtil.toJson(res));
    }

    private static void handleReturn(HttpExchange ex) throws IOException {
        String sid = requireStudent(ex);
        if (sid == null) return;
        Map<String, String> body = parseFormBody(readBody(ex));
        String recordId = body.get("recordId");
        if (recordId == null || recordId.isEmpty()) {
            sendJson(ex, 400, jsonError("Missing recordId."));
            return;
        }
        String damage = body.getOrDefault("damageCategory", "none");
        Map<String, Object> res = service.returnBook(sid, recordId, damage);
        sendJson(ex, res.containsKey("success") && (boolean) res.get("success") ? 200 : 400,
                JsonUtil.toJson(res));
    }

    private static void handleExtend(HttpExchange ex) throws IOException {
        String sid = requireStudent(ex);
        if (sid == null) return;
        Map<String, String> body = parseFormBody(readBody(ex));
        String recordId = body.get("recordId");
        if (recordId == null || recordId.isEmpty()) {
            sendJson(ex, 400, jsonError("Missing recordId."));
            return;
        }
        Map<String, Object> res = service.extendBorrowing(sid, recordId);
        sendJson(ex, res.containsKey("success") && (boolean) res.get("success") ? 200 : 400,
                JsonUtil.toJson(res));
    }

    private static void handleReserve(HttpExchange ex) throws IOException {
        String sid = requireStudent(ex);
        if (sid == null) return;
        Map<String, String> body = parseFormBody(readBody(ex));
        String bookId = body.get("bookId");
        if (bookId == null || bookId.isEmpty()) {
            sendJson(ex, 400, jsonError("Missing bookId."));
            return;
        }
        Map<String, Object> res = service.createReservation(sid, bookId);
        sendJson(ex, res.containsKey("success") && (boolean) res.get("success") ? 200 : 400,
                JsonUtil.toJson(res));
    }

    private static void handleCancelReservation(HttpExchange ex) throws IOException {
        String sid = requireStudent(ex);
        if (sid == null) return;
        Map<String, String> body = parseFormBody(readBody(ex));
        String reservationId = body.get("reservationId");
        if (reservationId == null || reservationId.isEmpty()) {
            sendJson(ex, 400, jsonError("Missing reservationId."));
            return;
        }
        Map<String, Object> res = service.cancelReservation(sid, reservationId);
        sendJson(ex, res.containsKey("success") && (boolean) res.get("success") ? 200 : 400,
                JsonUtil.toJson(res));
    }

    private static void handleBorrowReserved(HttpExchange ex) throws IOException {
        String sid = requireStudent(ex);
        if (sid == null) return;
        Map<String, String> body = parseFormBody(readBody(ex));
        String reservationId = body.get("reservationId");
        if (reservationId == null || reservationId.isEmpty()) {
            sendJson(ex, 400, jsonError("Missing reservationId."));
            return;
        }
        Map<String, Object> res = service.borrowReservedBook(sid, reservationId);
        sendJson(ex, res.containsKey("success") && (boolean) res.get("success") ? 200 : 400,
                JsonUtil.toJson(res));
    }

    private static void handleLost(HttpExchange ex) throws IOException {
        String sid = requireStudent(ex);
        if (sid == null) return;
        Map<String, String> body = parseFormBody(readBody(ex));
        String recordId = body.get("recordId");
        if (recordId == null || recordId.isEmpty()) {
            sendJson(ex, 400, jsonError("Missing recordId."));
            return;
        }
        Map<String, Object> res = service.markBookAsLost(sid, recordId);
        sendJson(ex, res.containsKey("success") && (boolean) res.get("success") ? 200 : 400,
                JsonUtil.toJson(res));
    }

    private static void handlePayPenalty(HttpExchange ex) throws IOException {
        String sid = requireStudent(ex);
        if (sid == null) return;
        Map<String, String> body = parseFormBody(readBody(ex));
        String type = body.getOrDefault("type", "");
        String relatedId = body.getOrDefault("relatedId", "");
        if (type.isEmpty() || relatedId.isEmpty()) {
            sendJson(ex, 400, jsonError("Missing type or relatedId."));
            return;
        }
        Map<String, Object> res = service.payPenalty(sid, type, relatedId);
        sendJson(ex, res.containsKey("success") && (boolean) res.get("success") ? 200 : 400,
                JsonUtil.toJson(res));
    }

    // =====================================================================
    //  Helpers
    // =====================================================================

    /** Returns the authenticated student id, or sends a 401 and returns null. */
    private static String requireStudent(HttpExchange ex) throws IOException {
        String token = tokenFrom(ex);
        String sid = service.studentIdForToken(token);
        if (sid == null) {
            sendJson(ex, 401, jsonError("You must be logged in to perform this action."));
            return null;
        }
        return sid;
    }

    private static String tokenFrom(HttpExchange ex) {
        // 1. Authorization: Bearer <token>
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        // 2. X-Session-Token header
        String t = ex.getRequestHeaders().getFirst("X-Session-Token");
        if (t != null && !t.isEmpty()) return t.trim();
        // 3. ?token=... query parameter (handy for GET requests)
        Map<String, String> q = parseQuery(ex.getRequestURI().getRawQuery());
        if (q.get("token") != null && !q.get("token").isEmpty()) return q.get("token");
        return null;
    }

    private static String readBody(HttpExchange ex) throws IOException {
        try (InputStream is = ex.getRequestBody();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) baos.write(buf, 0, n);
            return new String(baos.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    /** Parses both URL-encoded form bodies and simple key=value&... bodies. */
    private static Map<String, String> parseFormBody(String body) {
        Map<String, String> map = new HashMap<>();
        if (body == null || body.isEmpty()) return map;
        // If the body starts with '{' it might be JSON; we still try to
        // pluck key=value pairs out of the raw string for convenience.
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) continue;
            String k = urlDecode(pair.substring(0, eq));
            String v = urlDecode(pair.substring(eq + 1));
            map.put(k, v);
        }
        return map;
    }

    private static Map<String, String> parseQuery(String query) {
        Map<String, String> map = new HashMap<>();
        if (query == null || query.isEmpty()) return map;
        for (String pair : query.split("&")) {
            int eq = pair.indexOf('=');
            if (eq < 0) {
                map.put(urlDecode(pair), "");
            } else {
                map.put(urlDecode(pair.substring(0, eq)),
                        urlDecode(pair.substring(eq + 1)));
            }
        }
        return map;
    }

    private static String urlDecode(String s) {
        if (s == null) return "";
        try {
            return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return s;
        }
    }

    private static void sendJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] data = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
        addCorsHeaders(ex);
        ex.sendResponseHeaders(status, data.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static void addCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers",
                "Content-Type, Authorization, X-Session-Token");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    }

    private static String jsonError(String msg) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("success", false);
        m.put("message", msg);
        return JsonUtil.toJson(m);
    }

    // =====================================================================
    //  Static file handler
    // =====================================================================
    static class StaticHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange ex) throws IOException {
            String path = ex.getRequestURI().getPath();
            if (path.equals("/") || path.isEmpty()) path = "/index.html";

            // Never allow path traversal
            if (path.contains("..") || path.contains("\\")) {
                sendJson(ex, 400, jsonError("Bad request."));
                return;
            }

            // Handle CORS preflight quickly
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, Authorization, X-Session-Token");
                ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                ex.sendResponseHeaders(204, -1);
                return;
            }

            Path publicRoot = findPublicRoot();
            String relativePath = path.startsWith("/") ? path.substring(1) : path;
            Path file = publicRoot.resolve(relativePath).normalize();
            if (!file.startsWith(publicRoot)) {
                sendJson(ex, 400, jsonError("Bad request."));
                return;
            }
            if (!Files.exists(file) || Files.isDirectory(file)) {
                byte[] notFound = "404 Not Found".getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "text/plain; charset=UTF-8");
                ex.sendResponseHeaders(404, notFound.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(notFound); }
                return;
            }
            byte[] data = Files.readAllBytes(file);
            String mime = guessMime(path);
            ex.getResponseHeaders().add("Content-Type", mime);
            ex.sendResponseHeaders(200, data.length);
            try (OutputStream os = ex.getResponseBody()) {
                os.write(data);
            }
        }

        private Path findPublicRoot() throws IOException {
            Path workingDirectory = Paths.get("").toAbsolutePath().normalize();
            for (Path directory = workingDirectory; directory != null; directory = directory.getParent()) {
                Path direct = directory.resolve(PUBLIC_DIR).normalize();
                if (Files.isRegularFile(direct.resolve("index.html"))) {
                    return direct;
                }

                // IDEs often launch a project from its parent workspace folder.
                try (DirectoryStream<Path> children = Files.newDirectoryStream(directory)) {
                    for (Path child : children) {
                        if (!Files.isDirectory(child)) continue;
                        Path nested = child.resolve(PUBLIC_DIR).normalize();
                        if (Files.isRegularFile(nested.resolve("index.html"))) {
                            return nested;
                        }
                    }
                } catch (java.nio.file.AccessDeniedException ignored) {
                    // Keep checking parent folders when a system folder is restricted.
                }
            }
            throw new IOException("Cannot find src/web/index.html. Run the server from the project folder.");
        }

        private String guessMime(String path) {
            String lower = path.toLowerCase();
            if (lower.endsWith(".html") || lower.endsWith(".htm")) return "text/html; charset=UTF-8";
            if (lower.endsWith(".css"))  return "text/css; charset=UTF-8";
            if (lower.endsWith(".js"))   return "application/javascript; charset=UTF-8";
            if (lower.endsWith(".png"))  return "image/png";
            if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
            if (lower.endsWith(".svg"))  return "image/svg+xml";
            if (lower.endsWith(".ico"))  return "image/x-icon";
            return "application/octet-stream";
        }
    }

    // =====================================================================
    //  Minimal JSON encoder (no external library)
    // =====================================================================
    static class JsonUtil {
        static String toJson(Object o) {
            StringBuilder sb = new StringBuilder();
            write(sb, o);
            return sb.toString();
        }

        @SuppressWarnings("unchecked")
        private static void write(StringBuilder sb, Object o) {
            if (o == null) {
                sb.append("null");
            } else if (o instanceof String) {
                sb.append(quote((String) o));
            } else if (o instanceof Boolean || o instanceof Number) {
                sb.append(o.toString());
            } else if (o instanceof Map) {
                sb.append('{');
                boolean first = true;
                for (Map.Entry<String, Object> e : ((Map<String, Object>) o).entrySet()) {
                    if (!first) sb.append(',');
                    first = false;
                    sb.append(quote(e.getKey())).append(':');
                    write(sb, e.getValue());
                }
                sb.append('}');
            } else if (o instanceof Iterable) {
                sb.append('[');
                boolean first = true;
                for (Object item : (Iterable<?>) o) {
                    if (!first) sb.append(',');
                    first = false;
                    write(sb, item);
                }
                sb.append(']');
            } else {
                sb.append(quote(o.toString()));
            }
        }

        private static String quote(String s) {
            if (s == null) return "null";
            StringBuilder sb = new StringBuilder(s.length() + 2);
            sb.append('"');
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '"':  sb.append("\\\""); break;
                    case '\\': sb.append("\\\\"); break;
                    case '\b': sb.append("\\b"); break;
                    case '\f': sb.append("\\f"); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default:
                        if (c < 0x20) {
                            sb.append(String.format("\\u%04x", (int) c));
                        } else {
                            sb.append(c);
                        }
                }
            }
            sb.append('"');
            return sb.toString();
        }
    }
}
