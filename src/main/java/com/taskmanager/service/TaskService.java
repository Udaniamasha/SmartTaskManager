package com.taskmanager.service;

import com.taskmanager.dao.TaskDAO;
import com.taskmanager.model.Task;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class TaskService {
    
    // Connect to the DAO layer
    private final TaskDAO taskDAO = new TaskDAO();

    public boolean addTask(Task task) {
        return taskDAO.addTask(task);
    }

    public List<Task> getAllTasks() {
        return taskDAO.getAllTasks();
    }

    public boolean completeTask(int id) {
        return taskDAO.updateStatus(id, "Completed");
    }

    public boolean deleteTask(int id) {
        return taskDAO.deleteTask(id);
    }

    // ⭐ ADVANCED FEATURE 1: Sorting by Date (Java Streams)
    public List<Task> getTasksSortedByDate() {
        List<Task> tasks = taskDAO.getAllTasks();
        tasks.sort(Comparator.comparing(Task::getDueDate));
        return tasks;
    }

    // ⭐ ADVANCED FEATURE 2: Filter by Priority
    public List<Task> filterByPriority(String priority) {
        return taskDAO.getAllTasks().stream()
                .filter(t -> t.getPriority().equalsIgnoreCase(priority))
                .collect(Collectors.toList());
    }

    // ⭐ ADVANCED FEATURE 3: Generate Productivity Report
    public void generateReport() {
        List<Task> tasks = taskDAO.getAllTasks();
        long total = tasks.size();
        long completed = tasks.stream().filter(t -> t.getStatus().equals("Completed")).count();
        long pending = total - completed;
        double percentage = total > 0 ? ((double) completed / total) * 100 : 0;

        System.out.println("\n --- PRODUCTIVITY REPORT ---");
        System.out.println("Total Tasks: " + total);
        System.out.println("Completed:   " + completed);
        System.out.println("Pending:     " + pending);
        System.out.printf("Efficiency:  %.2f%%\n", percentage);
        System.out.println("------------------------------");
    }
}