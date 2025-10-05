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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.robv.android.xposed.XposedHelpers;
import ru.extreames.pgravitation.utiils.Constants;
import ru.extreames.pgravitation.utiils.Utils;
import ru.extreames.pgravitation.xposed.XposedPrefs;

public class GravitationProcessor implements SensorEventListener { // very trash code, refactor is highly needed (FIXME)
    private interface ViewProcessor {
        void process(View view);
    }

    private static final int BOTTOM_PADDING = 670; // dock on Pixel 7 with 393 DPI (FIXME, automatic detection needed)
    private static final int COLLISION_ITER = 6;

    private static float GRAVITY_SCALE; // INIT_ON_CONSTRUCTOR
    private static float MAX_VELOCITY; // INIT_ON_CONSTRUCTOR

    private static float LINEAR_DAMPING; // INIT_ON_CONSTRUCTOR
    private static float NORMAL_VEL_DAMP; // INIT_ON_CONSTRUCTOR
    private static float HITBOX_SCALE; // INIT_ON_CONSTRUCTOR
    private static float SEPARATION_STRENGTH; // INIT_ON_CONSTRUCTOR

    private final Object workspace;
    private final SensorManager sensorManager;
    private final Sensor rotationSensor;

    private final List<View> icons = new ArrayList<>();
    private final Map<View, PointF> positions = new HashMap<>();
    private final Map<View, PointF> scales = new HashMap<>();
    private final Map<View, PointF> velocities = new HashMap<>();
    private final Rect workspaceBounds = new Rect();

    private boolean isGravitationEnabled = false;
    private long lastUpdate = 0;

    public GravitationProcessor(Object workspace, Context ctx) {
        this.workspace = workspace;
        this.sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        this.rotationSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
    }

    public void enable() {
        if (isGravitationEnabled)
            return;

        loadConstants();
        collectIcons();
        backupPositions();
        captureBounds();
        backupVelocities();
        start();

        isGravitationEnabled = true;
    }

    public void disable() {
        if (!isGravitationEnabled)
            return;

        stop();
        restorePositions();

        isGravitationEnabled = false;
    }

    private void start() {
        sensorManager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        lastUpdate = System.currentTimeMillis();
    }

    private void stop() {
        sensorManager.unregisterListener(this);
    }

    private void collectIcons() {
        icons.clear();
        if (workspace instanceof ViewGroup) {
            int selectedPage = getCurrentPage(workspace);
            traverse((ViewGroup) workspace, view -> {
                if (isIcon(view) && getScreenId(view) == selectedPage)
                    icons.add(view);
            });
        }
    }

    private void traverse(ViewGroup parent, ViewProcessor processor) {
        for (int i = 0; i < parent.getChildCount(); i++) {
            View child = parent.getChildAt(i);
            if (child instanceof ViewGroup && !isIcon(child))
                traverse((ViewGroup) child, processor);
            else
                processor.process(child);
        }
    }

    private boolean isIcon(View view) {
        return view.getClass().getSimpleName().contains("BubbleTextView");
    }

    private int getScreenId(View view) {
        try {
            Object tag = view.getTag();
            if (tag != null) {
                return XposedHelpers.getIntField(tag, "screenId");
            }
        } catch (Exception e) {
            Utils.log(Utils.DEBUG_LEVEL.ERROR, "getScreenId ->> " + e.getMessage());
        }
        return -1;
    }

    private int getCurrentPage(Object obj) {
        try {
            return XposedHelpers.getIntField(obj, "mCurrentPage");
        } catch (Exception e) {
            Utils.log(Utils.DEBUG_LEVEL.ERROR, "getCurrentPage ->> " + e.getMessage());
        }
        return -1;
    }

    private void backupVelocities() {
        velocities.clear();
        for (View v : icons)
            velocities.put(v, new PointF(0, 0));
    }

    private void backupPositions() {
        positions.clear(); scales.clear();
        for (View v : icons) {
            positions.put(v, new PointF(v.getX(), v.getY()));
            scales.put(v, new PointF(v.getScaleX(), v.getScaleY()));
            v.setScaleX(0.94f); v.setScaleY(0.94f);
        }
    }

    private void restorePositions() {
        for (View v : icons) {
            PointF pos = positions.get(v);
            PointF scale = scales.get(v);

            if (pos == null || scale == null)
                continue;

            v.animate()
                    .x(pos.x)
                    .y(pos.y)
                    .scaleX(scale.x)
                    .scaleY(scale.y)
                    .setDuration(600)
                    .start();
        }
    }

    @Override
    public void onSensorChanged(SensorEvent e) {
        if (e.sensor.getType() != Sensor.TYPE_ROTATION_VECTOR)
            return;

        float[] rot = new float[9];
        SensorManager.getRotationMatrixFromVector(rot, e.values);

        float[] ori = new float[3];
        SensorManager.getOrientation(rot, ori);

        float pitch = ori[1];
        float roll = ori[2];

        long now = System.currentTimeMillis();
        float dt = (now - lastUpdate) / 1000f;

        if (dt > 0.05f)
            dt = 0.05f;

        lastUpdate = now;
        apply(pitch, roll, dt);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }

    private void apply(float pitch, float roll, float dt) {
        float gx = (float) Math.sin(roll) * GRAVITY_SCALE;
        float gy = (float) -Math.sin(pitch) * GRAVITY_SCALE;

        for (View v : icons) {
            PointF vel = velocities.get(v);
            if (vel == null)
                continue;

            vel.x += gx * dt;
            vel.y += gy * dt;

            vel.x *= LINEAR_DAMPING;
            vel.y *= LINEAR_DAMPING;

            vel.x = Math.max(-MAX_VELOCITY, Math.min(MAX_VELOCITY, vel.x));
            vel.y = Math.max(-MAX_VELOCITY, Math.min(MAX_VELOCITY, vel.y));

            float x = v.getX() + vel.x * dt;
            float y = v.getY() + vel.y * dt;

            if (x < workspaceBounds.left) {
                x = workspaceBounds.left; vel.x *= -NORMAL_VEL_DAMP;
            }
            if (x + v.getWidth() > workspaceBounds.right) {
                x = workspaceBounds.right - v.getWidth(); vel.x *= -NORMAL_VEL_DAMP;
            }
            if (y < workspaceBounds.top) {
                y = workspaceBounds.top; vel.y *= -NORMAL_VEL_DAMP;
            }
            if (y + v.getHeight() > workspaceBounds.bottom - BOTTOM_PADDING) {
                y = workspaceBounds.bottom - BOTTOM_PADDING - v.getHeight();
                vel.y *= -NORMAL_VEL_DAMP;
            }

            v.setX(x);
            v.setY(y);
        }

        for (int k = 0; k < COLLISION_ITER; k++) {
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

        float ra = (Math.min(a.getWidth(), a.getHeight()) / 2f) * HITBOX_SCALE;
        float rb = (Math.min(b.getWidth(), b.getHeight()) / 2f) * HITBOX_SCALE;

        float dx = bx - ax;
        float dy = by - ay;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        float min = ra + rb;

        if (dist > 0 && dist < min) {
            float overlap = (min - dist) * SEPARATION_STRENGTH;
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

    private void captureBounds() {
        if (workspace instanceof View) {
            View ws = (View) workspace;

            int iconRadius = 0;
            if (!icons.isEmpty()) {
                View i = icons.get(0);
                iconRadius = (int) (Math.min(i.getWidth(), i.getHeight()) / 2f);
            }

            int[] loc = new int[2];
            ws.getLocationOnScreen(loc);

            workspaceBounds.set(loc[0], loc[1], loc[0] + ws.getWidth() - iconRadius, loc[1] + ws.getHeight());
        }
    }

    private void loadConstants() {
        GRAVITY_SCALE = XposedPrefs.getInt("GRAVITY_SCALE", Constants.GRAVITY_SCALE);
        MAX_VELOCITY = XposedPrefs.getInt("MAX_VELOCITY", Constants.MAX_VELOCITY);
        LINEAR_DAMPING = XposedPrefs.getFloat("LINEAR_DAMPING", Constants.LINEAR_DAMPING);
        NORMAL_VEL_DAMP = XposedPrefs.getFloat("NORMAL_VEL_DAMP", Constants.NORMAL_VEL_DAMP);
        HITBOX_SCALE = XposedPrefs.getFloat("HITBOX_SCALE", Constants.HITBOX_SCALE);
        SEPARATION_STRENGTH = XposedPrefs.getFloat("SEPARATION_STRENGTH", Constants.SEPARATION_STRENGTH);
    }
}
