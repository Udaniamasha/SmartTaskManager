package com.taskmanager;

import com.taskmanager.model.Task;
import com.taskmanager.service.TaskService;

import javax.swing.*;
import javax.swing.border.AbstractBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.basic.BasicButtonUI;
import javax.swing.plaf.basic.BasicScrollBarUI;
import javax.swing.table.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.RoundRectangle2D;
import java.sql.Date;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Main application window for the Task Manager.
 *
 * Layout (top → bottom):
 *   STICKY: Header  (title + subtitle + KPI stat cards)
 *   ─────────────────────────────────────────────────
 *   SCROLLABLE:
 *     🔴 Overdue Tasks panel      (hidden when empty)
 *     🟡 Upcoming Tasks panel     (hidden when empty)
 *     Search bar + status filter
 *     Main task table
 *     Add-task form + action buttons
 *   ─────────────────────────────────────────────────
 *   STICKY: Status bar
 *
 * Key fix: the JScrollPane wrapping taskTable is created ONCE in buildTableCard()
 * and NEVER recreated. updateEmptyState() swaps only the viewport content,
 * preventing the "tasks not showing" bug caused by rebuilding the scroll pane
 * on every data load.
 */
public class TaskUI extends JFrame {

    private static final Logger LOGGER = Logger.getLogger(TaskUI.class.getName());

    // ── Domain constants ──────────────────────────────────────────────────────
    private static final String STATUS_PENDING   = "Pending";
    private static final String STATUS_COMPLETED = "Completed";

    private static final int UPCOMING_DAYS  = 3;
    private static final int ALERT_MAX_ROWS = 5;

    // ── Palette (design system) ───────────────────────────────────────────────
    private static final Color BG_BASE          = new Color(0x0B1220);  // --bg-main
    private static final Color BG_SURFACE       = new Color(0x111827);  // --bg-card
    private static final Color BG_SURFACE_ALT   = new Color(0x132235);  // --row-even
    private static final Color BG_SURFACE_HOVER = new Color(0x1E293B);  // --row-hover
    private static final Color BG_INPUT         = new Color(0x1E293B);  // --input-bg
    private static final Color BG_INPUT_FOCUS   = new Color(0x1E3A5F);
    private static final Color BG_OVERDUE       = new Color(0x3B0A0A);  // --danger-bg
    private static final Color BG_UPCOMING      = new Color(0x3A2A05);  // --warning-bg

    private static final Color ACCENT           = new Color(0x10B981);  // --btn-primary
    private static final Color ACCENT_DARK      = new Color(0x059669);
    private static final Color BTN_SECONDARY    = new Color(0x334155);  // --btn-secondary
    private static final Color BTN_SECONDARY_H  = new Color(0x475569);
    private static final Color DANGER           = new Color(0xEF4444);  // --btn-danger
    private static final Color DANGER_DARK      = new Color(0xDC2626);

    private static final Color BADGE_PENDING_BG   = new Color(0xF59E0B);
    private static final Color BADGE_COMPLETED_BG = new Color(0x22C55E);
    private static final Color BADGE_TEXT         = Color.BLACK;

    private static final Color URGENCY_RED    = new Color(0xFF6B6B);
    private static final Color URGENCY_ORANGE = new Color(0xFB923C);
    private static final Color URGENCY_YELLOW = new Color(0xFBBF24);
    private static final Color URGENCY_OVERDUE= new Color(0xEF4444);

    private static final Color TEXT_PRIMARY   = new Color(0xE5E7EB);  // --text-primary
    private static final Color TEXT_SECONDARY = new Color(0x9CA3AF);  // --text-secondary
    private static final Color TEXT_MUTED     = new Color(0x6B7280);  // --text-muted
    private static final Color TEXT_HEADER    = new Color(0xFFFFFF);

    private static final Color BORDER_NORMAL  = new Color(0x1F2937);  // --border
    private static final Color BORDER_INPUT   = new Color(0x334155);  // --input-border
    private static final Color BORDER_FOCUS   = new Color(0x3B82F6);  // --input-focus
    private static final Color BORDER_OVERDUE = new Color(0x7F1D1D);
    private static final Color BORDER_UPCOMING= new Color(0x78350F);
    private static final Color BORDER_HEADER  = new Color(0x1F2937);
    private static final Color SELECTION_BG   = new Color(0x1D4ED8);

    // ── Fonts ─────────────────────────────────────────────────────────────────
    @SuppressWarnings("SpellCheckingInspection")
    private static final String FONT_FAMILY = "Segoe UI";

    private static final Font FONT_HEADING     = new Font(FONT_FAMILY, Font.BOLD,  22);
    private static final Font FONT_SUBHEAD     = new Font(FONT_FAMILY, Font.PLAIN, 13);
    private static final Font FONT_CARD_NUM    = new Font(FONT_FAMILY, Font.BOLD,  30);
    private static final Font FONT_CARD_LBL    = new Font(FONT_FAMILY, Font.BOLD,  11);
    private static final Font FONT_LABEL       = new Font(FONT_FAMILY, Font.BOLD,  12);
    private static final Font FONT_INPUT       = new Font(FONT_FAMILY, Font.PLAIN, 13);
    private static final Font FONT_TABLE       = new Font(FONT_FAMILY, Font.PLAIN, 13);
    private static final Font FONT_TABLE_HDR   = new Font(FONT_FAMILY, Font.BOLD,  12);
    private static final Font FONT_BTN         = new Font(FONT_FAMILY, Font.BOLD,  13);
    private static final Font FONT_BADGE       = new Font(FONT_FAMILY, Font.BOLD,  11);
    private static final Font FONT_ALERT_TITLE = new Font(FONT_FAMILY, Font.BOLD,  13);
    private static final Font FONT_ALERT_ITEM  = new Font(FONT_FAMILY, Font.PLAIN, 12);
    private static final Font FONT_ALERT_BADGE = new Font(FONT_FAMILY, Font.BOLD,  11);
    private static final Font FONT_EMPTY       = new Font(FONT_FAMILY, Font.BOLD,  16);
    private static final Font FONT_EMPTY_SUB   = new Font(FONT_FAMILY, Font.PLAIN, 13);

    // ── Instance fields ───────────────────────────────────────────────────────
    private final TaskService service;

    private JTable            taskTable;
    private DefaultTableModel tableModel;
    private TableRowSorter<DefaultTableModel> rowSorter;

    /**
     * The ONE scroll pane wrapping taskTable — created once, never recreated.
     * updateEmptyState() swaps what's INSIDE the viewport, not the scroll pane itself.
     */
    private JScrollPane tableScrollPane;

    /** Card that holds tableScrollPane OR the empty-state panel. */
    private JPanel tableCardPanel;

    private JPanel alertContainer;

    private JTextField        txtTitle;
    private JTextField        txtDesc;
    private JTextField        txtSearch;
    private JSpinner          datePicker;
    private JComboBox<String> cbPriority;
    private JComboBox<String> cbFilter;

    private JButton btnAdd;
    private JButton btnComplete;
    private JButton btnDelete;
    private JButton btnRefresh;

    private JLabel lblTotalValue;
    private JLabel lblCompletedValue;
    private JLabel lblPendingValue;
    private JLabel lblStatus;

    // ── Constructor ───────────────────────────────────────────────────────────
    public TaskUI(TaskService service) {
        this.service = service;
        initComponents();
        loadTasksAsync();
    }

    // =========================================================================
    // UI Construction
    // =========================================================================

    private void initComponents() {
        setTitle("Task Manager");
        setSize(1150, 860);
        setMinimumSize(new Dimension(940, 700));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel shell = new JPanel(new BorderLayout(0, 0));
        shell.setBackground(BG_BASE);
        setContentPane(shell);

        // ── STICKY HEADER ─────────────────────────────────────────────────────
        JPanel stickyHeader = buildHeaderPanel();
        stickyHeader.setBackground(BG_BASE);
        stickyHeader.setBorder(BorderFactory.createEmptyBorder(20, 24, 0, 24));
        shell.add(stickyHeader, BorderLayout.NORTH);

        // ── SCROLLABLE CONTENT ────────────────────────────────────────────────
        // Use GridBagLayout so every section stretches to full width correctly.
        // BoxLayout with Integer.MAX_VALUE widths breaks Swing's layout math.
        JPanel scrollContent = new JPanel(new GridBagLayout());
        scrollContent.setBackground(BG_BASE);
        scrollContent.setBorder(BorderFactory.createEmptyBorder(12, 24, 16, 24));

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill      = GridBagConstraints.HORIZONTAL;
        gbc.weightx   = 1.0;
        gbc.weighty   = 0;
        gbc.gridx     = 0;
        gbc.gridwidth = 1;
        gbc.insets    = new Insets(0, 0, 10, 0);

        // Alert container (rebuilt on every data load)
        alertContainer = new JPanel();
        alertContainer.setOpaque(false);
        alertContainer.setLayout(new GridBagLayout());   // inner GridBag so alerts also stretch
        gbc.gridy = 0;
        scrollContent.add(alertContainer, gbc);

        // Toolbar (search + filter + refresh)
        JPanel toolbar = buildToolbar();
        gbc.gridy = 1;
        scrollContent.add(toolbar, gbc);

        // Table card — fixed height, full width
        buildTableCard();
        gbc.gridy   = 2;
        gbc.weighty = 0;
        gbc.ipady   = 340;  // forces 340px height without breaking width
        scrollContent.add(tableCardPanel, gbc);
        gbc.ipady = 0;      // reset for subsequent rows

        // Form + action buttons
        JPanel bottomArea = buildBottomArea();
        gbc.gridy   = 3;
        gbc.weighty = 1.0;  // absorbs remaining vertical space so page scroll works
        gbc.fill    = GridBagConstraints.BOTH;
        scrollContent.add(bottomArea, gbc);

        // Page-level scroll pane
        JScrollPane pageScroll = new JScrollPane(scrollContent);
        pageScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        pageScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        pageScroll.getVerticalScrollBar().setUnitIncrement(16);
        pageScroll.setBorder(BorderFactory.createEmptyBorder());
        pageScroll.setBackground(BG_BASE);
        pageScroll.getViewport().setBackground(BG_BASE);
        styleScrollBar(pageScroll, BG_BASE);

        // ── STICKY STATUS BAR ─────────────────────────────────────────────────
        shell.add(pageScroll,       BorderLayout.CENTER);
        shell.add(buildStatusBar(), BorderLayout.SOUTH);
    }

    // ── Header (sticky) ───────────────────────────────────────────────────────

    private JPanel buildHeaderPanel() {
        JPanel wrapper = new JPanel(new BorderLayout(0, 16));
        wrapper.setOpaque(false);

        JLabel title = new JLabel("Task Manager");
        title.setFont(FONT_HEADING);
        title.setForeground(TEXT_PRIMARY);

        JLabel sub = new JLabel("Track, manage and complete your work");
        sub.setFont(FONT_SUBHEAD);
        sub.setForeground(TEXT_SECONDARY);

        JPanel titleStack = new JPanel();
        titleStack.setOpaque(false);
        titleStack.setLayout(new BoxLayout(titleStack, BoxLayout.Y_AXIS));
        titleStack.add(title);
        titleStack.add(Box.createVerticalStrut(3));
        titleStack.add(sub);

        JPanel titleRow = new JPanel(new BorderLayout());
        titleRow.setOpaque(false);
        titleRow.add(titleStack, BorderLayout.WEST);

        wrapper.add(titleRow,         BorderLayout.NORTH);
        wrapper.add(buildStatCards(), BorderLayout.CENTER);
        wrapper.add(buildSeparator(), BorderLayout.SOUTH);
        return wrapper;
    }

    private JPanel buildStatCards() {
        JPanel panel = new JPanel(new GridLayout(1, 3, 16, 0));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(16, 0, 16, 0));

        lblTotalValue     = createStatLabel();
        lblCompletedValue = createStatLabel();
        lblPendingValue   = createStatLabel();

        panel.add(buildStatCard("TOTAL TASKS", lblTotalValue,     new Color(0x3B82F6)));
        panel.add(buildStatCard("COMPLETED",   lblCompletedValue, BADGE_COMPLETED_BG));
        panel.add(buildStatCard("PENDING",     lblPendingValue,   BADGE_PENDING_BG));
        return panel;
    }

    private JLabel createStatLabel() {
        JLabel l = new JLabel("–");
        l.setFont(FONT_CARD_NUM);
        l.setForeground(TEXT_PRIMARY);
        return l;
    }

    private JPanel buildStatCard(String label, JLabel valueLabel, Color accentColor) {
        JPanel card = new RoundedPanel(BG_SURFACE, 14);
        card.setLayout(new BorderLayout(0, 6));
        card.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 18));

        JPanel stripe = new StripePanel(accentColor);
        stripe.setLayout(new BorderLayout());
        stripe.add(valueLabel, BorderLayout.CENTER);

        JLabel lbl = new JLabel(label);
        lbl.setFont(FONT_CARD_LBL);
        lbl.setForeground(TEXT_MUTED);

        card.add(stripe, BorderLayout.CENTER);
        card.add(lbl,    BorderLayout.SOUTH);
        return card;
    }

    // =========================================================================
    // Alert Sections  (Overdue + Upcoming)
    // =========================================================================

    private void rebuildAlertSections(List<Task> allTasks) {
        LocalDate today  = LocalDate.now();
        LocalDate cutoff = today.plusDays(UPCOMING_DAYS);

        List<Task> overdue = allTasks.stream()
                .filter(t -> !STATUS_COMPLETED.equals(t.getStatus()))
                .filter(t -> t.getDueDate() != null
                        && t.getDueDate().toLocalDate().isBefore(today))
                .sorted(Comparator.comparing(t -> t.getDueDate().toLocalDate()))
                .limit(ALERT_MAX_ROWS)
                .collect(Collectors.toList());

        List<Task> upcoming = allTasks.stream()
                .filter(t -> !STATUS_COMPLETED.equals(t.getStatus()))
                .filter(t -> {
                    if (t.getDueDate() == null) return false;
                    LocalDate due = t.getDueDate().toLocalDate();
                    return !due.isBefore(today) && !due.isAfter(cutoff);
                })
                .sorted(Comparator.comparing(t -> t.getDueDate().toLocalDate()))
                .limit(ALERT_MAX_ROWS)
                .collect(Collectors.toList());

        alertContainer.removeAll();

        // alertContainer uses GridBagLayout so cards fill the full width
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill    = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1.0;
        gbc.gridx   = 0;
        gbc.insets  = new Insets(0, 0, 8, 0);

        int row = 0;
        if (!overdue.isEmpty()) {
            gbc.gridy = row++;
            alertContainer.add(buildOverduePanel(overdue), gbc);
        }
        if (!upcoming.isEmpty()) {
            gbc.gridy = row;
            alertContainer.add(buildUpcomingPanel(upcoming), gbc);
        }

        // If either panel was added, push a spacer below the container
        if (!overdue.isEmpty() || !upcoming.isEmpty()) {
            gbc.gridy  = row + 1;
            gbc.insets = new Insets(0, 0, 0, 0);
            alertContainer.add(Box.createVerticalStrut(2), gbc);
        }

        alertContainer.revalidate();
        alertContainer.repaint();
    }

    private JPanel buildOverduePanel(List<Task> tasks) {
        JPanel card = new TintedCard(BG_OVERDUE, BORDER_OVERDUE, 12);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

        // Header row
        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel("[!]  Overdue Tasks");   // ASCII safe — no broken emoji
        title.setFont(FONT_ALERT_TITLE);
        title.setForeground(URGENCY_OVERDUE);

        JLabel count = new JLabel(tasks.size() + " task" + (tasks.size() > 1 ? "s" : ""));
        count.setFont(FONT_BADGE);
        count.setForeground(TEXT_MUTED);

        header.add(title, BorderLayout.WEST);
        header.add(count, BorderLayout.EAST);

        // Task rows — GridBagLayout so each row fills card width
        JPanel rows = new JPanel(new GridBagLayout());
        rows.setOpaque(false);

        GridBagConstraints rg = new GridBagConstraints();
        rg.fill    = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1.0;
        rg.gridx   = 0;
        rg.insets  = new Insets(3, 0, 3, 0);

        LocalDate today = LocalDate.now();
        int r = 0;
        for (Task t : tasks) {
            long   daysAgo = ChronoUnit.DAYS.between(t.getDueDate().toLocalDate(), today);
            String badge   = daysAgo == 0 ? "Due today"
                    : daysAgo == 1 ? "1 day ago"
                    : daysAgo + " days ago";
            rg.gridy = r++;
            rows.add(buildAlertRow("!", t.getTitle(), t.getDueDate().toString(),
                    badge, URGENCY_OVERDUE, new Color(0x4A1010)), rg);
        }

        card.add(header, BorderLayout.NORTH);
        card.add(rows,   BorderLayout.CENTER);
        return card;
    }

    private JPanel buildUpcomingPanel(List<Task> tasks) {
        JPanel card = new TintedCard(BG_UPCOMING, BORDER_UPCOMING, 12);
        card.setLayout(new BorderLayout(0, 8));
        card.setBorder(BorderFactory.createEmptyBorder(14, 18, 14, 18));

        JPanel header = new JPanel(new BorderLayout());
        header.setOpaque(false);

        JLabel title = new JLabel(">>  Upcoming - Next " + UPCOMING_DAYS + " Days");
        title.setFont(FONT_ALERT_TITLE);
        title.setForeground(URGENCY_YELLOW);

        JLabel count = new JLabel(tasks.size() + " task" + (tasks.size() > 1 ? "s" : ""));
        count.setFont(FONT_BADGE);
        count.setForeground(TEXT_MUTED);

        header.add(title, BorderLayout.WEST);
        header.add(count, BorderLayout.EAST);

        JPanel rows = new JPanel(new GridBagLayout());
        rows.setOpaque(false);

        GridBagConstraints rg = new GridBagConstraints();
        rg.fill    = GridBagConstraints.HORIZONTAL;
        rg.weightx = 1.0;
        rg.gridx   = 0;
        rg.insets  = new Insets(3, 0, 3, 0);

        LocalDate today = LocalDate.now();
        int r = 0;
        for (Task t : tasks) {
            LocalDate due     = t.getDueDate().toLocalDate();
            long      days    = ChronoUnit.DAYS.between(today, due);
            Color     urgency = urgencyColor(days);
            Color     badgeBg = urgencyBadgeBg(days);
            String    label   = days == 0 ? "Due today"
                    : days == 1 ? "Due tomorrow"
                    : "Due in " + days + " days";
            rg.gridy = r++;
            rows.add(buildAlertRow("*", t.getTitle(), t.getDueDate().toString(),
                    label, urgency, badgeBg), rg);
        }

        card.add(header, BorderLayout.NORTH);
        card.add(rows,   BorderLayout.CENTER);
        return card;
    }

    private JPanel buildAlertRow(String dot, String taskTitle, String dateStr,
                                 String badgeText, Color textColor, Color badgeBg) {
        // GridBagLayout gives predictable left/right alignment inside any parent
        JPanel row = new JPanel(new GridBagLayout());
        row.setOpaque(false);

        GridBagConstraints g = new GridBagConstraints();

        // Dot indicator
        JLabel dotLbl = new JLabel(dot);
        dotLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 11));
        dotLbl.setForeground(textColor);
        dotLbl.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 8));
        g.gridx = 0; g.gridy = 0; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(0, 0, 0, 0);
        row.add(dotLbl, g);

        // Title — expands to fill space
        JLabel titleLbl = new JLabel(truncate(taskTitle, 50));
        titleLbl.setFont(FONT_ALERT_ITEM);
        titleLbl.setForeground(TEXT_PRIMARY);
        titleLbl.setToolTipText(taskTitle);
        g.gridx = 1; g.weightx = 1.0; g.fill = GridBagConstraints.HORIZONTAL;
        row.add(titleLbl, g);

        // Date
        JLabel dateLbl = new JLabel(dateStr);
        dateLbl.setFont(FONT_ALERT_ITEM);
        dateLbl.setForeground(TEXT_MUTED);
        dateLbl.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 10));
        g.gridx = 2; g.weightx = 0; g.fill = GridBagConstraints.NONE;
        g.anchor = GridBagConstraints.EAST;
        row.add(dateLbl, g);

        // Badge pill
        JLabel badgeLbl = new JLabel(badgeText);
        badgeLbl.setFont(FONT_ALERT_BADGE);
        badgeLbl.setForeground(textColor.equals(URGENCY_OVERDUE) ? new Color(0xFF8A8A) : textColor);
        badgeLbl.setOpaque(true);
        badgeLbl.setBackground(badgeBg);
        badgeLbl.setBorder(BorderFactory.createEmptyBorder(2, 10, 2, 10));
        g.gridx = 3;
        row.add(badgeLbl, g);

        return row;
    }

    private static Color urgencyColor(long days) {
        if (days <= 1) return URGENCY_RED;
        if (days == 2) return URGENCY_ORANGE;
        return URGENCY_YELLOW;
    }

    private static Color urgencyBadgeBg(long days) {
        if (days <= 1) return new Color(0x4A1515);
        if (days == 2) return new Color(0x3D2800);
        return new Color(0x2E2800);
    }

    private static String truncate(String s, int maxLen) {
        return (s != null && s.length() > maxLen) ? s.substring(0, maxLen) + "…" : s;
    }

    // ── Toolbar ───────────────────────────────────────────────────────────────

    private JPanel buildToolbar() {
        JPanel toolbar = new JPanel(new BorderLayout(12, 0));
        toolbar.setOpaque(false);
        toolbar.add(buildSearchBox(), BorderLayout.CENTER);
        toolbar.add(buildFilterRow(), BorderLayout.EAST);
        return toolbar;
    }

    private JPanel buildSearchBox() {
        FocusAwarePanel box = new FocusAwarePanel();
        box.setPreferredSize(new Dimension(0, 40));
        box.setBorder(BorderFactory.createEmptyBorder(2, 12, 2, 12));

        JLabel iconLbl = new JLabel("[S]");
        iconLbl.setFont(new Font(FONT_FAMILY, Font.BOLD, 11));
        iconLbl.setForeground(TEXT_MUTED);

        txtSearch = new JTextField();
        txtSearch.setOpaque(false);
        txtSearch.setBorder(BorderFactory.createEmptyBorder());
        txtSearch.setFont(FONT_INPUT);
        txtSearch.setForeground(TEXT_PRIMARY);
        txtSearch.setCaretColor(ACCENT);
        txtSearch.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) { box.setFocused(true);  }
            public void focusLost (FocusEvent e)  { box.setFocused(false); }
        });
        txtSearch.getDocument().addDocumentListener(new DocumentListener() {
            public void insertUpdate(DocumentEvent e)  { applyFilters(); }
            public void removeUpdate(DocumentEvent e)  { applyFilters(); }
            public void changedUpdate(DocumentEvent e) { applyFilters(); }
        });

        JPanel fieldWrap = new JPanel(new BorderLayout());
        fieldWrap.setOpaque(false);
        fieldWrap.add(txtSearch);

        box.add(iconLbl,   BorderLayout.WEST);
        box.add(fieldWrap, BorderLayout.CENTER);
        return box;
    }

    private JPanel buildFilterRow() {
        cbFilter = styledCombo(new String[]{"All Statuses", STATUS_PENDING, STATUS_COMPLETED});
        cbFilter.setPreferredSize(new Dimension(165, 40));
        cbFilter.addActionListener(e -> applyFilters());

        btnRefresh = buildIconButton("↻  Refresh", BTN_SECONDARY, TEXT_PRIMARY);
        btnRefresh.addActionListener(e -> loadTasksAsync());

        JLabel statusLbl = new JLabel("Status:");
        statusLbl.setForeground(TEXT_SECONDARY);
        statusLbl.setFont(FONT_LABEL);

        JPanel row = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        row.setOpaque(false);
        row.add(statusLbl);
        row.add(cbFilter);
        row.add(Box.createHorizontalStrut(4));
        row.add(btnRefresh);
        return row;
    }

    // ── Main task table ───────────────────────────────────────────────────────

    /**
     * Builds the table card and sets {@link #tableCardPanel} and
     * {@link #tableScrollPane}. The scroll pane is created HERE and
     * never recreated — this is the root fix for tasks not showing.
     */
    private void buildTableCard() {
        String[] cols = {"ID", "Title", "Description", "Priority", "Due Date", "Status"};

        tableModel = new DefaultTableModel(cols, 0) {
            @Override public boolean isCellEditable(int r, int c) { return false; }
        };

        taskTable = new HoverZebraTable(tableModel);
        styleTable(taskTable);

        rowSorter = new TableRowSorter<>(tableModel);
        taskTable.setRowSorter(rowSorter);
        taskTable.getColumnModel().getColumn(5).setCellRenderer(new BadgeRenderer());
        taskTable.getColumnModel().getColumn(2).setCellRenderer(new EllipsisRenderer());

        int[] widths = {50, 190, 230, 85, 105, 115};
        for (int i = 0; i < widths.length; i++) {
            taskTable.getColumnModel().getColumn(i).setPreferredWidth(widths[i]);
        }

        taskTable.getSelectionModel().addListSelectionListener(e -> {
            boolean sel = taskTable.getSelectedRow() >= 0;
            btnComplete.setEnabled(sel);
            btnDelete.setEnabled(sel);
        });

        // Create the scroll pane ONCE — updateEmptyState() never recreates it
        tableScrollPane = new JScrollPane(taskTable);
        tableScrollPane.setBorder(BorderFactory.createEmptyBorder());
        tableScrollPane.getViewport().setBackground(BG_SURFACE);
        tableScrollPane.setBackground(BG_SURFACE);
        styleScrollBar(tableScrollPane, BG_SURFACE);

        // The card holds either the scroll pane or the empty-state panel
        tableCardPanel = new RoundedPanel(BG_SURFACE, 14);
        tableCardPanel.setLayout(new BorderLayout());
        tableCardPanel.add(tableScrollPane, BorderLayout.CENTER);  // start with table visible
    }

    private void styleTable(JTable table) {
        table.setBackground(BG_SURFACE);
        table.setForeground(TEXT_PRIMARY);
        table.setFont(FONT_TABLE);
        table.setRowHeight(42);
        table.setShowGrid(false);
        table.setIntercellSpacing(new Dimension(0, 0));
        table.setSelectionBackground(SELECTION_BG);
        table.setSelectionForeground(TEXT_PRIMARY);
        table.setFillsViewportHeight(true);

        JTableHeader header = table.getTableHeader();
        header.setBackground(BG_BASE);
        header.setForeground(TEXT_HEADER);
        header.setFont(FONT_TABLE_HDR);
        header.setBorder(BorderFactory.createMatteBorder(0, 0, 2, 0, BORDER_HEADER));
        header.setPreferredSize(new Dimension(0, 44));
        header.setReorderingAllowed(false);
        header.setDefaultRenderer(new UpperCaseHeaderRenderer(header.getDefaultRenderer()));

        DefaultTableCellRenderer center = new DefaultTableCellRenderer();
        center.setHorizontalAlignment(SwingConstants.CENTER);
        center.setBackground(BG_SURFACE);
        center.setForeground(TEXT_PRIMARY);
        for (int i : new int[]{0, 3, 4}) {
            table.getColumnModel().getColumn(i).setCellRenderer(center);
        }

        DefaultTableCellRenderer left = new DefaultTableCellRenderer();
        left.setHorizontalAlignment(SwingConstants.LEFT);
        left.setBackground(BG_SURFACE);
        left.setForeground(TEXT_PRIMARY);
        left.setBorder(BorderFactory.createEmptyBorder(0, 12, 0, 0));
        table.getColumnModel().getColumn(1).setCellRenderer(left);
    }

    /**
     * Reusable scroll-bar styler — used for both the table scroll pane
     * and the page-level scroll pane.
     *
     * @param scroll    the scroll pane to style
     * @param trackBg   background colour for the scroll track
     */
    private void styleScrollBar(JScrollPane scroll, Color trackBg) {
        scroll.getVerticalScrollBar().setUI(new BasicScrollBarUI() {
            @Override protected void configureScrollBarColors() {
                thumbColor = BORDER_INPUT;
                trackColor = trackBg;
            }
            @Override protected JButton createDecreaseButton(int o) { return zeroButton(); }
            @Override protected JButton createIncreaseButton(int o) { return zeroButton(); }
            private JButton zeroButton() {
                JButton b = new JButton();
                b.setPreferredSize(new Dimension(0, 0));
                return b;
            }
        });
    }

    /**
     * Swaps between the task table and the empty-state illustration.
     *
     * FIX: We no longer recreate the JScrollPane here. We swap only the
     * content of tableCardPanel using removeAll() + add() + revalidate().
     * The tableScrollPane (wrapping taskTable) is reused as-is.
     */
    private void updateEmptyState(int rowCount) {
        tableCardPanel.removeAll();
        if (rowCount == 0) {
            tableCardPanel.add(buildEmptyState(), BorderLayout.CENTER);
        } else {
            // Re-add the SAME scroll pane — taskTable rows are already populated
            tableCardPanel.add(tableScrollPane, BorderLayout.CENTER);
        }
        tableCardPanel.revalidate();
        tableCardPanel.repaint();
    }

    private JPanel buildEmptyState() {
        JPanel panel = new JPanel();
        panel.setOpaque(false);
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setBorder(BorderFactory.createEmptyBorder(60, 0, 60, 0));

        JLabel icon = new JLabel("📋");
        icon.setFont(new Font(FONT_FAMILY, Font.PLAIN, 48));
        icon.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel heading = new JLabel("No tasks yet");
        heading.setFont(FONT_EMPTY);
        heading.setForeground(TEXT_PRIMARY);
        heading.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel sub = new JLabel("Add your first task using the form below");
        sub.setFont(FONT_EMPTY_SUB);
        sub.setForeground(TEXT_SECONDARY);
        sub.setAlignmentX(Component.CENTER_ALIGNMENT);

        panel.add(Box.createVerticalGlue());
        panel.add(icon);
        panel.add(Box.createVerticalStrut(16));
        panel.add(heading);
        panel.add(Box.createVerticalStrut(8));
        panel.add(sub);
        panel.add(Box.createVerticalGlue());
        return panel;
    }

    // ── Bottom area ───────────────────────────────────────────────────────────

    private JPanel buildBottomArea() {
        JPanel panel = new JPanel(new BorderLayout(0, 10));
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(12, 0, 0, 0));
        panel.add(buildFormCard(),  BorderLayout.CENTER);
        panel.add(buildActionBar(), BorderLayout.SOUTH);
        return panel;
    }

    private JPanel buildFormCard() {
        JPanel card = new RoundedPanel(BG_SURFACE, 14);
        card.setLayout(new BorderLayout(0, 12));
        card.setBorder(BorderFactory.createEmptyBorder(18, 22, 18, 22));

        JLabel heading = new JLabel("Add New Task");
        heading.setFont(new Font(FONT_FAMILY, Font.BOLD, 14));
        heading.setForeground(TEXT_PRIMARY);
        heading.setBorder(BorderFactory.createEmptyBorder(0, 0, 6, 0));

        JPanel grid = new JPanel(new GridBagLayout());
        grid.setOpaque(false);
        GridBagConstraints gc = new GridBagConstraints();
        gc.insets  = new Insets(5, 8, 5, 8);
        gc.fill    = GridBagConstraints.HORIZONTAL;
        gc.anchor  = GridBagConstraints.WEST;

        txtTitle = styledTextField();
        txtDesc  = styledTextField();
        gc.gridy = 0;
        addFormRow(grid, gc, 0, "Title",       txtTitle);
        addFormRow(grid, gc, 2, "Description", txtDesc);

        cbPriority = styledCombo(new String[]{"Low", "Medium", "High"});
        cbPriority.setSelectedItem("Medium");
        datePicker = buildDatePicker();

        gc.gridy = 1;
        addFormRow(grid, gc, 0, "Priority", cbPriority);
        addFormRow(grid, gc, 2, "Due Date", datePicker);

        card.add(heading, BorderLayout.NORTH);
        card.add(grid,    BorderLayout.CENTER);
        return card;
    }

    private JSpinner buildDatePicker() {
        SpinnerDateModel model = new SpinnerDateModel();
        JSpinner spinner = new JSpinner(model);
        spinner.setEditor(new JSpinner.DateEditor(spinner, "yyyy-MM-dd"));

        JSpinner.DateEditor editor = (JSpinner.DateEditor) spinner.getEditor();
        editor.getTextField().setBackground(BG_INPUT);
        editor.getTextField().setForeground(TEXT_PRIMARY);
        editor.getTextField().setCaretColor(ACCENT);
        editor.getTextField().setFont(FONT_INPUT);
        editor.getTextField().setBorder(BorderFactory.createEmptyBorder(4, 8, 4, 8));
        editor.getTextField().addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) { spinner.setBorder(new RoundBorder(BORDER_FOCUS, 8)); }
            public void focusLost (FocusEvent e)  { spinner.setBorder(new RoundBorder(BORDER_INPUT, 8)); }
        });

        spinner.setBackground(BG_INPUT);
        spinner.setBorder(new RoundBorder(BORDER_INPUT, 8));
        spinner.setPreferredSize(new Dimension(0, 36));
        return spinner;
    }

    private void addFormRow(JPanel grid, GridBagConstraints gc,
                            int startX, String labelText, JComponent field) {
        gc.gridx   = startX;
        gc.weightx = 0;
        JLabel lbl = new JLabel(labelText);
        lbl.setFont(FONT_LABEL);
        lbl.setForeground(TEXT_SECONDARY);
        grid.add(lbl, gc);
        gc.gridx   = startX + 1;
        gc.weightx = 1.0;
        grid.add(field, gc);
    }

    private JPanel buildActionBar() {
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
        bar.setOpaque(false);

        btnAdd      = buildIconButton("＋  Add Task", ACCENT,       Color.BLACK);
        btnComplete = buildIconButton("✔  Complete",  BTN_SECONDARY, BADGE_COMPLETED_BG);
        btnDelete   = buildIconButton("✕  Delete",    DANGER,        Color.WHITE);

        btnAdd.setFont(FONT_BTN);
        btnComplete.setFont(FONT_BTN);
        btnDelete.setFont(FONT_BTN);

        btnComplete.setEnabled(false);
        btnDelete.setEnabled(false);

        btnAdd.addActionListener(e      -> submitNewTask());
        btnComplete.addActionListener(e -> updateTaskStatus());
        btnDelete.addActionListener(e   -> deleteSelectedTask());

        bar.add(btnComplete);
        bar.add(btnDelete);
        bar.add(btnAdd);
        return bar;
    }

    // ── Status bar ────────────────────────────────────────────────────────────

    private JPanel buildStatusBar() {
        JPanel bar = new JPanel(new BorderLayout());
        bar.setOpaque(false);
        bar.setBackground(BG_BASE);
        bar.setBorder(BorderFactory.createEmptyBorder(8, 24, 8, 24));

        lblStatus = new JLabel(" ");
        lblStatus.setFont(FONT_SUBHEAD);
        lblStatus.setForeground(TEXT_MUTED);

        bar.add(buildSeparator(), BorderLayout.NORTH);
        bar.add(lblStatus,        BorderLayout.CENTER);
        return bar;
    }

    private JSeparator buildSeparator() {
        JSeparator sep = new JSeparator();
        sep.setForeground(BORDER_NORMAL);
        sep.setBackground(BORDER_NORMAL);
        return sep;
    }

    // =========================================================================
    // Widget factories
    // =========================================================================

    private JTextField styledTextField() {
        JTextField f = new JTextField();
        f.setFont(FONT_INPUT);
        f.setForeground(TEXT_PRIMARY);
        f.setBackground(BG_INPUT);
        f.setCaretColor(ACCENT);
        f.setBorder(BorderFactory.createCompoundBorder(
                new RoundBorder(BORDER_INPUT, 8),
                BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        f.addFocusListener(new FocusAdapter() {
            public void focusGained(FocusEvent e) {
                f.setBackground(BG_INPUT_FOCUS);
                f.setBorder(BorderFactory.createCompoundBorder(
                        new RoundBorder(BORDER_FOCUS, 8),
                        BorderFactory.createEmptyBorder(6, 10, 6, 10)));
            }
            public void focusLost(FocusEvent e) {
                f.setBackground(BG_INPUT);
                f.setBorder(BorderFactory.createCompoundBorder(
                        new RoundBorder(BORDER_INPUT, 8),
                        BorderFactory.createEmptyBorder(6, 10, 6, 10)));
            }
        });
        return f;
    }

    private <T> JComboBox<T> styledCombo(T[] items) {
        JComboBox<T> cb = new JComboBox<>(items);
        cb.setFont(FONT_INPUT);
        cb.setForeground(TEXT_PRIMARY);
        cb.setBackground(BG_INPUT);
        cb.setBorder(BorderFactory.createCompoundBorder(
                new RoundBorder(BORDER_INPUT, 8),
                BorderFactory.createEmptyBorder(4, 4, 4, 4)));
        cb.setRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(
                    JList<?> list, Object val, int idx, boolean sel, boolean focus) {
                super.getListCellRendererComponent(list, val, idx, sel, focus);
                setBackground(sel ? ACCENT_DARK : BG_INPUT);
                setForeground(TEXT_PRIMARY);
                setFont(FONT_INPUT);
                setBorder(BorderFactory.createEmptyBorder(5, 12, 5, 12));
                return this;
            }
        });
        return cb;
    }

    private JButton buildIconButton(String text, Color bgColor, Color fgColor) {
        Color hoverBg = bgColor.equals(DANGER)       ? DANGER_DARK    :
                bgColor.equals(ACCENT)        ? ACCENT_DARK    :
                        bgColor.equals(BTN_SECONDARY) ? BTN_SECONDARY_H:
                                bgColor.brighter();
        return new HoverButton(text, bgColor, hoverBg, fgColor);
    }

    // =========================================================================
    // Business actions
    // =========================================================================

    private void applyFilters() {
        String text   = txtSearch.getText().toLowerCase();
        String status = cbFilter.getSelectedItem() != null
                ? cbFilter.getSelectedItem().toString() : "All Statuses";

        RowFilter<DefaultTableModel, Object> rf =
                new RowFilter<DefaultTableModel, Object>() {
                    @Override
                    public boolean include(Entry<? extends DefaultTableModel, ?> e) {
                        boolean matchText   = text.isEmpty()
                                || e.getStringValue(1).toLowerCase().contains(text)
                                || e.getStringValue(2).toLowerCase().contains(text);
                        boolean matchStatus = "All Statuses".equals(status)
                                || e.getStringValue(5).equals(status);
                        return matchText && matchStatus;
                    }
                };
        rowSorter.setRowFilter(rf);
    }

    private void loadTasksAsync() {
        setLoadingState(true);
        setStatus("Loading tasks…");

        new SwingWorker<List<Task>, Void>() {
            @Override protected List<Task> doInBackground() { return service.getAllTasks(); }

            @Override
            protected void done() {
                try {
                    List<Task> tasks = get();

                    // 1. Populate the model — the existing tableScrollPane renders it
                    tableModel.setRowCount(0);
                    long completed = 0;
                    for (Task t : tasks) {
                        tableModel.addRow(new Object[]{
                                t.getId(), t.getTitle(), t.getDescription(),
                                t.getPriority(), t.getDueDate(), t.getStatus()
                        });
                        if (STATUS_COMPLETED.equals(t.getStatus())) completed++;
                    }

                    long total   = tasks.size();
                    long pending = total - completed;

                    lblTotalValue.setText(String.valueOf(total));
                    lblCompletedValue.setText(String.valueOf(completed));
                    lblPendingValue.setText(String.valueOf(pending));

                    // 2. Show table or empty-state (reuses the same scroll pane)
                    updateEmptyState((int) total);

                    // 3. Refresh alert bands
                    rebuildAlertSections(tasks);

                    setStatus("Loaded " + total + " task" + (total != 1 ? "s" : "") + ".");

                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    showError("Load interrupted.");
                    LOGGER.log(Level.WARNING, "Task load interrupted", ex);
                } catch (ExecutionException ex) {
                    Throwable cause = ex.getCause();
                    String msg = cause != null ? cause.getMessage() : ex.getMessage();
                    showError("Failed to load tasks: " + (msg != null ? msg : "unknown error"));
                    LOGGER.log(Level.SEVERE, "Task load failed", ex);
                } finally {
                    setLoadingState(false);
                }
            }
        }.execute();
    }

    private void submitNewTask() {
        String title    = txtTitle.getText().trim();
        String desc     = txtDesc.getText().trim();
        String priority = (String) cbPriority.getSelectedItem();

        java.util.Date picked = (java.util.Date) ((SpinnerDateModel) datePicker.getModel()).getValue();
        Calendar cal = Calendar.getInstance();
        cal.setTime(picked);
        Date sqlDate = new Date(cal.getTimeInMillis());

        if (title.isEmpty()) { showError("Title is required."); txtTitle.requestFocus(); return; }

        try {
            Task task = new Task(title, desc, priority, sqlDate, STATUS_PENDING);
            if (service.addTask(task)) {
                clearForm();
                loadTasksAsync();
                setStatus("Task \"" + title + "\" added.");
            } else {
                showError("Could not save task. Please try again.");
            }
        } catch (IllegalArgumentException ex) {
            showError(ex.getMessage());
        }
    }

    private void updateTaskStatus() {
        int viewRow = taskTable.getSelectedRow();
        if (viewRow < 0) return;
        int    modelRow = taskTable.convertRowIndexToModel(viewRow);
        int    id       = (int)    tableModel.getValueAt(modelRow, 0);
        String title    = (String) tableModel.getValueAt(modelRow, 1);
        service.completeTask(id);
        loadTasksAsync();
        setStatus("Task \"" + title + "\" marked as completed.");
    }

    private void deleteSelectedTask() {
        int viewRow = taskTable.getSelectedRow();
        if (viewRow < 0) return;
        int    modelRow = taskTable.convertRowIndexToModel(viewRow);
        int    id       = (int)    tableModel.getValueAt(modelRow, 0);
        String title    = (String) tableModel.getValueAt(modelRow, 1);

        int confirm = JOptionPane.showConfirmDialog(
                this,
                "Are you sure you want to delete \"" + title + "\"?",
                "Confirm Delete",
                JOptionPane.YES_NO_OPTION,
                JOptionPane.WARNING_MESSAGE);

        if (confirm == JOptionPane.YES_OPTION) {
            service.deleteTask(id);
            loadTasksAsync();
            setStatus("Task \"" + title + "\" deleted.");
        }
    }

    // =========================================================================
    // UI helpers
    // =========================================================================

    private void setLoadingState(boolean loading) {
        btnRefresh.setEnabled(!loading);
        btnAdd.setEnabled(!loading);
        setCursor(loading ? Cursor.getPredefinedCursor(Cursor.WAIT_CURSOR) : Cursor.getDefaultCursor());
    }

    private void clearForm() {
        txtTitle.setText("");
        txtDesc.setText("");
        datePicker.setValue(new java.util.Date());
        cbPriority.setSelectedItem("Medium");
    }

    private void showError(String msg) {
        JOptionPane.showMessageDialog(this, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    private void setStatus(String msg) {
        if (lblStatus != null) lblStatus.setText(msg);
    }

    // =========================================================================
    // Static nested component classes
    // =========================================================================

    private static class RoundedPanel extends JPanel {
        private final Color bg;
        private final int   radius;
        RoundedPanel(Color bg, int radius) { this.bg = bg; this.radius = radius; setOpaque(false); }

        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class TintedCard extends JPanel {
        private final Color bg, border;
        private final int   radius;
        TintedCard(Color bg, Color border, int radius) {
            this.bg = bg; this.border = border; this.radius = radius; setOpaque(false);
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(bg);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), radius, radius));
            g2.setColor(border);
            g2.setStroke(new BasicStroke(1.2f));
            g2.draw(new RoundRectangle2D.Float(0.6f, 0.6f, getWidth()-1.2f, getHeight()-1.2f, radius, radius));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class StripePanel extends JPanel {
        private final Color stripe;
        StripePanel(Color stripe) { this.stripe = stripe; setOpaque(false); }
        @Override protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(stripe);
            g2.fill(new RoundRectangle2D.Float(0, 0, 5, getHeight(), 4, 4));
            g2.dispose();
        }
    }

    private static class FocusAwarePanel extends JPanel {
        private boolean focused = false;
        FocusAwarePanel() { super(new BorderLayout(8, 0)); setOpaque(false); }
        void setFocused(boolean f) { this.focused = f; repaint(); }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(BG_INPUT);
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
            g2.setColor(focused ? BORDER_FOCUS : BORDER_NORMAL);
            g2.setStroke(focused ? new BasicStroke(1.8f) : new BasicStroke(1f));
            g2.draw(new RoundRectangle2D.Float(0, 0, getWidth()-1, getHeight()-1, 10, 10));
            g2.dispose();
        }
    }

    private static class HoverZebraTable extends JTable {
        private int hoveredRow = -1;
        HoverZebraTable(TableModel model) {
            super(model);
            addMouseMotionListener(new MouseMotionAdapter() {
                @Override public void mouseMoved(MouseEvent e) {
                    int r = rowAtPoint(e.getPoint());
                    if (r != hoveredRow) { hoveredRow = r; repaint(); }
                }
            });
            addMouseListener(new MouseAdapter() {
                @Override public void mouseExited(MouseEvent e) { hoveredRow = -1; repaint(); }
            });
        }
        @Override
        public Component prepareRenderer(TableCellRenderer r, int row, int col) {
            Component c = super.prepareRenderer(r, row, col);
            if (!isRowSelected(row)) {
                c.setBackground(row == hoveredRow ? BG_SURFACE_HOVER
                        : (row % 2 == 0 ? new Color(0x0F1B2A) : BG_SURFACE_ALT));
                c.setForeground(TEXT_PRIMARY);
            }
            return c;
        }
    }

    private static class HoverButton extends JButton {
        private final Color normalBg, hoverBg;
        private boolean hovered = false;
        HoverButton(String text, Color normalBg, Color hoverBg, Color fg) {
            super(text);
            this.normalBg = normalBg; this.hoverBg = hoverBg;
            setUI(new BasicButtonUI());
            setContentAreaFilled(false); setFocusPainted(false);
            setBorderPainted(false); setOpaque(false); setForeground(fg);
            setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            setBorder(BorderFactory.createEmptyBorder(8, 20, 8, 20));
            addMouseListener(new MouseAdapter() {
                @Override public void mouseEntered(MouseEvent e) { hovered = true;  repaint(); }
                @Override public void mouseExited (MouseEvent e) { hovered = false; repaint(); }
            });
        }
        @Override protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(isEnabled() ? (hovered ? hoverBg : normalBg) : new Color(0x1A2535));
            g2.fill(new RoundRectangle2D.Float(0, 0, getWidth(), getHeight(), 10, 10));
            g2.dispose();
            super.paintComponent(g);
        }
    }

    private static class BadgeRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int col) {
            JPanel cell = new JPanel(new GridBagLayout());
            cell.setBackground(isSelected ? table.getSelectionBackground()
                    : (row % 2 == 0 ? BG_SURFACE : BG_SURFACE_ALT));

            String status = value == null ? "" : value.toString();
            boolean done  = STATUS_COMPLETED.equals(status);
            Color   bg    = done ? BADGE_COMPLETED_BG : BADGE_PENDING_BG;

            JLabel pill = new JLabel(status);
            pill.setFont(FONT_BADGE);
            pill.setForeground(BADGE_TEXT);
            pill.setOpaque(true);
            pill.setBackground(bg);
            pill.setBorder(BorderFactory.createEmptyBorder(3, 12, 3, 12));

            JPanel pillPanel = new RoundedPanel(bg, 12);
            pillPanel.setLayout(new BorderLayout());
            pillPanel.add(pill);

            cell.add(pillPanel);
            return cell;
        }
    }

    private static class EllipsisRenderer extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int col) {
            super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, col);
            String text = value != null ? value.toString() : "";
            setText(text);
            setToolTipText(text.isEmpty() ? null : text);
            setForeground(isSelected ? TEXT_PRIMARY : TEXT_SECONDARY);
            setBackground(isSelected ? table.getSelectionBackground()
                    : (row % 2 == 0 ? BG_SURFACE : BG_SURFACE_ALT));
            setBorder(BorderFactory.createEmptyBorder(0, 8, 0, 8));
            return this;
        }
    }

    private static class UpperCaseHeaderRenderer implements TableCellRenderer {
        private final TableCellRenderer delegate;
        UpperCaseHeaderRenderer(TableCellRenderer delegate) { this.delegate = delegate; }
        @Override
        public Component getTableCellRendererComponent(
                JTable table, Object value, boolean isSelected,
                boolean hasFocus, int row, int col) {
            String upper = value != null ? value.toString().toUpperCase() : "";
            Component c  = delegate.getTableCellRendererComponent(
                    table, upper, isSelected, hasFocus, row, col);
            c.setForeground(TEXT_HEADER);
            c.setFont(FONT_TABLE_HDR);
            return c;
        }
    }

    private static class RoundBorder extends AbstractBorder {
        private final Color color;
        private final int   radius;
        RoundBorder(Color color, int radius) { this.color = color; this.radius = radius; }
        @Override
        public void paintBorder(Component c, Graphics g, int x, int y, int w, int h) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g2.setColor(color);
            g2.draw(new RoundRectangle2D.Float(x, y, w-1, h-1, radius, radius));
            g2.dispose();
        }
        @Override
        public Insets getBorderInsets(Component c) {
            int r = radius / 2; return new Insets(r, r, r, r);
        }
    }
}