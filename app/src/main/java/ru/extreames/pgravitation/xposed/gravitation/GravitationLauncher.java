package ru.extreames.pgravitation.xposed.gravitation;

import android.app.Activity;
import android.view.ViewGroup;

import ru.extreames.pgravitation.utiils.Utils;

public class GravitationLauncher {
    private final Activity launcher;
    private final Object workspace;

    private final ShakeDetector shakeDetector;
    private final GravitationController gravitationController;

    public GravitationLauncher(Activity launcher, Object workspace) {
        this.launcher = launcher;
        this.workspace = workspace;

        this.shakeDetector = new ShakeDetector(launcher.getApplicationContext(), this::onShake);
        this.gravitationController = new GravitationController((ViewGroup) workspace, launcher.getApplicationContext());
    }

    public boolean isSame(Activity launcher, Object workspace) {
        return this.launcher == launcher && this.workspace == workspace;
    }

    public void onStart() {
        this.shakeDetector.start();
    }

    public void onStop() {
        if (this.gravitationController.isEnabled()) {
            this.gravitationController.disable();
        }
        this.shakeDetector.stop();
    }

    public boolean onSwipe() {
        return !this.gravitationController.isEnabled(); // reject this swipe if gravitation enabled
    }

    private void onShake() {
        if (!gravitationController.isEnabled()) {
            gravitationController.enable();
        }
        else {
            gravitationController.disable();
        }
        Utils.log(Utils.DEBUG_LEVEL.INFO, "GravitationController state changed: " + gravitationController.isEnabled());
    }
}
