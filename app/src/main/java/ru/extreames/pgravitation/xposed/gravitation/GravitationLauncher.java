package ru.extreames.pgravitation.xposed.gravitation;

import android.app.Activity;

import ru.extreames.pgravitation.utiils.Utils;

public class GravitationLauncher {
    private final Activity launcher;

    private Object workspace = null;
    private ShakeDetector shakeDetector = null;
    private GravitationProcessor gravitationProcessor = null;

    private boolean isGravitationEnabled = false;

    public GravitationLauncher(Activity launcher) {
        this.launcher = launcher;

        if (!grabWorkspace()) {
            Utils.log(Utils.DEBUG_LEVEL.ERROR, "Not found workspace =(");
            return;
        }

        this.shakeDetector = new ShakeDetector(launcher.getApplicationContext(), this::onShake);
        this.gravitationProcessor = new GravitationProcessor(workspace, launcher.getApplicationContext());

        Utils.log(Utils.DEBUG_LEVEL.INFO, "GravitationLauncher initialized");
    }

    public void enable() {
        if (shakeDetector != null) {
            this.shakeDetector.start();
        }
    }

    public void disable() {
        if (shakeDetector != null) {
            this.shakeDetector.stop();
        }
        if (isGravitationEnabled) {
            this.gravitationProcessor.disable();
            isGravitationEnabled = false;
        }
    }

    private void onShake() {
        isGravitationEnabled = !isGravitationEnabled;

        if (isGravitationEnabled) {
            gravitationProcessor.enable();
        }
        else {
            gravitationProcessor.disable();
        }

        Utils.log(Utils.DEBUG_LEVEL.INFO, "Changed state to ->> " + ((isGravitationEnabled) ? "SHAKING" : "IDLING"));
    }

    private boolean grabWorkspace() {
        try {
            workspace = Utils.tryCall(launcher, "getWorkspace");
        } catch (Throwable t) {
            Utils.log(Utils.DEBUG_LEVEL.ERROR, "Not found workspace =(");
        }
        return (workspace != null);
    }
}
