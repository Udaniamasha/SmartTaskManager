package com.taskmanager.dao;

import com.taskmanager.config.DatabaseConnection;
import com.taskmanager.model.Task;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class TaskDAO {
    public boolean addTask(Task task) {
        String sql = "INSERT INTO tasks (title, description, priority, due_date, status) VALUES (?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, task.getTitle());
            pstmt.setString(2, task.getDescription());
            pstmt.setString(3, task.getPriority());
            pstmt.setDate(4, task.getDueDate());
            pstmt.setString(5, task.getStatus());

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0; // Returns true if successful

        } catch (SQLException e) {
            System.err.println("❌ Error adding task: " + e.getMessage());
            return false; // Returns false if it failed
        }
    }

    public List<Task> getAllTasks() {
        List<Task> tasks = new ArrayList<>();
        String sql = "SELECT * FROM tasks";
        
        try (Connection conn = DatabaseConnection.getConnection();
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
             
            while (rs.next()) {
                Task t = new Task();
                t.setId(rs.getInt("id"));
                t.setTitle(rs.getString("title"));
                t.setDescription(rs.getString("description"));
                t.setPriority(rs.getString("priority"));
                t.setDueDate(rs.getDate("due_date"));
                t.setStatus(rs.getString("status"));
                tasks.add(t);
            }
        } catch (SQLException e) {
            System.err.println("❌ Error fetching tasks: " + e.getMessage());
        }
        return tasks;
    }

    public boolean updateStatus(int id, String newStatus) {
        String sql = "UPDATE tasks SET status = ? WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, newStatus);
            pstmt.setInt(2, id);

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0; // Returns true if a task was actually updated
        } catch (SQLException e) {
            System.err.println("❌ Error updating task: " + e.getMessage());
            return false;
        }
    }

    public boolean deleteTask(int id) {
        String sql = "DELETE FROM tasks WHERE id = ?";
        try (Connection conn = DatabaseConnection.getConnection(); PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setInt(1, id);

            int rowsAffected = pstmt.executeUpdate();
            return rowsAffected > 0; // Returns true if a task was actually deleted
        } catch (SQLException e) {
            System.err.println("❌ Error deleting task: " + e.getMessage());
            return false;
        }
    }
}