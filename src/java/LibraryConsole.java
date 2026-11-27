import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Small console entry point for demonstrating one complete borrow flow.
 * Business rules stay in LibraryService; this class only collects input
 * and displays the results.
 */
public class LibraryConsole {

    public static void main(String[] args) {
        FileStorage storage = new FileStorage();
        LibraryService service = new LibraryService(storage);

        try (Scanner input = new Scanner(System.in)) {
            System.out.println("=== Online Library Console ===");
            System.out.println("1. Log in and borrow a book");
            System.out.println("2. Register and borrow a book");
            System.out.println("0. Exit");
            System.out.print("Choose an option: ");

            String choice = input.nextLine().trim();
            if ("0".equals(choice)) {
                System.out.println("Goodbye.");
                return;
            }
            if (!"1".equals(choice) && !"2".equals(choice)) {
                System.out.println("Invalid option. Run the console again to try.");
                return;
            }

            runBorrowFlow(input, service, "2".equals(choice));
        }
    }

    private static void runBorrowFlow(Scanner input, LibraryService service,
                                     boolean register) {
        System.out.print("Student ID (8 digits): ");
        String studentId = input.nextLine().trim();

        Map<String, Object> account;
        if (register) {
            System.out.print("Full name: ");
            String fullName = input.nextLine().trim();
            System.out.print("Phone number: ");
            String phone = input.nextLine().trim();
            account = service.registerStudent(studentId, fullName, phone, true);
        } else {
            account = service.loginStudent(studentId);
        }

        if (!isSuccessful(account)) {
            System.out.println("Could not continue: " + account.get("message"));
            return;
        }
        System.out.println("Welcome, " + account.get("fullName") + ".");

        List<Map<String, Object>> books = service.listBooks("", "");
        System.out.println("\nBooks:");
        for (Map<String, Object> book : books) {
            System.out.printf("%s | %s by %s | %s (%s available)%n",
                    book.get("bookId"), book.get("title"), book.get("author"),
                    book.get("status"), book.get("availableCopies"));
        }

        System.out.print("Enter the ID of the book to borrow: ");
        String bookId = input.nextLine().trim();
        Map<String, Object> result = service.borrowBook(studentId, bookId);
        if (!isSuccessful(result)) {
            System.out.println("Could not borrow book: " + result.get("message"));
            return;
        }

        Map<String, Object> receipt = service.getReceipt(
                studentId, String.valueOf(result.get("receiptNo")));
        System.out.println("\nBorrow complete!");
        System.out.println("Receipt: " + receipt.get("receiptNo"));
        System.out.println("Book: " + receipt.get("bookTitle"));
        System.out.println("Borrowed: " + receipt.get("borrowDateTime"));
        System.out.println("Due: " + receipt.get("dueDate"));
    }

    private static boolean isSuccessful(Map<String, Object> result) {
        return Boolean.TRUE.equals(result.get("success"));
    }
}
