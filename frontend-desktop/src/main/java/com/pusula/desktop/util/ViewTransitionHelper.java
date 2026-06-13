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

        if (container.getChildren().isEmpty()) {
            newContent.setOpacity(0);
            container.getChildren().setAll(newContent);
            fadeIn(newContent);
            return;
        }

        Node outgoing = container.getChildren().get(0);
        FadeTransition fadeOut = new FadeTransition(Duration.millis(120), outgoing);
        fadeOut.setFromValue(outgoing.getOpacity());
        fadeOut.setToValue(0);
        fadeOut.setOnFinished(e -> {
            newContent.setOpacity(0);
            container.getChildren().setAll(newContent);
            fadeIn(newContent);
        });
        fadeOut.play();
    }

    private static void fadeIn(Node node) {
        FadeTransition fadeIn = new FadeTransition(Duration.millis(180), node);
        fadeIn.setFromValue(0);
        fadeIn.setToValue(1);
        fadeIn.play();
    }
}
