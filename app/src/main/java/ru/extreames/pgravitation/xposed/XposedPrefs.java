package ru.extreames.pgravitation.xposed;

import de.robv.android.xposed.XSharedPreferences;
import ru.extreames.pgravitation.utiils.Constants;
import ru.extreames.pgravitation.utiils.Utils;

public class XposedPrefs {
    private static XSharedPreferences prefs = null;

    public static void initialize() {
        if (prefs != null) {
            return;
        }

        prefs = new XSharedPreferences(Constants.PREFS_PACKAGE, Constants.PREFS_CATEGORY);
        if (!prefs.getFile().canRead()) {
            Utils.log(Utils.DEBUG_LEVEL.ERROR, "Cannot read shared preferences =(");
        }
    }

    public static float getFloat(String name, float defValue) {
        return defValue;
        /*if (prefs == null) {
            initialize();
        }
        else {
            prefs.reload();
        }
        return prefs.getFloat(name, defValue);*/
    }
    public static int getInt(String name, int defValue) {
        return defValue;
        /*if (prefs == null) {
            initialize();
        }
        else {
            prefs.reload();
        }
        return prefs.getInt(name, defValue);*/
    }
    public static boolean getBoolean(String name, boolean defValue) {
        return defValue;
        /*if (prefs == null) {
            initialize();
        }
        else {
            prefs.reload();
        }
        return prefs.getBoolean(name, defValue);*/
    }
}
