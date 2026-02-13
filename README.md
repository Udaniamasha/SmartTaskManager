#  Smart Task Manager (Java + MySQL)

A robust, enterprise-grade console application to **manage tasks, track deadlines, and analyze personal productivity**.  

This project goes beyond “Hello World” examples, demonstrating **Layered Architecture**, **OOP principles**, **data persistence**, and **professional error handling**.

---

##  Project Purpose

The goal of this project is to build a **scalable, real-world application** that mirrors backend development practices:

- **Data Persistence:** Connect Java objects to a relational database (MySQL).  
- **Clean Architecture:** Separation of concerns—View (Console), Business Logic (Service), Data Access (DAO).  
- **Defensive Programming:** Validates user input, handles SQL exceptions, and prevents crashes.  
- **Security:** Uses `PreparedStatement` to prevent SQL injection.

 Highlights: OOP, JDBC, MySQL, Layered Architecture, Input Validation, SQL Injection Protection, Maven Build.

---

##  Folder Structure
This project follows standard Maven directory layout with a package-by-feature approach.

``text
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
 ``bash
git clone https://github.com/Udaniamasha/Smart-Task-Manager-Java.git
cd Smart-Task-Manager-Java

### 2. Configure the Database

Open MySQL Workbench or command line.

Open the database.sql file provided in this repo.

Run the script to create the database and populate it with sample tasks.

### 3. Update Configuration

Navigate to src/main/java/com/taskmanager/config/DatabaseConnection.java.

Update the PASSWORD field with MySQL root password:
``java
private static final String PASSWORD = "your_actual_password";

### Buid and Run
``bash
# Clean and Build the project
mvn clean install

# Run the Application
mvn exec:java -Dexec.mainClass="com.taskmanager.Main"




