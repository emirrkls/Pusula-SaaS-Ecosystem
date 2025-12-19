package com.pusula.desktop.controller;

import javafx.animation.AnimationTimer;
import javafx.animation.RotateTransition;
import javafx.fxml.FXML;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Controller for the cinematic screensaver with spinning logo and vapor
 * particle effect.
 * The screensaver shows a 3D rotating logo that emits "cold air" particles
 * outward,
 * creating an HVAC-themed visual effect.
 */
public class ScreensaverController {

    @FXML
    private StackPane rootPane;

    @FXML
    private Pane particlePane;

    @FXML
    private ImageView logoImage;

    private RotateTransition rotateTransition;
    private AnimationTimer particleTimer;
    private final List<Particle> particles = new ArrayList<>();
    private final Random random = new Random();

    private double centerX;
    private double centerY;
    private long lastSpawnTime = 0;

    // Premium Tuning Parameters
    private static final long SPAWN_INTERVAL_NS = 20_000_000L; // Faster spawn (20ms) for denser vapor
    private static final double VAPOR_SPREAD = 30.0; // Initial spread radius

    @FXML
    public void initialize() {
        // Setup 3D rotation for logo
        setupLogoRotation();

        // Calculate center after scene is ready
        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                newScene.windowProperty().addListener((wObs, oldWindow, newWindow) -> {
                    if (newWindow != null) {
                        centerX = rootPane.getWidth() / 2;
                        centerY = rootPane.getHeight() / 2;
                    }
                });
            }
        });

        // Update center when size changes
        rootPane.widthProperty().addListener((obs, oldVal, newVal) -> centerX = newVal.doubleValue() / 2);
        rootPane.heightProperty().addListener((obs, oldVal, newVal) -> centerY = newVal.doubleValue() / 2);

        // Setup particle emitter
        setupParticleSystem();
    }

    private void setupLogoRotation() {
        // Create Y-axis rotation for 3D spin effect
        logoImage.setRotationAxis(Rotate.Y_AXIS);

        rotateTransition = new RotateTransition(Duration.seconds(8), logoImage);
        rotateTransition.setFromAngle(0);
        rotateTransition.setToAngle(360);
        rotateTransition.setCycleCount(RotateTransition.INDEFINITE);
        rotateTransition.setInterpolator(javafx.animation.Interpolator.LINEAR);
    }

    private void setupParticleSystem() {
        particleTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                // Spawn new vapor particles frequently
                if (now - lastSpawnTime > SPAWN_INTERVAL_NS) {
                    // Spawn more particles for "thick" cold air look
                    spawnParticles(1 + random.nextInt(3));
                    lastSpawnTime = now;
                }

                // Update existing particles
                updateParticles();
            }
        };
    }

    private void spawnParticles(int count) {
        for (int i = 0; i < count; i++) {
            Particle particle = new Particle(centerX, centerY);
            particles.add(particle);
            particlePane.getChildren().add(particle);
        }
    }

    private void updateParticles() {
        Iterator<Particle> iterator = particles.iterator();

        while (iterator.hasNext()) {
            Particle particle = iterator.next();
            particle.update();

            // Remove dead particles
            if (particle.isDead()) {
                particlePane.getChildren().remove(particle);
                iterator.remove();
            }
        }
    }

    /**
     * Start the screensaver animations
     */
    public void start() {
        if (rotateTransition != null) {
            rotateTransition.play();
        }
        if (particleTimer != null) {
            particleTimer.start();
        }
    }

    /**
     * Stop the screensaver animations and cleanup
     */
    public void stop() {
        if (rotateTransition != null) {
            rotateTransition.stop();
        }
        if (particleTimer != null) {
            particleTimer.stop();
        }
        particles.clear();
        particlePane.getChildren().clear();
    }

    /**
     * Inner class representing a premium vapor/cold air particle
     */
    private class Particle extends Circle {
        private double velocityX;
        private double velocityY;
        private double life;
        private final double maxLife;
        private final double initialRadius;
        private double angle; // Current angle for swirl effect

        public Particle(double startX, double startY) {
            super();

            // Start very small, expand later
            initialRadius = 2 + random.nextDouble() * 4;
            setRadius(initialRadius);

            // Start around center with slight spread
            double spreadAngle = random.nextDouble() * 2 * Math.PI;
            double distance = random.nextDouble() * VAPOR_SPREAD;
            setTranslateX(startX + Math.cos(spreadAngle) * distance);
            setTranslateY(startY + Math.sin(spreadAngle) * distance);

            // Premium Icy Colors: Pure White to Cyan/Blue tint
            // Use ADDITIVE blend mode for "light/energy" look
            setBlendMode(BlendMode.ADD);

            // Randomize icy blue tint
            double blueTint = 0.9 + random.nextDouble() * 0.1; // 0.9 - 1.0
            double greenTint = 0.8 + random.nextDouble() * 0.2; // 0.8 - 1.0
            // Start with very low opacity
            setFill(new Color(0.8, greenTint, blueTint, 0.05 + random.nextDouble() * 0.1));

            // Heavy blur for "smoke/vapor" texture
            setEffect(new GaussianBlur(8 + random.nextDouble() * 10));

            // Movement: Radial outward + Swirl
            angle = Math.atan2(getTranslateY() - startY, getTranslateX() - startX);
            double speed = 0.2 + random.nextDouble() * 0.8; // Slower, more majestic movement

            velocityX = Math.cos(angle) * speed;
            velocityY = Math.sin(angle) * speed;

            // Long life for sustained trails
            maxLife = 180 + random.nextInt(120); // 3-5 seconds
            life = maxLife;
        }

        public void update() {
            // Add subtle swirl/turbulence
            angle += (random.nextDouble() - 0.5) * 0.02; // Slight change in direction
            velocityX += Math.cos(angle) * 0.005;
            velocityY += Math.sin(angle) * 0.005;

            // Move
            setTranslateX(getTranslateX() + velocityX);
            setTranslateY(getTranslateY() + velocityY);

            // Drag/Friction for "hanging in air" look
            velocityX *= 0.99;
            velocityY *= 0.99;

            life--;

            // Opacity Logic: Fade in quickly, then fade out slowly
            double lifeRatio = life / maxLife;
            double maxOpacity = 0.25; // Don't get too opaque to keep transparent look
            double currentOpacity;

            if (lifeRatio > 0.8) {
                // Fade in phase (last 20% of life = first 20% of existence)
                currentOpacity = maxOpacity * (1 - lifeRatio) * 5;
            } else {
                // Fade out phase
                currentOpacity = maxOpacity * lifeRatio;
            }
            // Clamp
            currentOpacity = Math.max(0, Math.min(maxOpacity, currentOpacity));
            setOpacity(currentOpacity);

            // Expansion: Grow significantly as it dissipates (diffusion)
            // Smoke gets larger and more diffuse as it spreads
            double expansion = 1 + ((1 - lifeRatio) * 2.0); // Grow up to 3x start size
            setRadius(initialRadius * expansion);
        }

        public boolean isDead() {
            return life <= 0 || getOpacity() < 0.001;
        }
    }
}
