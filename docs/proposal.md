# Project Proposal: Online Library System

## 1. Project Overview

The Online Library System is a locally hosted web application for managing common student library tasks. Students can create an account, sign in, search the catalog, borrow or reserve books, and review their library activity. The application uses a Java server, a browser-based interface, and a local text file to save its data.


## 2. Problem Statement

Keeping track of books, student borrowing, due dates, reservations, and penalties can take time when these tasks are handled manually. Paper records can be difficult to search and update, and students may not have one place to check their current borrowings or transaction history. This project addresses these issues with a simple catalog and a central application that records library activity.


## 3. Proposed Solutions

The project provides a web interface connected to a Java service that manages catalog and student transactions. The service applies the borrowing and reservation rules, calculates penalties, and stores changes in `data/library-data.txt`. Students can use the interface to search for books and manage their own borrowing activity. The application runs locally and does not require a separate database server or third-party Java libraries.


## 4. Project Objectives

- Build a working library web application using Java, HTML, CSS, and JavaScript.
- Let students register and sign in using an eight-digit Student ID.
- Provide a searchable catalog with book details and availability.
- Apply the system's borrowing, reservation, return, extension, and penalty rules consistently.
- Save student and transaction data locally so it remains available after the server restarts.
- Demonstrate object-oriented programming through separate classes with clear responsibilities.


## 5. Intended Users

- **Students:** register, sign in, find books, borrow or reserve them, and view account activity.
- **Library staff or project demonstrators:** review the catalog and demonstrate how transactions and library rules work.
- **Project evaluators:** examine the application as a classroom demonstration of Java, web development, and object-oriented design.

The current application is a local classroom demonstration. It is not designed for public internet access or production library operations.


## 6. Proposed Features

- Student registration with Student ID, full name, phone number, and agreement to the Terms and Conditions.
- Student ID sign-in and sign-out.
- A catalog of sample books with search by title, author, or category.
- Book details and availability information.
- Borrowing for three days, with one two-day extension per borrowing.
- Reservations that expire after seven days if they are not canceled or converted into a borrowing.
- Returns, transaction history, borrowing receipts, profile details, and penalty information.
- Penalty tracking for overdue, damaged, lost, and expired-reservation cases.
- Responsive pages for desktop and mobile browsers.
- Local plain-text data storage in `data/library-data.txt`.

The Java service enforces the transaction rules. It allows up to two active borrowings and two active reservations per student, while also preventing a new borrowing when that student already has an unreturned book.


## 7. Scope and Assumptions

### In scope

- A local web application served at `http://localhost:2350`.
- Student accounts and a sample catalog of 44 books across multiple categories.
- Borrowing, returning, reservations, extensions, receipts, transaction history, and penalties.
- Saving data to a local file without a separate database server.

### Out of scope

- Deployment to a public website or cloud service.
- Password-based authentication or production-grade identity security.
- Online payment processing, email notifications, and barcode scanning.
- A staff administration portal for maintaining a real library inventory.

### Assumptions

- The computer running the application has JDK 11 or newer and a modern web browser.
- The server is started from the project folder so it can find the frontend and data files.
- The project is used for learning and demonstration with sample data, not for handling sensitive real-world library records.


## 8. Object-Oriented Design

The application separates its responsibilities across Java classes:

| Class | Responsibility |
|---|---|
| `LibraryServer` | Starts the HTTP server, serves frontend files, and handles API requests. |
| `LibraryService` | Applies library rules and coordinates student, book, and transaction operations. |
| `FileStorage` | Reads and writes persistent application data. |
| `Book` | Represents a catalog book and its availability. |
| `Student` | Represents a student account and profile details. |
| `BorrowingRecord` | Represents a borrowing, due date, return, or extension. |
| `Reservation` | Represents a student's reservation and its status. |
| `DigitalReceipt` | Represents a receipt for a successful borrowing. |

This design uses encapsulation to keep data with the objects it describes and separates HTTP handling, business rules, and storage. The browser communicates with the server through HTTP requests; the Java service validates and processes operations before data is saved.


## 9. Main Workflow

1. The student opens the application in a browser at `http://localhost:2350`.
2. A new student registers with the required details and signs in with their Student ID.
3. The student searches the catalog and checks book availability.
4. The student requests to borrow an available book or reserve a book, subject to the applicable rules.
5. The Java service validates the request, updates the relevant records, and saves the data.
6. The student can view receipts, current borrowings, reservations, history, profile details, or penalties.
7. The student returns or extends a borrowing when eligible; the service updates the record and any applicable penalties.


## 10. Non-Functional Requirements

- **Usability:** provide clear navigation, readable content, and helpful success or error messages.
- **Compatibility:** work in a modern browser on desktop and mobile screen sizes.
- **Performance:** load the local catalog and process typical classroom demonstration requests without noticeable delay.
- **Reliability:** validate requests and apply transaction rules in the Java service before saving changes.
- **Maintainability:** keep server, business logic, data models, storage, and frontend code organized in separate files.
- **Data persistence:** retain saved student and transaction records across server restarts.
- **Security limits:** keep the application local and use sample information; Student ID sign-in has no password and is not suitable for protecting real accounts.

## 11. Success Measures

The project will be considered successful when:

- The Java source files compile using JDK 11 or newer.
- The server starts and the web interface opens at `http://localhost:2350`.
- A student can register, sign in, and find books in the catalog.
- Borrowing, returning, reservations, extensions, and penalties follow the rules implemented by the Java service.
- Receipts and transaction history show the relevant completed activity.
- Saved data remains available after restarting the server.
- The interface remains usable at desktop and mobile browser sizes.


## 12. Risks and Mitigations

| Risk | Mitigation |
|---|---|
| Port 2350 is already in use. | Stop the other server using the port or update the configured port and its documentation. |
| The app cannot find its frontend or data files. | Start the server from the project root and keep the expected folder structure. |
| Local data is accidentally changed or removed. | Keep a backup of `data/library-data.txt` before making manual changes. |
| The server stops or the computer shuts down. | Save data through the application and restart the Java server when needed. |
| Student ID-only sign-in is mistaken for secure authentication. | Use sample data and restrict use to local classroom demonstrations. |
| Business rules are applied inconsistently. | Keep rule checks in `LibraryService` and verify each main transaction workflow during development. |


## 13. Conclusion

The Online Library System demonstrates how a small web application can support catalog browsing and common library transactions. It combines a Java HTTP server, a responsive browser interface, object-oriented classes, and local file storage. The project provides a practical learning example while keeping its limitations clear: it is intended for local demonstration and does not provide production-grade authentication or deployment.
