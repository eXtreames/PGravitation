package ru.extreames.pgravitation.utiils;

import android.hardware.SensorManager;

public class Constants {
    public static final int COLLISION_ITERATIONS = 3;
    public static final int ANIMATION_DURATION = 600;
    public static final float ICON_SCALE = 0.94F;

    public static final int GRAVITY_SCALE = 15000;
    public static final int MAX_VELOCITY = 2000;
    public static final float LINEAR_DAMPING = 1.0f; // 1.1f
    public static final float NORMAL_VEL_DAMP = 0.1f;
    public static final float HITBOX_SCALE = 0.8f;
    public static final float SEPARATION_STRENGTH = 0.2f;

    public static final float SHAKE_THRESHOLD_G = 21.0f / SensorManager.GRAVITY_EARTH;
    public static final int SHAKE_WAIT_TIME_MS = 800;
}
