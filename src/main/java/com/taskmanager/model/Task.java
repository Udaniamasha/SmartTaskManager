package com.taskmanager.model;

import java.sql.Date;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Immutable-by-default domain model representing a single task.
 *
 * <h2>Design decisions</h2>
 * <ul>
 *   <li>All fields are private; access is strictly through getters/setters.</li>
 *   <li>Setters on mutable fields validate their input so a {@code Task} object
 *       can never be placed into an illegal state after construction.</li>
 *   <li>{@code equals} and {@code hashCode} are based on {@code id} alone —
 *       consistent with how the database identifies rows.</li>
 *   <li>{@link java.sql.Date} is a mutable class; getters and setters return /
 *       accept defensive copies so callers cannot mutate the stored date.</li>
 * </ul>
 *
 * <h2>Allowed values</h2>
 * <pre>
 *   priority : "Low" | "Medium" | "High"
 *   status   : "Pending" | "Completed"
 * </pre>
 */
public class Task {

    // ── Allowed domain values ─────────────────────────────────────────────────

    /** All legal values for {@link #priority}. */
    public static final String[] VALID_PRIORITIES = {"Low", "Medium", "High"};

    /** All legal values for {@link #status}. */
    public static final String[] VALID_STATUSES   = {"Pending", "Completed"};

    // ── Fields ────────────────────────────────────────────────────────────────

    /** Database-assigned primary key. {@code 0} means "not yet persisted". */
    private int    id;

    /** Short, human-readable summary. Required; max 150 characters. */
    private String title;

    /** Optional longer description. Max 500 characters. */
    private String description;

    /** Task urgency: Low, Medium, or High. */
    private String priority;

    /** Calendar date by which the task must be completed. Required. */
    private Date   dueDate;

    /** Lifecycle state: Pending or Completed. */
    private String status;

    // =========================================================================
    // Constructors
    // =========================================================================

    /**
     * No-arg constructor required by frameworks (e.g. JDBC mapping, Jackson).
     * Fields default to {@code null} / {@code 0}; populate via setters before use.
     */
    public Task() {}

    /**
     * Convenience constructor for creating a brand-new task (before it has a
     * database ID).  Every parameter is validated through the corresponding
     * setter so the same rules apply regardless of how a {@code Task} is built.
     *
     * @param title       short task summary (required, ≤ 150 chars)
     * @param description optional detail (may be {@code null}, ≤ 500 chars)
     * @param priority    Low, Medium, or High
     * @param dueDate     due date (required)
     * @param status      Pending or Completed
     */
    public Task(String title, String description,
                String priority, Date dueDate, String status) {
        setTitle      (title);
        setDescription(description);
        setPriority   (priority);
        setDueDate    (dueDate);
        setStatus     (status);
    }

    /**
     * Full constructor including the database-assigned {@code id}.
     * Typically used when reconstructing a {@code Task} from a {@link java.sql.ResultSet}.
     *
     * @param id          the database primary key (must be positive)
     * @param title       short task summary
     * @param description optional detail
     * @param priority    Low, Medium, or High
     * @param dueDate     due date (required)
     * @param status      Pending or Completed
     */
    public Task(int id, String title, String description,
                String priority, Date dueDate, String status) {
        this(title, description, priority, dueDate, status);
        setId(id);
    }

    // =========================================================================
    // Getters and setters
    // =========================================================================

    /**
     * Returns the database primary key.
     * A value of {@code 0} indicates the task has not yet been persisted.
     */
    public int getId() { return id; }

    /**
     * Sets the database primary key.
     * Should only be called by the DAO layer after a successful INSERT.
     *
     * @param id must be a positive integer
     * @throws IllegalArgumentException if {@code id} is not positive
     */
    public void setId(int id) {
        if (id <= 0) throw new IllegalArgumentException(
                "Task id must be a positive integer, got: " + id);
        this.id = id;
    }

    /** Returns the task title. */
    public String getTitle() { return title; }

    /**
     * Sets the task title.
     *
     * @param title must be non-blank and at most 150 characters
     * @throws IllegalArgumentException if the title is blank or too long
     */
    public void setTitle(String title) {
        requireNonBlank(title, "Title");
        requireMaxLength(title, 150, "Title");
        this.title = title.trim();
    }

    /** Returns the optional description, or {@code null} if not set. */
    public String getDescription() { return description; }

    /**
     * Sets the optional description.
     *
     * @param description may be {@code null}; if provided, must be ≤ 500 characters
     * @throws IllegalArgumentException if the description exceeds 500 characters
     */
    public void setDescription(String description) {
        if (description != null) requireMaxLength(description, 500, "Description");
        this.description = description == null ? null : description.trim();
    }

    /** Returns the priority level: Low, Medium, or High. */
    public String getPriority() { return priority; }

    /**
     * Sets the priority level.
     *
     * @param priority must be "Low", "Medium", or "High" (case-insensitive)
     * @throws IllegalArgumentException if the value is not recognised
     */
    public void setPriority(String priority) {
        this.priority = requireOneOf(priority, VALID_PRIORITIES, "Priority");
    }

    /**
     * Returns a defensive copy of the due date so callers cannot mutate the
     * stored value directly.
     */
    public Date getDueDate() {
        return dueDate == null ? null : new Date(dueDate.getTime());
    }

    /**
     * Sets the due date.
     *
     * @param dueDate must not be {@code null}
     * @throws IllegalArgumentException if {@code dueDate} is {@code null}
     */
    public void setDueDate(Date dueDate) {
        if (dueDate == null) throw new IllegalArgumentException("Due date must not be null.");
        // Store a defensive copy so the caller's reference can't mutate our field
        this.dueDate = new Date(dueDate.getTime());
    }

    /** Returns the lifecycle status: Pending or Completed. */
    public String getStatus() { return status; }

    /**
     * Sets the lifecycle status.
     *
     * @param status must be "Pending" or "Completed" (case-insensitive)
     * @throws IllegalArgumentException if the value is not recognised
     */
    public void setStatus(String status) {
        this.status = requireOneOf(status, VALID_STATUSES, "Status");
    }

    // =========================================================================
    // Derived helpers
    // =========================================================================

    /**
     * Returns {@code true} if this task has been persisted to the database
     * (i.e. its {@code id} has been set to a positive value by the DAO layer).
     */
    public boolean isPersisted() {
        return id > 0;
    }

    /**
     * Returns {@code true} if the task's due date is strictly before today.
     * Returns {@code false} if {@code dueDate} is {@code null}.
     */
    public boolean isOverdue() {
        return dueDate != null && dueDate.toLocalDate().isBefore(LocalDate.now());
    }

    // =========================================================================
    // Object overrides
    // =========================================================================

    /**
     * Two tasks are equal if and only if they share the same positive {@code id}.
     * Unpersisted tasks (id = 0) are never equal to each other.
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof Task other)) return false;
        return id > 0 && id == other.id;
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    /**
     * Returns a concise, human-readable summary suitable for logging.
     * Does not include the description to keep log lines short.
     *
     * <p>Example output:
     * <pre>Task{id=7, title="Fix login bug", priority=High, due=2025-09-01, status=Pending}</pre>
     */
    @Override
    public String toString() {
        return String.format(
                "Task{id=%d, title=\"%s\", priority=%s, due=%s, status=%s}",
                id, title, priority, dueDate, status);
    }

    // =========================================================================
    // Private validation helpers
    // =========================================================================

    private static void requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank.");
        }
    }

    private static void requireMaxLength(String value, int max, String fieldName) {
        if (value.length() > max) {
            throw new IllegalArgumentException(
                    fieldName + " must not exceed " + max + " characters "
                            + "(got " + value.length() + ").");
        }
    }

    /**
     * Returns the canonical form (first matching allowed value) of {@code value},
     * comparing case-insensitively.
     *
     * @throws IllegalArgumentException listing all allowed values if no match found
     */
    private static String requireOneOf(String value, String[] allowed, String fieldName) {
        if (value != null) {
            for (String a : allowed) {
                if (a.equalsIgnoreCase(value)) return a;   // normalise to canonical casing
            }
        }
        throw new IllegalArgumentException(
                fieldName + " must be one of " + java.util.Arrays.toString(allowed)
                        + ", got: \"" + value + "\".");
    }
}