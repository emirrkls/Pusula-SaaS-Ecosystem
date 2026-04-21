package com.pusula.desktop.util;

import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Dialog;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyCodeCombination;
import javafx.scene.input.KeyCombination;

/**
 * KeyboardShortcutHelper - Global keyboard shortcuts for premium UX.
 * Provides common shortcuts like Enter to submit, Escape to cancel, etc.
 */
public class KeyboardShortcutHelper {

    // =========================================
    // ENTER TO SUBMIT (Forms/Login)
    // =========================================

    /**
     * Makes Enter key trigger a button click.
     * Useful for login forms, search fields, dialogs.
     * 
     * @param triggerNode  The node to listen for Enter key (e.g., text field)
     * @param submitButton The button to click when Enter is pressed
     */
    public static void enterToSubmit(Node triggerNode, Button submitButton) {
        triggerNode.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                submitButton.fire();
                event.consume();
            }
        });
    }

    /**
     * Makes Enter key trigger a button click on the entire scene.
     * Sets the button as the default button for the scene.
     * 
     * @param scene        The scene to listen for Enter key
     * @param submitButton The button to click when Enter is pressed
     */
    public static void setDefaultButton(Scene scene, Button submitButton) {
        if (submitButton != null) {
            submitButton.setDefaultButton(true);
        }
    }

    // =========================================
    // ESCAPE TO CLOSE (Dialogs/Popups)
    // =========================================

    /**
     * Makes Escape key close a dialog.
     * 
     * @param dialog The dialog to close on Escape
     */
    public static void escapeToClose(Dialog<?> dialog) {
        dialog.getDialogPane().getScene().setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                dialog.close();
                event.consume();
            }
        });
    }

    /**
     * Adds Escape key handler to close/cancel action on a scene.
     * 
     * @param scene    The scene to listen for Escape
     * @param onEscape The action to run when Escape is pressed
     */
    public static void escapeToAction(Scene scene, Runnable onEscape) {
        scene.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ESCAPE) {
                onEscape.run();
                event.consume();
            }
        });
    }

    // =========================================
    // GLOBAL NAVIGATION SHORTCUTS
    // =========================================

    /**
     * Registers global keyboard shortcuts on a scene.
     * Common shortcuts:
     * - Ctrl+D: Dashboard
     * - Ctrl+S: Service Management
     * - Ctrl+I: Inventory
     * - Ctrl+F: Finance
     * - Ctrl+K: Customers
     * - Ctrl+L: Logout
     * - F11: Toggle Fullscreen
     * - F5: Refresh
     * 
     * @param scene     The scene to register shortcuts on
     * @param shortcuts A ShortcutHandler with callbacks for each action
     */
    public static void registerGlobalShortcuts(Scene scene, ShortcutHandler shortcuts) {
        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.D, KeyCombination.CONTROL_DOWN),
                shortcuts::onDashboard);

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.S, KeyCombination.CONTROL_DOWN),
                shortcuts::onServiceManagement);

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.I, KeyCombination.CONTROL_DOWN),
                shortcuts::onInventory);

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F, KeyCombination.CONTROL_DOWN),
                shortcuts::onFinance);

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.K, KeyCombination.CONTROL_DOWN),
                shortcuts::onCustomers);

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.L, KeyCombination.CONTROL_DOWN),
                shortcuts::onLogout);

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F5),
                shortcuts::onRefresh);

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.F11),
                shortcuts::onToggleFullscreen);

        scene.getAccelerators().put(
                new KeyCodeCombination(KeyCode.T, KeyCombination.CONTROL_DOWN),
                shortcuts::onToggleTheme);
    }

    // =========================================
    // SHORTCUT HANDLER INTERFACE
    // =========================================

    /**
     * Interface for handling keyboard shortcuts.
     * Implement this to define actions for each shortcut.
     */
    public interface ShortcutHandler {
        default void onDashboard() {
        }

        default void onServiceManagement() {
        }

        default void onInventory() {
        }

        default void onFinance() {
        }

        default void onCustomers() {
        }

        default void onLogout() {
        }

        default void onRefresh() {
        }

        default void onToggleFullscreen() {
        }

        default void onToggleTheme() {
        }
    }
}
