package ru.extreames.pgravitation.xposed.gravitation;

import android.annotation.SuppressLint;
import android.app.Activity;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import ru.extreames.pgravitation.utiils.Utils;

public class Launcher {
    @SuppressLint("StaticFieldLeak") // GravitationLauncher contains Activity field
    private static GravitationLauncher gravitationLauncher = null;
    private static final Object gravitationLocker = new Object();

    public static void onLauncherStartup(final XC_LoadPackage.LoadPackageParam lpParam) {
        if (!interceptControl(lpParam)) {
            Utils.log(Utils.DEBUG_LEVEL.ERROR, "Failed to intercept control of launcher =(");
            return;
        }
        Utils.log(Utils.DEBUG_LEVEL.INFO, "Intercepted control of launcher");
    }
    public static boolean interceptControl(final XC_LoadPackage.LoadPackageParam lpParam) {
        try {
            XposedHelpers.findAndHookMethod(
                    "com.android.launcher3.Launcher",
                    lpParam.classLoader,
                    "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            synchronized (gravitationLocker) {
                                if (gravitationLauncher == null) {
                                    gravitationLauncher = new GravitationLauncher((Activity) param.thisObject);
                                }
                                gravitationLauncher.enable();
                            }
                        }
                    });
            XposedHelpers.findAndHookMethod(
                    "com.android.launcher3.Launcher",
                    lpParam.classLoader,
                    "onPause",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            synchronized (gravitationLocker) {
                                if (gravitationLauncher != null) {
                                    gravitationLauncher.disable();
                                }
                            }
                        }
                    });
        } catch (Throwable t) {
            return false;
        }

        return true;
    }
}
