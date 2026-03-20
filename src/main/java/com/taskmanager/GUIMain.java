package com.taskmanager;

import com.formdev.flatlaf.FlatDarkLaf;
import com.taskmanager.service.TaskService;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.Logger;


public final class GUIMain {

    private static final Logger LOGGER = Logger.getLogger(GUIMain.class.getName());

    /** Utility class — no instantiation needed. */
    private GUIMain() {}

    // -------------------------------------------------------------------------
    // Entry point
    // -------------------------------------------------------------------------

    public static void main(String[] args) {
        configureLogging();
        applyLookAndFeel();

        /*
         * All Swing component creation and manipulation must happen on the
         * Event Dispatch Thread to avoid race conditions and rendering artefacts.
         * SwingUtilities.invokeLater schedules the lambda on the EDT without
         * blocking the calling thread.
         */
        SwingUtilities.invokeLater(GUIMain::launchUI);
    }


    /**
     * Configures the root JUL logger with a concise console handler.
     * Replace or augment with a file handler / SLF4J bridge as required.
     */
    private static void configureLogging() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(Level.INFO);

        ConsoleHandler handler = new ConsoleHandler();
        handler.setLevel(Level.INFO);
        rootLogger.addHandler(handler);

        LOGGER.info("Logging initialised.");
    }

    /**
     * <p>Must be called <em>before</em> any Swing component is instantiated.
     * Falls back to the platform look-and-feel so the app remains usable even
     * if the FlatLaf dependency is absent (e.g. in a minimal test environment).
     */
    private static void applyLookAndFeel() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
            LOGGER.info("FlatDarkLaf look-and-feel applied.");
        } catch (Exception ex) {
            LOGGER.log(Level.WARNING,
                    "FlatDarkLaf unavailable — falling back to system look-and-feel.", ex);
            applySystemLookAndFeel();
        }
    }

    /** Last-resort fallback: apply whatever native L&F the OS provides. */
    private static void applySystemLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ex) {
            // Non-fatal: Swing will use the Metal L&F by default.
            LOGGER.log(Level.SEVERE, "Could not apply system look-and-feel.", ex);
        }
    }

    /**
     * Constructs the service layer and opens the main window.
     *
     * <p>Called exclusively from the EDT via {@link SwingUtilities#invokeLater}.
     */
    private static void launchUI() {
        LOGGER.info("Launching TaskManager UI on the Event Dispatch Thread.");

        // Service layer is created once here and injected into the UI.
        TaskService taskService = new TaskService();

        TaskUI ui = new TaskUI(taskService);
        ui.setVisible(true);

        LOGGER.info("TaskManager UI is now visible.");
    }
}