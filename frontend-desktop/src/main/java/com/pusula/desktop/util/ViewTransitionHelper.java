package com.pusula.desktop.util;

import javafx.animation.FadeTransition;
import javafx.scene.Node;
import javafx.scene.layout.StackPane;
import javafx.util.Duration;

/**
 * Smooth page transitions for main content area.
 */
public final class ViewTransitionHelper {

    private ViewTransitionHelper() {
    }

    public static void swapContent(StackPane container, Node newContent) {
        if (container == null || newContent == null) {
            return;
        }

        container.getChildren().setAll(newContent);
        fadeIn(newContent);
    }

    private static void fadeIn(Node node) {
        FadeTransition fadeIn = new FadeTransition(Duration.millis(100), node);
        fadeIn.setFromValue(0.92);
        fadeIn.setToValue(1);
        fadeIn.play();
    }
}
