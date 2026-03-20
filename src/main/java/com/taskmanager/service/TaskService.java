package com.taskmanager.service;

import com.taskmanager.dao.TaskDAO;
import com.taskmanager.model.Task;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Service layer for all Task business operations.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validate inputs <em>before</em> delegating to the DAO</li>
 *   <li>Encapsulate every business rule (status transitions, search logic, etc.)</li>
 *   <li>Return clean results — callers never touch the DAO directly</li>
 * </ul>
 *
 * <p>The {@link TaskDAO} is injected via the constructor so this class is
 * fully unit-testable without a real database (pass a mock or stub instead).
 */
public class TaskService {

    private static final Logger LOGGER = Logger.getLogger(TaskService.class.getName());

    // ── Domain constants ──────────────────────────────────────────────────────
    public static final String STATUS_PENDING   = "Pending";
    public static final String STATUS_COMPLETED = "Completed";

    private static final int MAX_TITLE_LENGTH = 150;
    private static final int MAX_DESC_LENGTH  = 500;

    // ── Dependencies (injected) ───────────────────────────────────────────────
    private final TaskDAO taskDAO;

    /**
     * Primary constructor — accepts any {@link TaskDAO} implementation.
     * Enables easy unit testing with a mock or in-memory DAO.
     */
    public TaskService(TaskDAO taskDAO) {
        this.taskDAO = taskDAO;
    }

    /**
     * Convenience no-arg constructor for production use.
     * Creates the default {@link TaskDAO} internally.
     */
    public TaskService() {
        this(new TaskDAO());
    }

    // =========================================================================
    // Write operations
    // =========================================================================

    /**
     * Validates and persists a new task.
     *
     * <p>Business rules enforced:
     * <ul>
     *   <li>Title must be non-blank and within {@value #MAX_TITLE_LENGTH} chars</li>
     *   <li>Description must not exceed {@value #MAX_DESC_LENGTH} chars</li>
     *   <li>Due date must be today or in the future</li>
     *   <li>Priority must be Low, Medium, or High (case-insensitive)</li>
     *   <li>Status is always forced to {@value #STATUS_PENDING} on creation</li>
     * </ul>
     *
     * @param task the task to persist (must not be {@code null})
     * @return {@code true} if the task was saved successfully
     * @throws IllegalArgumentException if any validation rule is violated
     */
    public boolean addTask(Task task) {
        validateNotNull(task, "task");
        validateTitle(task.getTitle());
        validateDescription(task.getDescription());
        validateDueDate(task.getDueDate());
        validatePriority(task.getPriority());

        // New tasks are always Pending — enforce the status invariant here
        task.setStatus(STATUS_PENDING);

        LOGGER.info(() -> "Adding task: \"" + task.getTitle() + "\"");
        return taskDAO.addTask(task);
    }

    /**
     * Marks an existing task as {@value #STATUS_COMPLETED}.
     *
     * <p>Verifies the task is not already completed before issuing the update,
     * preventing redundant database writes.
     *
     * @param id the task ID (must be positive)
     * @return {@code true} if the status was updated
     * @throws IllegalArgumentException if {@code id} is not positive
     * @throws IllegalStateException    if the task is already completed
     */
    public boolean markTaskCompleted(int id) {
        validatePositiveId(id);

        // Guard against completing an already-completed task
        findById(id).ifPresent(t -> {
            if (STATUS_COMPLETED.equals(t.getStatus())) {
                throw new IllegalStateException(
                        "Task " + id + " is already marked as completed.");
            }
        });

        LOGGER.info(() -> "Marking task " + id + " as completed.");
        return taskDAO.updateStatus(id, STATUS_COMPLETED);
    }

    /**
     * Backward-compatible alias for {@link #markTaskCompleted(int)}.
     * Kept so existing callers (e.g. {@code TaskUI}) compile without changes.
     */
    public boolean completeTask(int id) {
        return markTaskCompleted(id);
    }

    /**
     * Permanently removes a task by ID.
     *
     * @param id the task ID (must be positive)
     * @return {@code true} if a row was deleted
     * @throws IllegalArgumentException if {@code id} is not positive
     */
    public boolean deleteTask(int id) {
        validatePositiveId(id);
        LOGGER.info(() -> "Deleting task " + id);
        return taskDAO.deleteTask(id);
    }

    // =========================================================================
    // Read / query operations
    // =========================================================================

    /**
     * Returns every task in default database order.
     */
    public List<Task> getAllTasks() {
        return taskDAO.getAllTasks();
    }

    /**
     * Returns every task sorted ascending by due date.
     * Tasks with a {@code null} due date are sorted last.
     */
    public List<Task> getTasksSortedByDate() {
        return taskDAO.getAllTasks().stream()
                .sorted(Comparator.comparing(
                        Task::getDueDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .collect(Collectors.toList());
    }

    /**
     * Case-insensitive full-text search across title and description.
     *
     * @param keyword the search term (must not be blank)
     * @return matching tasks in insertion order
     * @throws IllegalArgumentException if keyword is blank
     */
    public List<Task> searchTasks(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            throw new IllegalArgumentException("Search keyword must not be blank.");
        }
        String lower = keyword.toLowerCase().trim();

        return taskDAO.getAllTasks().stream()
                .filter(t -> containsIgnoreCase(t.getTitle(),       lower)
                        || containsIgnoreCase(t.getDescription(), lower))
                .collect(Collectors.toList());
    }

    /**
     * Filters tasks by their exact status value.
     *
     * @param status {@value #STATUS_PENDING} or {@value #STATUS_COMPLETED}
     * @return matching tasks (never {@code null})
     * @throws IllegalArgumentException if status is not a recognised value
     */
    public List<Task> filterByStatus(String status) {
        validateStatus(status);
        return taskDAO.getAllTasks().stream()
                .filter(t -> status.equalsIgnoreCase(t.getStatus()))
                .collect(Collectors.toList());
    }

    /**
     * Filters tasks by priority level.
     *
     * @param priority Low, Medium, or High (case-insensitive)
     * @return matching tasks (never {@code null})
     * @throws IllegalArgumentException if priority is not recognised
     */
    public List<Task> filterByPriority(String priority) {
        validatePriority(priority);
        return taskDAO.getAllTasks().stream()
                .filter(t -> priority.equalsIgnoreCase(t.getPriority()))
                .collect(Collectors.toList());
    }

    /**
     * Looks up a single task by ID.
     *
     * @param id the task ID
     * @return an {@link Optional} containing the task, or empty if not found
     */
    public Optional<Task> findById(int id) {
        validatePositiveId(id);
        return taskDAO.getAllTasks().stream()
                .filter(t -> t.getId() == id)
                .findFirst();
    }

    // =========================================================================
    // Reporting
    // =========================================================================

    /**
     * Builds and returns an immutable {@link ProductivityReport}.
     *
     * <p>The service layer never writes to {@code System.out} directly —
     * callers decide how to present the report (Swing label, log line,
     * REST response, etc.).
     *
     * @return a snapshot report; never {@code null}
     */
    public ProductivityReport generateReport() {
        List<Task> tasks = taskDAO.getAllTasks();

        long total     = tasks.size();
        long completed = tasks.stream()
                .filter(t -> STATUS_COMPLETED.equals(t.getStatus()))
                .count();
        long   pending    = total - completed;
        double efficiency = total > 0 ? (completed * 100.0 / total) : 0.0;

        LOGGER.info(() -> String.format(
                "Report — total=%d, completed=%d, pending=%d, efficiency=%.2f%%",
                total, completed, pending, efficiency));

        return new ProductivityReport(total, completed, pending, efficiency);
    }

    // =========================================================================
    // Validation helpers  (package-private so unit tests can reach them)
    // =========================================================================

    static void validateNotNull(Object value, String fieldName) {
        if (value == null) {
            throw new IllegalArgumentException(fieldName + " must not be null.");
        }
    }

    static void validatePositiveId(int id) {
        if (id <= 0) {
            throw new IllegalArgumentException(
                    "ID must be a positive integer, got: " + id);
        }
    }

    static void validateTitle(String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Task title must not be blank.");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException(
                    "Task title exceeds the maximum of " + MAX_TITLE_LENGTH + " characters.");
        }
    }

    static void validateDescription(String description) {
        if (description != null && description.length() > MAX_DESC_LENGTH) {
            throw new IllegalArgumentException(
                    "Description exceeds the maximum of " + MAX_DESC_LENGTH + " characters.");
        }
    }

    static void validateDueDate(Date dueDate) {
        if (dueDate == null) {
            throw new IllegalArgumentException("Due date must not be null.");
        }
        if (dueDate.toLocalDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Due date must be today or in the future, got: " + dueDate);
        }
    }

    static void validatePriority(String priority) {
        if (priority == null
                || (!priority.equalsIgnoreCase("Low")
                &&  !priority.equalsIgnoreCase("Medium")
                &&  !priority.equalsIgnoreCase("High"))) {
            throw new IllegalArgumentException(
                    "Priority must be Low, Medium, or High. Got: " + priority);
        }
    }

    static void validateStatus(String status) {
        if (!STATUS_PENDING.equalsIgnoreCase(status)
                && !STATUS_COMPLETED.equalsIgnoreCase(status)) {
            throw new IllegalArgumentException(
                    "Status must be '" + STATUS_PENDING + "' or '"
                            + STATUS_COMPLETED + "'. Got: " + status);
        }
    }

    // ── Private utilities ─────────────────────────────────────────────────────

    private static boolean containsIgnoreCase(String source, String lowerKeyword) {
        return source != null && source.toLowerCase().contains(lowerKeyword);
    }

    // =========================================================================
    // Nested value type — ProductivityReport
    // =========================================================================

    /**
     * Immutable snapshot of task productivity metrics.
     *
     * <p>Returned by {@link TaskService#generateReport()} so callers can
     * format the data however they choose without coupling the service to
     * any presentation layer.
     */
    public static final class ProductivityReport {

        private final long   total;
        private final long   completed;
        private final long   pending;
        private final double efficiencyPercent;

        ProductivityReport(long total, long completed,
                           long pending, double efficiencyPercent) {
            this.total             = total;
            this.completed         = completed;
            this.pending           = pending;
            this.efficiencyPercent = efficiencyPercent;
        }

        public long   getTotal()             { return total; }
        public long   getCompleted()         { return completed; }
        public long   getPending()           { return pending; }
        public double getEfficiencyPercent() { return efficiencyPercent; }

        /** Human-readable one-liner, suitable for a log line or status bar. */
        @Override
        public String toString() {
            return String.format(
                    "ProductivityReport{total=%d, completed=%d, pending=%d, efficiency=%.2f%%}",
                    total, completed, pending, efficiencyPercent);
        }
    }
}