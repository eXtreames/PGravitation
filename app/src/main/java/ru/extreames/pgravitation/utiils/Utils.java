package ru.extreames.pgravitation.utiils;

import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;

public class Utils {
    public enum DEBUG_LEVEL {
        INFO,
        WARNING,
        ERROR
    }

    public static void log(DEBUG_LEVEL level, String text) {
        XposedBridge.log("[ PGravitation ] [ " + level + " ] " + text);
    }

    public static Object tryCall(Object target, String methodName) {
        try {
            return XposedHelpers.callMethod(target, methodName);
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static boolean killProcess(String name) {
        try {
            Runtime.getRuntime().exec(new String[] {
                    "su",
                    "-c",
                    "killall " + name
            });
        }
        catch (Exception ignored) {
            return false;
        }
        return true;
    }
}
