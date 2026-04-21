package com.pusula.desktop.controller;

import javafx.animation.AnimationTimer;
import javafx.animation.RotateTransition;
import javafx.fxml.FXML;
import javafx.scene.effect.BlendMode;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.CubicCurve;
import javafx.scene.shape.Polygon;
import javafx.scene.shape.Line;
import javafx.scene.transform.Rotate;
import javafx.util.Duration;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

/**
 * Premium screensaver with realistic AC airflow effect and snowflakes.
 * 
 * Features:
 * - Spinning 3D logo
 * - Realistic curved airflow lines from below the logo (like AC split unit)
 * - Flowing micro-particles along airflow
 * - Gentle falling snowflakes with realistic physics
 * - Cold mist/vapor emanating from center
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

    // Particle collections
    private final List<MistParticle> mistParticles = new ArrayList<>();
    private final List<Snowflake> snowflakes = new ArrayList<>();
    private final List<AirflowWave> airflowWaves = new ArrayList<>();
    private final List<AirflowParticle> airflowParticles = new ArrayList<>();

    private final Random random = new Random();

    private double centerX;
    private double centerY;
    private double screenWidth;
    private double screenHeight;

    // Timing
    private long lastMistTime = 0;
    private long lastSnowflakeTime = 0;
    private long lastAirflowTime = 0;
    private long frameCount = 0;

    // Tuning Parameters - More particles, more realistic
    private static final long MIST_INTERVAL_NS = 30_000_000L; // 30ms - dense mist
    private static final long SNOWFLAKE_INTERVAL_NS = 150_000_000L; // 150ms - gentle snow
    private static final long AIRFLOW_INTERVAL_NS = 600_000_000L; // 600ms - airflow waves

    @FXML
    public void initialize() {
        setupLogoRotation();

        rootPane.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                updateDimensions();
                newScene.widthProperty().addListener((o, old, n) -> updateDimensions());
                newScene.heightProperty().addListener((o, old, n) -> updateDimensions());
            }
        });

        rootPane.widthProperty().addListener((obs, oldVal, newVal) -> updateDimensions());
        rootPane.heightProperty().addListener((obs, oldVal, newVal) -> updateDimensions());

        setupParticleSystem();
    }

    private void updateDimensions() {
        screenWidth = rootPane.getWidth() > 0 ? rootPane.getWidth() : 1000;
        screenHeight = rootPane.getHeight() > 0 ? rootPane.getHeight() : 700;
        centerX = screenWidth / 2;
        centerY = screenHeight / 2;
    }

    private void setupLogoRotation() {
        logoImage.setRotationAxis(Rotate.Y_AXIS);

        // Add subtle glow to logo
        DropShadow glow = new DropShadow();
        glow.setColor(Color.rgb(100, 180, 255, 0.6));
        glow.setRadius(30);
        glow.setSpread(0.3);
        logoImage.setEffect(glow);

        rotateTransition = new RotateTransition(Duration.seconds(10), logoImage);
        rotateTransition.setFromAngle(0);
        rotateTransition.setToAngle(360);
        rotateTransition.setCycleCount(RotateTransition.INDEFINITE);
        rotateTransition.setInterpolator(javafx.animation.Interpolator.LINEAR);
    }

    private void setupParticleSystem() {
        particleTimer = new AnimationTimer() {
            @Override
            public void handle(long now) {
                frameCount++;

                // Spawn mist particles around logo (limit to max 30)
                if (now - lastMistTime > MIST_INTERVAL_NS && mistParticles.size() < 30) {
                    spawnMist(1); // Reduced from 2-5 to just 1
                    lastMistTime = now;
                }

                // Spawn snowflakes from top (limit to max 40)
                if (now - lastSnowflakeTime > SNOWFLAKE_INTERVAL_NS && snowflakes.size() < 40) {
                    spawnSnowflakes(1);
                    lastSnowflakeTime = now;
                }

                // Update all particles
                updateMist();
                updateSnowflakes();
            }
        };
    }

    // ==================== SPAWN METHODS ====================

    private void spawnMist(int count) {
        for (int i = 0; i < count; i++) {
            MistParticle mist = new MistParticle(centerX, centerY);
            mistParticles.add(mist);
            particlePane.getChildren().add(0, mist); // Add behind other elements
        }
    }

    private void spawnSnowflakes(int count) {
        for (int i = 0; i < count; i++) {
            double x = random.nextDouble() * screenWidth;
            Snowflake snowflake = new Snowflake(x, -30);
            snowflakes.add(snowflake);
            particlePane.getChildren().add(snowflake);
        }
    }

    private void spawnAirflowWave() {
        // Logo bottom position
        double logoBottom = centerY + 70;

        // Create 5 wave lines for fuller effect
        for (int i = -2; i <= 2; i++) {
            double startX = centerX + (i * 25);
            double angle = i * 8; // Spread angle in degrees
            AirflowWave wave = new AirflowWave(startX, logoBottom, angle);
            airflowWaves.add(wave);
            particlePane.getChildren().add(0, wave);
        }
    }

    private void spawnAirflowParticles() {
        // Spawn tiny particles that flow along airflow path
        double logoBottom = centerY + 80;

        for (int i = 0; i < 3; i++) {
            double offsetX = (random.nextDouble() - 0.5) * 100;
            AirflowParticle particle = new AirflowParticle(centerX + offsetX, logoBottom);
            airflowParticles.add(particle);
            particlePane.getChildren().add(particle);
        }
    }

    // ==================== UPDATE METHODS ====================

    private void updateMist() {
        Iterator<MistParticle> iterator = mistParticles.iterator();
        while (iterator.hasNext()) {
            MistParticle mist = iterator.next();
            mist.update();
            if (mist.isDead()) {
                particlePane.getChildren().remove(mist);
                iterator.remove();
            }
        }
    }

    private void updateSnowflakes() {
        Iterator<Snowflake> iterator = snowflakes.iterator();
        while (iterator.hasNext()) {
            Snowflake snowflake = iterator.next();
            snowflake.update();
            if (snowflake.getTranslateY() > screenHeight + 50) {
                particlePane.getChildren().remove(snowflake);
                iterator.remove();
            }
        }
    }

    private void updateAirflowWaves() {
        Iterator<AirflowWave> iterator = airflowWaves.iterator();
        while (iterator.hasNext()) {
            AirflowWave wave = iterator.next();
            wave.update();
            if (wave.isDead()) {
                particlePane.getChildren().remove(wave);
                iterator.remove();
            }
        }
    }

    private void updateAirflowParticles() {
        Iterator<AirflowParticle> iterator = airflowParticles.iterator();
        while (iterator.hasNext()) {
            AirflowParticle particle = iterator.next();
            particle.update();
            if (particle.isDead()) {
                particlePane.getChildren().remove(particle);
                iterator.remove();
            }
        }
    }

    public void start() {
        updateDimensions();
        if (rotateTransition != null)
            rotateTransition.play();
        if (particleTimer != null)
            particleTimer.start();
    }

    public void stop() {
        if (rotateTransition != null)
            rotateTransition.stop();
        if (particleTimer != null)
            particleTimer.stop();
        mistParticles.clear();
        snowflakes.clear();
        airflowWaves.clear();
        airflowParticles.clear();
        particlePane.getChildren().clear();
    }

    // ==================== PARTICLE CLASSES ====================

    /**
     * Soft mist/vapor particle emanating from logo
     */
    private class MistParticle extends Circle {
        private double vx, vy;
        private double life, maxLife;
        private double initialRadius;
        private double turbulence;

        public MistParticle(double cx, double cy) {
            // Start near center with BIGGER radius offset
            double angle = random.nextDouble() * Math.PI * 2;
            double dist = 40 + random.nextDouble() * 120; // Much larger spawn radius

            setTranslateX(cx + Math.cos(angle) * dist);
            setTranslateY(cy + Math.sin(angle) * dist);

            initialRadius = 15 + random.nextDouble() * 25; // Bigger particles
            setRadius(initialRadius);

            // Radial velocity outward - slower for longer visibility
            double speed = 0.2 + random.nextDouble() * 0.4;
            vx = Math.cos(angle) * speed;
            vy = Math.sin(angle) * speed;

            // Icy blue-white color with HIGHER opacity for visibility
            double blue = 0.85 + random.nextDouble() * 0.15;
            setFill(Color.rgb(200, 225, 255, 0.08 + random.nextDouble() * 0.12)); // More visible

            // Heavy blur for fog effect - bigger blur
            setEffect(new GaussianBlur(25 + random.nextDouble() * 25));
            setBlendMode(BlendMode.ADD);

            maxLife = 300 + random.nextInt(150); // Longer life
            life = maxLife;
            turbulence = random.nextDouble() * Math.PI * 2;
        }

        public void update() {
            // Add turbulence/swirl
            turbulence += 0.02;
            vx += Math.cos(turbulence) * 0.01;
            vy += Math.sin(turbulence) * 0.01;

            // Apply velocity with drag
            setTranslateX(getTranslateX() + vx);
            setTranslateY(getTranslateY() + vy);
            vx *= 0.995;
            vy *= 0.995;

            life--;

            // Fade in then out
            double lifeRatio = life / maxLife;
            double opacity;
            if (lifeRatio > 0.85) {
                opacity = (1 - lifeRatio) * 6.67 * 0.15;
            } else {
                opacity = lifeRatio * 0.15;
            }
            setOpacity(Math.max(0, opacity));

            // Expand as it dissipates
            double expansion = 1 + (1 - lifeRatio) * 3;
            setRadius(initialRadius * expansion);
        }

        public boolean isDead() {
            return life <= 0;
        }
    }

    /**
     * Realistic snowflake with 6-fold symmetry
     */
    private class Snowflake extends Pane {
        private double vy, vx;
        private double rotationSpeed;
        private double swayPhase;
        private double swayAmplitude;

        public Snowflake(double x, double y) {
            setTranslateX(x);
            setTranslateY(y);

            double size = 4 + random.nextDouble() * 8;

            // Create realistic 6-arm snowflake
            createSnowflakeShape(size);

            // Fall speed - smaller = slower (more realistic)
            vy = 0.8 + random.nextDouble() * 1.2;
            vx = 0;

            // Rotation
            rotationSpeed = (random.nextDouble() - 0.5) * 1.5;

            // Swaying motion
            swayPhase = random.nextDouble() * Math.PI * 2;
            swayAmplitude = 0.3 + random.nextDouble() * 0.5;

            // Subtle blur
            setEffect(new GaussianBlur(0.5 + random.nextDouble() * 0.5));
        }

        private void createSnowflakeShape(double size) {
            Color snowColor = Color.rgb(230, 240, 255, 0.7 + random.nextDouble() * 0.3);

            // Main 6 arms
            for (int i = 0; i < 6; i++) {
                double angle = i * 60;

                // Main arm line
                Line arm = new Line(0, 0, 0, -size);
                arm.setStroke(snowColor);
                arm.setStrokeWidth(1);
                arm.setRotate(angle);
                getChildren().add(arm);

                // Small branches on each arm
                if (size > 5) {
                    double branchY = -size * 0.5;
                    double branchLen = size * 0.35;

                    // Left branch
                    Line lb = new Line(0, branchY, -branchLen * 0.7, branchY - branchLen * 0.5);
                    lb.setStroke(snowColor);
                    lb.setStrokeWidth(0.7);
                    lb.setRotate(angle);
                    getChildren().add(lb);

                    // Right branch
                    Line rb = new Line(0, branchY, branchLen * 0.7, branchY - branchLen * 0.5);
                    rb.setStroke(snowColor);
                    rb.setStrokeWidth(0.7);
                    rb.setRotate(angle);
                    getChildren().add(rb);
                }
            }

            // Center dot
            Circle center = new Circle(0, 0, size * 0.15);
            center.setFill(snowColor);
            getChildren().add(center);
        }

        public void update() {
            // Swaying horizontal motion
            swayPhase += 0.03;
            vx = Math.sin(swayPhase) * swayAmplitude;

            setTranslateX(getTranslateX() + vx);
            setTranslateY(getTranslateY() + vy);

            // Gentle rotation
            setRotate(getRotate() + rotationSpeed);
        }
    }

    /**
     * Curved airflow wave line (like AC output)
     */
    private class AirflowWave extends CubicCurve {
        private double life, maxLife;
        private double progress;
        private final double spreadAngle;
        private final double startX, startY;
        private final double length;

        public AirflowWave(double x, double y, double angleDeg) {
            this.startX = x;
            this.startY = y;
            this.spreadAngle = Math.toRadians(angleDeg);
            this.length = 180 + random.nextDouble() * 80;

            // Initialize curve points
            setStartX(startX);
            setStartY(startY);

            // Control points create smooth S-curve like real airflow
            double endX = startX + Math.sin(spreadAngle) * length;
            double endY = startY + length;

            setControlX1(startX + Math.sin(spreadAngle) * 20);
            setControlY1(startY + 40);
            setControlX2(endX - Math.sin(spreadAngle) * 30);
            setControlY2(endY - 60);
            setEndX(endX);
            setEndY(endY);

            // Gradient stroke - fades toward end
            LinearGradient gradient = new LinearGradient(
                    0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                    new Stop(0, Color.rgb(180, 210, 255, 0.6)),
                    new Stop(0.3, Color.rgb(160, 200, 255, 0.4)),
                    new Stop(0.7, Color.rgb(200, 225, 255, 0.2)),
                    new Stop(1, Color.rgb(220, 240, 255, 0.0)));

            setStroke(gradient);
            setStrokeWidth(2 + random.nextDouble() * 1.5);
            setFill(null);

            // Soft blur
            setEffect(new GaussianBlur(2 + random.nextDouble()));

            maxLife = 150;
            life = maxLife;
            progress = 0;
            setOpacity(0);
        }

        public void update() {
            life--;
            progress++;

            // Animate growth - line extends downward
            double growthRatio = Math.min(1, progress / 40.0);
            double currentLength = length * growthRatio;

            double endX = startX + Math.sin(spreadAngle) * currentLength;
            double endY = startY + currentLength;

            setControlX1(startX + Math.sin(spreadAngle) * (20 * growthRatio));
            setControlY1(startY + 40 * growthRatio);
            setControlX2(endX - Math.sin(spreadAngle) * (30 * growthRatio));
            setControlY2(Math.max(startY + 10, endY - 60));
            setEndX(endX);
            setEndY(endY);

            // Fade in/out
            double lifeRatio = life / maxLife;
            double opacity;
            if (lifeRatio > 0.8) {
                opacity = (1 - lifeRatio) * 5;
            } else if (lifeRatio < 0.3) {
                opacity = lifeRatio * 3.33;
            } else {
                opacity = 1.0;
            }
            setOpacity(opacity * 0.5);
        }

        public boolean isDead() {
            return life <= 0;
        }
    }

    /**
     * Small particle flowing along airflow path
     */
    private class AirflowParticle extends Circle {
        private double vx, vy;
        private double life;
        private double gravity;

        public AirflowParticle(double x, double y) {
            super(1 + random.nextDouble() * 2);

            setTranslateX(x);
            setTranslateY(y);

            // Initial velocity - mostly downward with spread
            double spreadAngle = (random.nextDouble() - 0.5) * 0.6;
            double speed = 2 + random.nextDouble() * 2;
            vx = Math.sin(spreadAngle) * speed * 0.5;
            vy = speed;

            gravity = 0.02 + random.nextDouble() * 0.03;

            // Icy white color
            setFill(Color.rgb(200, 230, 255, 0.4 + random.nextDouble() * 0.3));
            setEffect(new GaussianBlur(1));

            life = 80 + random.nextInt(40);
        }

        public void update() {
            // Slight gravity acceleration
            vy += gravity;

            // Add slight horizontal drift
            vx += (random.nextDouble() - 0.5) * 0.1;

            setTranslateX(getTranslateX() + vx);
            setTranslateY(getTranslateY() + vy);

            // Air resistance
            vx *= 0.98;

            life--;

            // Fade out toward end
            if (life < 30) {
                setOpacity(life / 30.0 * 0.6);
            }
        }

        public boolean isDead() {
            return life <= 0 || getTranslateY() > screenHeight + 20;
        }
    }
}
