package com.taskmanager;

import com.taskmanager.model.Task;
import com.taskmanager.service.TaskService;

import java.sql.Date;
import java.util.List;
import java.util.Scanner;

public class Main {
    private static final TaskService service = new TaskService();
    private static final Scanner scanner = new Scanner(System.in);

    public static void main(String[] args) {
        printHeader();

        while (true) {
            printMenu();
            int choice = readInt(" Choose an option: ");
            handleChoice(choice);
        }
    }

    private static void printHeader() {
        System.out.println("===============================================");
        System.out.println("  SMART TASK MANAGER (Java + MySQL) ");
        System.out.println("===============================================");
    }

    private static void printMenu() {
        System.out.println("\n Main Menu:");
        System.out.println("1. Add New Task");
        System.out.println("2. View All Tasks");
        System.out.println("3. Sort Tasks by Date");
        System.out.println("4. Filter by Priority");
        System.out.println("5. Mark Task as Completed");
        System.out.println("6. Delete Task");
        System.out.println("7. Generate Productivity Report");
        System.out.println("8. Exit");
    }

    private static void handleChoice(int choice) {
        switch (choice) {
            case 1 -> addTask();
            case 2 -> printList(service.getAllTasks());
            case 3 -> printList(service.getTasksSortedByDate());
            case 4 -> filterByPriority();
            case 5 -> markCompleted();
            case 6 -> deleteTask();
            case 7 -> service.generateReport();
            case 8 -> exitApp();
            default -> System.out.println("❌ Invalid option. Please try again.");
        }
    }


    private static void addTask() {
        System.out.println("\n Add New Task:");
        String title = readString("Title: ");
        String desc = readString("Description: ");
        String priority = readPriority("Priority (Low/Medium/High): ");
        Date dueDate = readDate("Due Date (YYYY-MM-DD): ");

        // Check if it actually worked!
        boolean success = service.addTask(new Task(title, desc, priority, dueDate, "Pending"));

        if (success) {
            System.out.println("✅ Task added successfully!");
        } else {
            System.out.println("❌ Failed to add task. Check database connection.");
        }
    }

    private static void filterByPriority() {
        String priority = readPriority("Enter Priority to filter (Low/Medium/High): ");
        printList(service.filterByPriority(priority));
    }

    private static void markCompleted() {
        int id = readInt("Enter Task ID to mark as completed: ");
        if (service.completeTask(id)) {
            System.out.println("✅ Task marked as completed!");
        } else {
            System.out.println("❌ Task ID not found.");
        }
    }

    private static void deleteTask() {
        int id = readInt("Enter Task ID to delete: ");
        String confirm = readString("Are you sure you want to delete this task? (Y/N): ");
        if (confirm.equalsIgnoreCase("Y")) {
            if (service.deleteTask(id)) {
                System.out.println("🗑️ Task deleted successfully!");
            } else {
                System.out.println("❌ Task ID not found.");
            }
        } else {
            System.out.println("⚠️ Deletion canceled.");
        }
    }

    private static void exitApp() {
        System.out.println("\n Thank you for using Smart Task Manager!");
        System.exit(0);
    }

    private static void printList(List<Task> tasks) {
        if (tasks.isEmpty()) {
            System.out.println(" No tasks found.");
            return;
        }
        System.out.println("\n---  TASK LIST ---");
        for (Task t : tasks) {
            System.out.println(t);
        }
        System.out.println("--------------------");
    }

    // ------------------- Helper Methods -------------------
    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            try {
                return Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("❌ Invalid number. Try again.");
            }
        }
    }

    private static String readString(String prompt) {
        System.out.print(prompt);
        return scanner.nextLine().trim();
    }

    private static String readPriority(String prompt) {
        while (true) {
            String input = readString(prompt);
            if (input.equalsIgnoreCase("Low") || input.equalsIgnoreCase("Medium") || input.equalsIgnoreCase("High")) {
                return input.substring(0,1).toUpperCase() + input.substring(1).toLowerCase();
            } else {
                System.out.println("❌ Invalid priority. Use Low, Medium, or High.");
            }
        }
    }

    private static Date readDate(String prompt) {
        while (true) {
            String input = readString(prompt);
            try {
                return Date.valueOf(input);
            } catch (IllegalArgumentException e) {
                System.out.println("❌ Invalid date format. Use YYYY-MM-DD.");
            }
        }
    }
}
