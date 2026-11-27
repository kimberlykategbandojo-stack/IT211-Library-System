# Online Library System

A browser-based library management application built with HTML, CSS, JavaScript, and a small Java HTTP server. Students can register and sign in, search the book catalog, borrow or reserve books, and review receipts, history, and penalties.

## Requirements

- Java 11 or newer (a JDK is required to compile the application)
- A modern web browser
- PowerShell, Command Prompt, or the VS Code integrated terminal

The Java server uses built-in JDK libraries; no external dependencies or database server are required.

## Run locally

Open PowerShell in the project folder (the folder containing `README.md`) and compile the Java files:

```powershell
New-Item -ItemType Directory -Force out
javac -d out src\java\*.java
```

Start the server:

```powershell
java -cp out LibraryServer
```

Keep that terminal open while using the website. Open the student website in your browser:

- Library website: <http://localhost:2350>

The Java server serves the website and its API on the same port. For local development, the page can also be opened through a separate static server such as VS Code Live Server; browser API requests will still be sent to `http://localhost:2350`.

If launching from an IDE workspace that contains this project in a subfolder, the server searches the workspace and its parent folders for `src/web/index.html`.

To stop the server, press **Ctrl+C** in the terminal.

## Using the app

Register with an eight-digit Student ID, full name, and phone number, and accept the Terms and Conditions. Then sign in with your Student ID to browse and use the catalog.

## Features

- Search books by title, author, or category and view their availability.
- Borrow books for three days, with one two-day extension per borrowing.
- Reserve books; reservations expire after seven days if they are not canceled or converted into a borrowing.
- Return books and view borrowing receipts, transaction history, profile details, and penalties.
- Track penalties for overdue, damaged, lost, or expired-reservation cases.
- Use a responsive interface on desktop and mobile browsers.

The system stores the sample catalog and student activity in `data/library-data.txt`. Unpaid penalties can restrict borrowing and reservations.

## Prototype notes

- This is a local classroom demonstration, not a production library system.
- Sign-in uses a Student ID without a password. Use sample information rather than sensitive real-world student data.
- Student and transaction records are stored on the computer running the server. Keep the data file private and make a backup before changing or removing it.
- Login sessions are held in memory and end when the server stops.
- Penalties are recorded by the app; it does not process real payments.

## Project files

- `src/web/index.html`: page structure and interface
- `src/web/style.css`: page styling and responsive layout
- `src/web/app.js`: browser interactions and API requests
- `src/java/LibraryServer.java`: Java HTTP server and API routes
- `src/java/LibraryService.java`: library rules and operations
- `src/java/`: student, book, borrowing, reservation, receipt, validation, and storage classes
- `data/library-data.txt`: local application data
- `docs/proposal.md`: project proposal
- `docs/class-ownership.md`: class assignments
- `diagrams/`: system diagrams

## Folder structure

```text
Online Library System/
|-- data/       Local application data
|-- diagrams/   System diagrams
|-- docs/       Proposal and class ownership
|-- out/        Compiled Java classes
|-- src/java/   Java server, business logic, and data models
|-- src/web/    Website files
|-- README.md   Setup and usage guide
```
