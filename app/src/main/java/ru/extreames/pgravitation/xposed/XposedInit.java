package ru.extreames.pgravitation.xposed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import ru.extreames.pgravitation.xposed.gravitation.Launcher;

public class XposedInit implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpParam) {
        Launcher.onLauncherStartup(lpParam);
    }
}
