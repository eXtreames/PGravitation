package ru.extreames.pgravitation.xposed.gravitation;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

import ru.extreames.pgravitation.utiils.Constants;
import ru.extreames.pgravitation.utiils.Utils;
import ru.extreames.pgravitation.xposed.XposedPrefs;

public class ShakeDetector implements SensorEventListener {
    public interface OnShakeListener {
        void onShake();
    }

    private static final float ALPHA_LOW = 0.4f;
    private static final float ALPHA_HIGH = 0.8f;

    private static float SHAKE_THRESHOLD_G; // INIT_ON_CONSTRUCTOR
    private static int SHAKE_WAIT_TIME_MS; // INIT_ON_CONSTRUCTOR

    private final SensorManager sensorManager;
    private final OnShakeListener listener;

    private final float[] gravity = new float[3];
    private final float[] filteredAccel = new float[3];
    private long lastShakeTime = 0;

    public ShakeDetector(Context ctx, OnShakeListener listener) {
        this.sensorManager = (SensorManager) ctx.getSystemService(Context.SENSOR_SERVICE);
        this.listener = listener;

        SHAKE_THRESHOLD_G = XposedPrefs.getFloat("SHAKE_THRESHOLD_G", Constants.SHAKE_THRESHOLD_G) / SensorManager.GRAVITY_EARTH;
        SHAKE_WAIT_TIME_MS = XposedPrefs.getInt("SHAKE_WAIT_TIME_MS", Constants.SHAKE_WAIT_TIME_MS);
    }

    public void start() {
        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        if (accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME); // SENSOR_DELAY_NORMAL can be used
        }
        else {
            Utils.log(Utils.DEBUG_LEVEL.ERROR, "Not found accelerometer =(");
        }
    }

    public void stop() {
        sensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float[] values = event.values;

        filteredAccel[0] = ALPHA_LOW * filteredAccel[0] + (1 - ALPHA_LOW) * values[0];
        filteredAccel[1] = ALPHA_LOW * filteredAccel[1] + (1 - ALPHA_LOW) * values[1];
        filteredAccel[2] = ALPHA_LOW * filteredAccel[2] + (1 - ALPHA_LOW) * values[2];

        gravity[0] = ALPHA_HIGH * gravity[0] + (1 - ALPHA_HIGH) * filteredAccel[0];
        gravity[1] = ALPHA_HIGH * gravity[1] + (1 - ALPHA_HIGH) * filteredAccel[1];
        gravity[2] = ALPHA_HIGH * gravity[2] + (1 - ALPHA_HIGH) * filteredAccel[2];

        float x = filteredAccel[0] - gravity[0];
        float y = filteredAccel[1] - gravity[1];
        float z = filteredAccel[2] - gravity[2];

        float gForce = (float) Math.sqrt(x * x + y * y + z * z) / SensorManager.GRAVITY_EARTH;
        long now = System.currentTimeMillis();

        if (gForce > SHAKE_THRESHOLD_G && now - lastShakeTime > SHAKE_WAIT_TIME_MS) {
            lastShakeTime = now;
            if (listener != null)
                listener.onShake();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) { }
}
