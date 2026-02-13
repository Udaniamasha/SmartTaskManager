# Smart Task Manager (Java + MySQL)


A robust, enterprise-grade console application to **manage tasks, track deadlines, and analyze personal productivity**.

This project demonstrates **Layered Architecture**, **OOP principles**, **data persistence**, and **professional error handling**.

---

## Project Purpose

- **Data Persistence:** Connect Java objects to a relational database (MySQL).  
- **Clean Architecture:** Separation of concerns—View (Console), Business Logic (Service), Data Access (DAO).  
- **Defensive Programming:** Validates user input, handles SQL exceptions, and prevents crashes.  
- **Security:** Uses `PreparedStatement` to prevent SQL injection.

---

## Folder Structure

```text
SmartTaskManager/
│
├── src/main/java/com/taskmanager/
│   ├── config/       # Database Connection (Singleton Pattern)
│   ├── model/        # Task Entity (POJO with Encapsulation)
│   ├── dao/          # Data Access Object (Raw SQL Operations)
│   ├── service/      # Business Logic (Sorting, Filtering, Reports)
│   └── Main.java     # User Interface (Console Menu & Validation)
│
├── database.sql      # SQL Script to create table & dummy data
├── pom.xml           # Maven Dependencies (MySQL Connector)
└── README.md         # Project Documentation
```

---

## Key Features

| Feature               | Description                                                   |
|-----------------------|---------------------------------------------------------------|
| CRUD Operations       | Create, Read, Update, and Delete tasks efficiently.          |
| Smart Sorting         | Instantly sort tasks by Due Date using Java Streams.         |
| Advanced Filtering    | View tasks based on Priority (High/Medium/Low).             |
| Productivity Report   | Generates a statistical report of completion percentage.    |
| Input Validation      | Prevents crashes by validating dates and numbers.           |

---

## Tech Stack

| Component      | Details                                |
|----------------|----------------------------------------|
| Language       | Java 17+ (Core Java, Streams API)      |
| Database       | MySQL 8.0                              |
| Connectivity   | JDBC (Java Database Connectivity)      |
| Build Tool     | Maven                                   |
| IDE            | NetBeans / IntelliJ IDEA               |

---

## Installation & Setup (Step-by-Step)

### 1. Clone the Repository
```bash
git clone https://github.com/Udaniamasha/SmartTaskManager-Java.git
cd SmartTaskManager-Java
```

### 2. Configure the Database
- Open MySQL Workbench or your command line.  
- Open the `database.sql` file provided in this repo.  
- Run the script to create the database and populate sample tasks.

### 3. Update Configuration
- Navigate to `src/main/java/com/taskmanager/config/DatabaseConnection.java`.  
- Update the `PASSWORD` field with your MySQL root password:

```java
private static final String PASSWORD = "";
```

### 4. Build and Run
```bash
# Clean and Build the project
mvn clean install

# Run the Application
mvn exec:java -Dexec.mainClass="com.taskmanager.Main"
```

---

## Sample Output

```
===============================================
        SMART TASK MANAGER (Java + MySQL)
===============================================

Main Menu:
1. Add New Task
2. View All Tasks
3. Sort Tasks by Date
4. Filter by Priority
5. Mark Task as Completed
6. Delete Task
7. Generate Productivity Report
8. Exit

Choose an option: 7

--- PRODUCTIVITY REPORT ---
Total Tasks: 20
Completed:   3
Pending:     17
Efficiency:  15.00%
------------------------------
```

---

## Validation & Error Handling

- SQL Injection Protection: All database queries use `PreparedStatement` to prevent security vulnerabilities.  
- Input Sanitization: The app validates dates (`YYYY-MM-DD`) and priorities (Low/Medium/High) before sending data to the backend.  
- Graceful Failure: If the database goes down, the app catches `SQLException` and displays a user-friendly error message instead of crashing with a stack trace.

---

## Future Enhancements

- GUI: Migrate from Console to JavaFX or Swing.  
- Web API: Convert Service layer into a REST API using Spring Boot.  
- User Authentication: Add Login/Register functionality for multiple users.  
- Export Reports: CSV or PDF export for productivity reports.

---




