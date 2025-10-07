package ru.extreames.pgravitation.xposed.gravitation;

import android.content.Context;
import android.graphics.PointF;
import android.graphics.Rect;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XposedHelpers;
import ru.extreames.pgravitation.utiils.Constants;
import ru.extreames.pgravitation.utiils.Utils;

public class GravitationController implements SensorEventListener {
    private interface ViewProcessor {
        void process(View view);
    }

    private static float PADDING_BOTTOM; // INIT_ON_CONSTRUCTOR

    private final ViewGroup workspace;
    private final SensorManager sensorManager;
    private final Sensor rotationSensor;

    private final List<View> icons = new ArrayList<>();
    private final Map<View, PointF> positions = new HashMap<>();
    private final Map<View, PointF> scales = new HashMap<>();
    private final Map<View, PointF> velocities = new HashMap<>();
    private final Rect workspaceBounds = new Rect();

    private boolean enabled = false;
    private long lastUpdate = 0;
    private int originalBottomPadding = -1;
    private int activeAnimations = 0;

    public GravitationController(ViewGroup workspace, Context context) {
        this.workspace = workspace;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);

        PADDING_BOTTOM = (80 * context.getResources().getDisplayMetrics().density);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void enable() {
        if (enabled)
            return;

        adjustWorkspacePadding();
        collectIcons();
        backupInitialState();
        startSensors();

        enabled = true;
    }

    public void disable() {
        if (!enabled)
            return;

        stopSensors();
        restoreInitialState();

        enabled = false;
    }

    private void adjustWorkspacePadding() {
        originalBottomPadding = workspace.getPaddingBottom();
        workspace.setPadding(
                workspace.getPaddingLeft(),
                workspace.getPaddingTop(),
                workspace.getPaddingRight(),
                0
        );
        workspace.requestLayout();
    }

    private void startSensors() {
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_FASTEST);
        lastUpdate = System.currentTimeMillis();
    }

    private void stopSensors() {
        sensorManager.unregisterListener(this);
    }

    private void collectIcons() {
        icons.clear();
        int selectedPage = getCurrentPage();
        traverseViewHierarchy(workspace, view -> {
            if (isIconView(view) && getIconScreenId(view) == selectedPage) {
                icons.add(view);
            }
        });
    }

    private void traverseViewHierarchy(ViewGroup parent, ViewProcessor processor) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ViewGroup && !isIconView(child)) {
                traverseViewHierarchy((ViewGroup) child, processor);
            } else {
                processor.process(child);
            }
        }
    }

    private boolean isIconView(View view) {
        return view.getClass().getSimpleName().contains("BubbleTextView");
    }

    private int getIconScreenId(View view) {
        try {
            Object tag = view.getTag();
            if (tag != null) {
                return XposedHelpers.getIntField(tag, "screenId");
            }
        } catch (Exception e) {
            Utils.log(Utils.DEBUG_LEVEL.ERROR, "getIconScreenId: " + e.getMessage());
        }
        return -1;
    }

    private int getCurrentPage() {
        try {
            return XposedHelpers.getIntField(workspace, "mCurrentPage");
        } catch (Exception e) {
            Utils.log(Utils.DEBUG_LEVEL.ERROR, "getCurrentPage: " + e.getMessage());
        }
        return -1;
    }

    private void backupInitialState() {
        positions.clear();
        scales.clear();
        velocities.clear();

        for (View icon : icons) {
            positions.put(icon, new PointF(icon.getX(), icon.getY()));
            scales.put(icon, new PointF(icon.getScaleX(), icon.getScaleY()));
            velocities.put(icon, new PointF(0, 0));

            icon.setScaleX(Constants.ICON_SCALE);
            icon.setScaleY(Constants.ICON_SCALE);
        }

        captureWorkspaceBounds();
    }

    private void restoreInitialState() {
        for (View icon : icons) {
            PointF position = positions.get(icon);
            PointF scale = scales.get(icon);

            if (position == null || scale == null)
                continue;

            icon.animate()
                    .x(position.x)
                    .y(position.y)
                    .scaleX(scale.x)
                    .scaleY(scale.y)
                    .setDuration(Constants.ANIMATION_DURATION)
                    .withEndAction(() -> {
                        if (--activeAnimations == 0) {
                            restoreWorkspacePadding();
                        }
                    })
                    .start();
            activeAnimations++;
        }
    }

    private void restoreWorkspacePadding() {
        workspace.setPadding(
                workspace.getPaddingLeft(),
                workspace.getPaddingTop(),
                workspace.getPaddingRight(),
                originalBottomPadding
        );
        workspace.requestLayout();

        workspace.getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
            @Override
            public void onGlobalLayout() {
                workspace.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                applyFinalPositions();
            }
        });
    }

    private void applyFinalPositions() {
        for (View icon : icons) {
            PointF position = positions.get(icon);
            if (position != null) {
                icon.setX(position.x);
                icon.setY(position.y);
            }
            icon.requestLayout();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR)
            return;

        float[] rotationMatrix = new float[9];
        SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);

        float[] orientation = new float[3];
        SensorManager.getOrientation(rotationMatrix, orientation);

        float pitch = orientation[1];
        float roll = orientation[2];

        long currentTime = System.currentTimeMillis();
        float deltaTime = Math.min((currentTime - lastUpdate) / 1000f, 0.05f);

        lastUpdate = currentTime;
        updatePhysics(pitch, roll, deltaTime);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void updatePhysics(float pitch, float roll, float dt) {
        float gx = (float) Math.sin(roll) * Constants.GRAVITY_SCALE;
        float gy = (float) -Math.sin(pitch) * Constants.GRAVITY_SCALE;

        for (View icon : icons) {
            PointF vel = velocities.get(icon);
            if (vel == null)
                continue;

            vel.x += gx * dt;
            vel.y += gy * dt;

            vel.x *= Constants.LINEAR_DAMPING;
            vel.y *= Constants.LINEAR_DAMPING;

            vel.x = Math.max(-Constants.MAX_VELOCITY, Math.min(Constants.MAX_VELOCITY, vel.x));
            vel.y = Math.max(-Constants.MAX_VELOCITY, Math.min(Constants.MAX_VELOCITY, vel.y));

            float x = icon.getX() + vel.x * dt;
            float y = icon.getY() + vel.y * dt;

            if (x < workspaceBounds.left) {
                x = workspaceBounds.left;
                vel.x *= -Constants.NORMAL_VEL_DAMP;
            }
            if (x + icon.getWidth() > workspaceBounds.right) {
                x = workspaceBounds.right - icon.getWidth();
                vel.x *= -Constants.NORMAL_VEL_DAMP;
            }
            if (y < workspaceBounds.top) {
                y = workspaceBounds.top;
                vel.y *= -Constants.NORMAL_VEL_DAMP;
            }
            if (y + icon.getHeight() > workspaceBounds.bottom) {
                y = workspaceBounds.bottom - icon.getHeight();
                vel.y *= -Constants.NORMAL_VEL_DAMP;
            }

            icon.setX(x);
            icon.setY(y);
        }

        handleCollisions();
    }

    private void handleCollisions() {
        for (int k = 0; k < Constants.COLLISION_ITERATIONS; k++) {
            for (int i = 0; i < icons.size(); i++) {
                for (int j = i + 1; j < icons.size(); j++) {
                    collide(icons.get(i), icons.get(j));
                }
            }
        }
    }

    private void collide(View a, View b) {
        float ax = a.getX() + a.getWidth() / 2f;
        float ay = a.getY() + a.getHeight() / 2f;
        float bx = b.getX() + b.getWidth() / 2f;
        float by = b.getY() + b.getHeight() / 2f;

        float ra = (Math.min(a.getWidth(), a.getHeight()) / 2f) * Constants.HITBOX_SCALE;
        float rb = (Math.min(b.getWidth(), b.getHeight()) / 2f) * Constants.HITBOX_SCALE;

        float dx = bx - ax;
        float dy = by - ay;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float min = ra + rb;

        if (dist > 0 && dist < min) {
            float overlap = (min - dist) * Constants.SEPARATION_STRENGTH;
            float nx = dx / dist;
            float ny = dy / dist;

            a.setX(a.getX() - nx * overlap / 2f);
            a.setY(a.getY() - ny * overlap / 2f);
            b.setX(b.getX() + nx * overlap / 2f);
            b.setY(b.getY() + ny * overlap / 2f);

            PointF va = velocities.get(a);
            PointF vb = velocities.get(b);

            if (va == null || vb == null)
                return;

            float rel = (vb.x - va.x) * nx + (vb.y - va.y) * ny;
            if (rel < 0) {
                float imp = -rel * 0.5f;
                va.x -= imp * nx;
                va.y -= imp * ny;
                vb.x += imp * nx;
                vb.y += imp * ny;
            }
        }
    }

    private void captureWorkspaceBounds() {
        int[] location = new int[2];
        workspace.getLocationOnScreen(location);

        int iconRadius = 0;
        if (!icons.isEmpty()) {
            View sampleIcon = icons.get(0);
            iconRadius = (int) (Math.min(sampleIcon.getWidth(), sampleIcon.getHeight()) / 2f);
        }

        workspaceBounds.set(
                location[0],
                location[1],
                location[0] + workspace.getWidth() - iconRadius,
                (int) (location[1] + workspace.getHeight() - PADDING_BOTTOM)
        );
    }
}