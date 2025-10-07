package ru.extreames.pgravitation.xposed.gravitation;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.view.MotionEvent;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import ru.extreames.pgravitation.utiils.Utils;

public class Launcher {
    @SuppressLint("StaticFieldLeak") // GravitationLauncher contains Activity field
    private static GravitationLauncher gravitationLauncher = null;
    private static final Object gravitationLocker = new Object();

    public static void onLauncherStartup(final XC_LoadPackage.LoadPackageParam lpParam) {
        if (!interceptLauncher(lpParam)) {
            Utils.log(Utils.DEBUG_LEVEL.ERROR, "Failed to intercept launcher =(");
        }
    }
    public static boolean interceptLauncher(final XC_LoadPackage.LoadPackageParam lpParam) {
        try {
            Class<?> Launcher3 = XposedHelpers.findClass("com.android.launcher3.Launcher", lpParam.classLoader);
            Class<?> Workspace = XposedHelpers.findClass("com.android.launcher3.Workspace", lpParam.classLoader);

            XposedHelpers.findAndHookMethod(
                    Launcher3,
                    "onResume",
                    new XC_MethodHook() {
                        @Override
                        protected void afterHookedMethod(MethodHookParam param) {
                            synchronized (gravitationLocker) {
                                Activity launcher = (Activity) param.thisObject;
                                Object workspace = getWorkspace(launcher);

                                if (workspace == null) {
                                    Utils.log(Utils.DEBUG_LEVEL.ERROR, "Not found workspace =(");
                                    return;
                                }
                                if (gravitationLauncher == null || !gravitationLauncher.isSame(launcher, workspace)) {
                                    if (gravitationLauncher != null) { // something changed
                                        gravitationLauncher.onStop(); // just in case
                                    }

                                    gravitationLauncher = new GravitationLauncher(launcher, workspace);
                                    Utils.log(Utils.DEBUG_LEVEL.INFO, "GravitationLauncher reinitialized");
                                }

                                gravitationLauncher.onStart();
                            }
                        }
                    });
            XposedHelpers.findAndHookMethod(
                    Launcher3,
                    "onPause",
                    new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            synchronized (gravitationLocker) {
                                if (gravitationLauncher != null) {
                                    gravitationLauncher.onStop();
                                }
                            }
                        }
                    });

            XposedHelpers.findAndHookMethod(Workspace, "onTouchEvent", MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    synchronized (gravitationLocker) {
                        MotionEvent event = (MotionEvent) param.args[0];
                        if (gravitationLauncher != null && event.getAction() == MotionEvent.ACTION_MOVE && !gravitationLauncher.onSwipe()) {
                            param.setResult(false);
                        }
                    }
                }
            });
            XposedHelpers.findAndHookMethod(Workspace, "onInterceptTouchEvent", MotionEvent.class, new XC_MethodHook() {
                @Override
                protected void beforeHookedMethod(MethodHookParam param) {
                    synchronized (gravitationLocker) {
                        if (gravitationLauncher != null && !gravitationLauncher.onSwipe()) {
                            param.setResult(false);
                        }
                    }
                }
            });
        } catch (Exception e) {
            return false; // Not an Launcher3 or other error
        }

        return true;
    }

    private static Object getWorkspace(Object launcher) {
        try {
            return Utils.tryCall(launcher, "getWorkspace");
        } catch (Throwable t) {
            return null;
        }
    }
}
