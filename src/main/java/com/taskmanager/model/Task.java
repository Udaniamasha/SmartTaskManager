package com.taskmanager.model;

import java.sql.Date;

public class Task {
    private int id;
    private String title;
    private String description;
    private String priority; // Low, Medium, High
    private Date dueDate;
    private String status;   // Pending, Completed

    // 1. Empty Constructor
    public Task() {}

    // 2. Constructor for creating new tasks (without ID)
    public Task(String title, String description, String priority, Date dueDate, String status) {
        this.title = title;
        this.description = description;
        this.priority = priority;
        this.dueDate = dueDate;
        this.status = status;
    }

    // 3. Getters and Setters
    public int getId() { return id; }
    public void setId(int id) { this.id = id; }
    
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    
    public Date getDueDate() { return dueDate; }
    public void setDueDate(Date dueDate) { this.dueDate = dueDate; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    // 4. toString() for easy printing
    @Override
    public String toString() {
        return String.format("ID: %d | %-15s | Priority: %-6s | Due: %s | Status: %s", 
                id, title, priority, dueDate, status);
    }
}