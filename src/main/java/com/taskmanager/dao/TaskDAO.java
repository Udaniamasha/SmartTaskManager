package com.taskmanager.dao;

import com.taskmanager.config.DatabaseConnection;
import com.taskmanager.model.Task;

import java.sql.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Data-Access Object for the {@code tasks} table.
 *
 * <p>Design principles applied:
 * <ul>
 *   <li><b>No connection leaks</b> — every {@link Connection}, {@link PreparedStatement},
 *       and {@link ResultSet} is opened inside a try-with-resources block.</li>
 *   <li><b>No SQL injection</b> — every parameter is bound via {@link PreparedStatement},
 *       never concatenated into a string.</li>
 *   <li><b>No silent failures</b> — every {@link SQLException} is logged with full
 *       context before a safe default is returned to the caller.</li>
 *   <li><b>Single responsibility</b> — this class only maps Java objects to SQL and
 *       back; no business logic lives here.</li>
 * </ul>
 */
public class TaskDAO {

    private static final Logger LOGGER = Logger.getLogger(TaskDAO.class.getName());

    // ── SQL constants ─────────────────────────────────────────────────────────
    // Keeping queries as named constants makes them easy to find, review,
    // and update without touching method bodies.

    private static final String SQL_INSERT =
            "INSERT INTO tasks (title, description, priority, due_date, status) " +
                    "VALUES (?, ?, ?, ?, ?)";

    private static final String SQL_SELECT_ALL =
            "SELECT id, title, description, priority, due_date, status " +
                    "FROM tasks " +
                    "ORDER BY due_date ASC";

    private static final String SQL_SELECT_BY_ID =
            "SELECT id, title, description, priority, due_date, status " +
                    "FROM tasks WHERE id = ?";

    private static final String SQL_UPDATE_TASK =
            "UPDATE tasks " +
                    "SET title = ?, description = ?, priority = ?, due_date = ?, status = ? " +
                    "WHERE id = ?";

    private static final String SQL_UPDATE_STATUS =
            "UPDATE tasks SET status = ? WHERE id = ?";

    private static final String SQL_DELETE =
            "DELETE FROM tasks WHERE id = ?";

    // =========================================================================
    // Write operations
    // =========================================================================

    /**
     * Inserts a new task row and populates the generated {@code id} back onto
     * the supplied {@link Task} object.
     *
     * @param task the task to insert; its {@code id} field is ignored on entry
     *             and set to the generated key on success
     * @return {@code true} if exactly one row was inserted
     */
    public boolean addTask(Task task) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     SQL_INSERT, Statement.RETURN_GENERATED_KEYS)) {

            bindTaskFields(ps, task);                  // columns 1-5
            int rows = ps.executeUpdate();

            // Write the DB-generated id back so the caller has the real key
            if (rows > 0) {
                try (ResultSet keys = ps.getGeneratedKeys()) {
                    if (keys.next()) {
                        task.setId(keys.getInt(1));
                    }
                }
                LOGGER.info(() -> "Task inserted with id=" + task.getId()
                        + " title=\"" + task.getTitle() + "\"");
                return true;
            }

        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to insert task: \"" + task.getTitle() + "\"", ex);
        }
        return false;
    }

    /**
     * Updates every mutable field of an existing task row.
     *
     * <p>Use this when the user edits the full task. For status-only changes,
     * prefer the lighter {@link #updateStatus(int, String)}.
     *
     * @param task the task carrying the new field values; {@code id} must be set
     * @return {@code true} if a row was matched and updated
     */
    public boolean updateTask(Task task) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_TASK)) {

            bindTaskFields(ps, task);           // columns 1-5 (same order as INSERT)
            ps.setInt(6, task.getId());         // WHERE id = ?

            int rows = ps.executeUpdate();
            if (rows > 0) {
                LOGGER.info(() -> "Task updated: id=" + task.getId());
                return true;
            }

            // Zero rows means the id did not exist — log a warning, not an error
            LOGGER.warning(() -> "updateTask matched no rows for id=" + task.getId());

        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to update task id=" + task.getId(), ex);
        }
        return false;
    }

    /**
     * Changes only the {@code status} column of a task.
     *
     * @param id        the task to update
     * @param newStatus the new status value (validated by the service layer)
     * @return {@code true} if a row was matched and updated
     */
    public boolean updateStatus(int id, String newStatus) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_UPDATE_STATUS)) {

            ps.setString(1, newStatus);
            ps.setInt   (2, id);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                LOGGER.info(() -> "Task id=" + id + " status → \"" + newStatus + "\"");
                return true;
            }
            LOGGER.warning(() -> "updateStatus matched no rows for id=" + id);

        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to update status for task id=" + id, ex);
        }
        return false;
    }

    /**
     * Permanently deletes the task row with the given {@code id}.
     *
     * @param id the task to remove
     * @return {@code true} if a row was found and deleted
     */
    public boolean deleteTask(int id) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_DELETE)) {

            ps.setInt(1, id);

            int rows = ps.executeUpdate();
            if (rows > 0) {
                LOGGER.info(() -> "Task id=" + id + " deleted.");
                return true;
            }
            LOGGER.warning(() -> "deleteTask matched no rows for id=" + id);

        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to delete task id=" + id, ex);
        }
        return false;
    }

    // =========================================================================
    // Read operations
    // =========================================================================

    /**
     * Returns every task row, ordered by due date ascending.
     *
     * @return a mutable list of tasks; empty (never {@code null}) on failure
     */
    public List<Task> getAllTasks() {
        List<Task> tasks = new ArrayList<>();

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_ALL);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                tasks.add(mapRow(rs));
            }
            LOGGER.fine(() -> "getAllTasks returned " + tasks.size() + " row(s).");

        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to fetch tasks.", ex);
            // Return an empty list — never propagate a half-built list
            return Collections.emptyList();
        }
        return tasks;
    }

    /**
     * Fetches a single task by its primary key.
     *
     * @param id the task ID to look up
     * @return an {@link Optional} containing the task, or empty if not found
     */
    public Optional<Task> findById(int id) {
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SQL_SELECT_BY_ID)) {

            ps.setInt(1, id);

            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRow(rs));
                }
            }

        } catch (SQLException ex) {
            LOGGER.log(Level.SEVERE, "Failed to fetch task id=" + id, ex);
        }
        return Optional.empty();
    }

    // =========================================================================
    // Private helpers
    // =========================================================================

    /**
     * Binds the five mutable task fields to a PreparedStatement in the
     * standard column order used by both {@code SQL_INSERT} and
     * {@code SQL_UPDATE_TASK}:
     * <pre>
     *   1 → title
     *   2 → description
     *   3 → priority
     *   4 → due_date
     *   5 → status
     * </pre>
     *
     * <p>Centralising binding in one method eliminates the risk of columns
     * drifting out of sync between INSERT and UPDATE statements.
     */
    private void bindTaskFields(PreparedStatement ps, Task task) throws SQLException {
        ps.setString(1, task.getTitle());
        ps.setString(2, task.getDescription());
        ps.setString(3, task.getPriority());
        ps.setDate  (4, task.getDueDate());
        ps.setString(5, task.getStatus());
    }

    /**
     * Maps the current row of a {@link ResultSet} to a {@link Task} instance.
     *
     * <p>Called for every row in both {@link #getAllTasks()} and
     * {@link #findById(int)}, so any schema change only needs updating here.
     */
    private Task mapRow(ResultSet rs) throws SQLException {
        Task task = new Task();
        task.setId         (rs.getInt   ("id"));
        task.setTitle      (rs.getString("title"));
        task.setDescription(rs.getString("description"));
        task.setPriority   (rs.getString("priority"));
        task.setDueDate    (rs.getDate  ("due_date"));
        task.setStatus     (rs.getString("status"));
        return task;
    }
}