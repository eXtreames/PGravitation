package ru.extreames.pgravitation.xposed;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import ru.extreames.pgravitation.utiils.Constants;
import ru.extreames.pgravitation.xposed.gravitation.Launcher;

public class XposedInit implements IXposedHookLoadPackage {
    @Override
    public void handleLoadPackage(final XC_LoadPackage.LoadPackageParam lpParam) {
        if (lpParam.packageName.equals(Constants.PACKAGE_NEXUS_LAUNCHER) || lpParam.packageName.equals(Constants.PACKAGE_LAUNCHER3))
            Launcher.onLauncherStartup(lpParam);
    }
}
