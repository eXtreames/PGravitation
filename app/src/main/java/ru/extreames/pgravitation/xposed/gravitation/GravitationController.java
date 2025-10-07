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

    // adaptive centering
    private float lastPitch = 0f;
    private float lastRoll  = 0f;
    private float prevPitch = 0f;
    private float prevRoll  = 0f;
    private float centerPitch = 0f;
    private float centerRoll  = 0f;

    public GravitationController(ViewGroup workspace, Context context) {
        this.workspace = workspace;
        this.sensorManager = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        this.rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void enable() {
        if (enabled)
            return;

        collectIcons();
        backupInitialState();
        adjustWorkspacePadding();
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

        float rawPitch = orientation[1];
        float rawRoll  = orientation[2];

        final float LP_ALPHA = 0.1f;
        lastPitch += LP_ALPHA * (rawPitch - lastPitch);
        lastRoll  += LP_ALPHA * (rawRoll  - lastRoll);

        float pitch = lastPitch;
        float roll  = lastRoll;
        float deltaPitch = Math.abs(pitch - prevPitch);
        float deltaRoll  = Math.abs(roll  - prevRoll);

        prevPitch = pitch;
        prevRoll  = roll;

        float movementMagnitude = (deltaPitch + deltaRoll) * 0.5f;
        float recenterSpeed = 0.001f + (0.02f / (1f + movementMagnitude * 500f));

        centerPitch += (pitch - centerPitch) * recenterSpeed;
        centerRoll  += (roll  - centerRoll)  * recenterSpeed;

        pitch -= centerPitch;
        roll  -= centerRoll;

        long currentTime = System.currentTimeMillis();
        float deltaTime = Math.min((currentTime - lastUpdate) / 1000f, 0.05f);
        if (deltaTime < 0.01f)
            return;

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

        int paddingBottom = workspace.getPaddingBottom();
        int paddingUpper = (int) (50 * workspace.getContext().getResources().getDisplayMetrics().density);
        int paddingRight = 0;

        if (!icons.isEmpty()) {
            View sampleIcon = icons.get(0);
            paddingRight = (int) ((Math.min(sampleIcon.getWidth(), sampleIcon.getHeight()) / 2F) / 1.8F);
        }

        workspaceBounds.set(
                location[0],
                location[1],
                location[0] + workspace.getWidth() - paddingRight,
                location[1] + workspace.getHeight() - paddingBottom + paddingUpper
        );
    }
}