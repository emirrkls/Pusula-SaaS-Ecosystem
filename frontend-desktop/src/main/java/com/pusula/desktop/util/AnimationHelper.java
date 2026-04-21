package com.pusula.desktop.util;

import javafx.animation.*;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * AnimationHelper - Reusable micro-interactions for premium UI feel.
 * Provides static methods for common animations: bounce, hover, shake, fade-in.
 */
public class AnimationHelper {

    // =========================================
    // BUTTON BOUNCE - Tactile press feedback
    // =========================================

    /**
     * Applies a bouncy press effect to any node (typically buttons).
     * On press: scales down to 0.95 (50ms)
     * On release: springs back to 1.0 (100ms, ease-out)
     */
    public static void applyButtonBounce(Node node) {
        ScaleTransition pressDown = new ScaleTransition(Duration.millis(50), node);
        pressDown.setToX(0.95);
        pressDown.setToY(0.95);

        ScaleTransition releaseUp = new ScaleTransition(Duration.millis(100), node);
        releaseUp.setToX(1.0);
        releaseUp.setToY(1.0);
        releaseUp.setInterpolator(Interpolator.EASE_OUT);

        node.setOnMousePressed(e -> pressDown.playFromStart());
        node.setOnMouseReleased(e -> releaseUp.playFromStart());
    }

    // =========================================
    // CARD HOVER - Lift effect with shadow
    // =========================================

    /**
     * Applies a hover "lift" effect to cards/panes.
     * On hover: translates Y by -5px and increases shadow.
     * On exit: returns to normal.
     */
    public static void applyCardHover(Node node) {
        // Store original shadow or create default
        DropShadow originalShadow = new DropShadow();
        originalShadow.setRadius(15);
        originalShadow.setOffsetY(4);
        originalShadow.setColor(Color.rgb(0, 0, 0, 0.06));

        DropShadow hoverShadow = new DropShadow();
        hoverShadow.setRadius(25);
        hoverShadow.setOffsetY(8);
        hoverShadow.setColor(Color.rgb(0, 0, 0, 0.12));

        node.setOnMouseEntered(e -> {
            TranslateTransition lift = new TranslateTransition(Duration.millis(150), node);
            lift.setToY(-5);
            lift.setInterpolator(Interpolator.EASE_OUT);
            lift.play();
            node.setEffect(hoverShadow);
        });

        node.setOnMouseExited(e -> {
            TranslateTransition drop = new TranslateTransition(Duration.millis(150), node);
            drop.setToY(0);
            drop.setInterpolator(Interpolator.EASE_OUT);
            drop.play();
            node.setEffect(originalShadow);
        });

        // Set initial effect
        node.setEffect(originalShadow);
    }

    // =========================================
    // SHAKE - Error indication (validation)
    // =========================================

    /**
     * Shakes a node horizontally to indicate an error (e.g., wrong password).
     * Quick 4-cycle X-axis shake.
     */
    public static void shake(Node node) {
        TranslateTransition shake = new TranslateTransition(Duration.millis(50), node);
        shake.setFromX(0);
        shake.setByX(10);
        shake.setCycleCount(6);
        shake.setAutoReverse(true);
        shake.setOnFinished(e -> node.setTranslateX(0));
        shake.play();
    }

    // =========================================
    // FADE IN UP - Content reveal animation
    // =========================================

    /**
     * Fades in a node while sliding it up from below.
     * Starting: opacity 0, translateY +20
     * Ending: opacity 1, translateY 0
     * 
     * @param node    The node to animate
     * @param delayMs Delay before animation starts (for staggering)
     */
    public static void fadeInUp(Node node, int delayMs) {
        node.setOpacity(0);
        node.setTranslateY(20);

        FadeTransition fade = new FadeTransition(Duration.millis(300), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.setDelay(Duration.millis(delayMs));

        TranslateTransition slide = new TranslateTransition(Duration.millis(300), node);
        slide.setFromY(20);
        slide.setToY(0);
        slide.setDelay(Duration.millis(delayMs));
        slide.setInterpolator(Interpolator.EASE_OUT);

        ParallelTransition parallel = new ParallelTransition(fade, slide);
        parallel.play();
    }

    /**
     * Convenience method for fadeInUp with no delay.
     */
    public static void fadeInUp(Node node) {
        fadeInUp(node, 0);
    }

    // =========================================
    // FADE IN - Simple opacity fade
    // =========================================

    /**
     * Simple fade in animation.
     */
    public static void fadeIn(Node node, int durationMs) {
        node.setOpacity(0);
        FadeTransition fade = new FadeTransition(Duration.millis(durationMs), node);
        fade.setFromValue(0);
        fade.setToValue(1);
        fade.play();
    }

    // =========================================
    // PULSE - Attention grabber
    // =========================================

    /**
     * Pulses a node's scale to grab attention.
     */
    public static void pulse(Node node) {
        ScaleTransition pulse = new ScaleTransition(Duration.millis(150), node);
        pulse.setFromX(1.0);
        pulse.setFromY(1.0);
        pulse.setToX(1.1);
        pulse.setToY(1.1);
        pulse.setCycleCount(2);
        pulse.setAutoReverse(true);
        pulse.play();
    }

    // =========================================
    // SUCCESS CHECKMARK - Completion indicator
    // =========================================

    /**
     * Plays a success animation (scale up with bounce).
     */
    public static void successBounce(Node node) {
        node.setScaleX(0);
        node.setScaleY(0);

        ScaleTransition scaleUp = new ScaleTransition(Duration.millis(200), node);
        scaleUp.setToX(1.2);
        scaleUp.setToY(1.2);
        scaleUp.setInterpolator(Interpolator.EASE_OUT);

        ScaleTransition settle = new ScaleTransition(Duration.millis(100), node);
        settle.setToX(1.0);
        settle.setToY(1.0);

        SequentialTransition sequence = new SequentialTransition(scaleUp, settle);
        sequence.play();
    }

    // =========================================
    // TABLE ROW ANIMATION - Staggered fade-in
    // =========================================

    /**
     * Animates table rows with staggered fade-in effect.
     * Call this after setting table data.
     * 
     * @param tableView      The TableView to animate
     * @param staggerDelayMs Delay between each row (e.g., 30ms)
     */
    public static void animateTableRows(javafx.scene.control.TableView<?> tableView, int staggerDelayMs) {
        // Get all visible rows via lookup
        javafx.application.Platform.runLater(() -> {
            var rows = tableView.lookupAll(".table-row-cell");
            int index = 0;
            for (Node row : rows) {
                if (row instanceof javafx.scene.control.TableRow<?> tableRow) {
                    if (!tableRow.isEmpty()) {
                        fadeInUp(tableRow, index * staggerDelayMs);
                        index++;
                    }
                }
            }
        });
    }

    // =========================================
    // COUNTER ANIMATION - Number increment
    // =========================================

    /**
     * Animates a Label counting up from 0 to a target value smoothly.
     */
    public static void animateCounter(javafx.scene.control.Label label, long targetValue, int durationMs) {
        if (targetValue == 0) {
            label.setText("0");
            return;
        }
        
        Animation animation = new Transition() {
            {
                setCycleDuration(Duration.millis(durationMs));
                setInterpolator(Interpolator.EASE_OUT);
            }
            @Override
            protected void interpolate(double frac) {
                long current = Math.round(targetValue * frac);
                label.setText(String.valueOf(current));
            }
        };
        animation.play();
    }
}
