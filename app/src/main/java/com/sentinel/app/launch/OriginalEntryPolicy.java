package com.sentinel.app.launch;

import android.content.Intent;

/** Only the verified launcher entry is eligible; incoming links and other apps pass through. */
public final class OriginalEntryPolicy {
    public static final String PKG = "com.cctv.yangshipin.app.androidp";
    public static final String SPLASH = "com.tencent.videolite.android.ui.SplashActivity";
    public static final String OPEN = "com.tencent.videolite.android.component.literoute.OpenActivity";
    public static boolean matches(Intent intent, String pkg, int sdk, long version, String name, boolean available) {
        return sdk == 31 && version == 305030 && "3.5.3.26910".equals(name) && available &&
            PKG.equals(pkg) && intent != null && Intent.ACTION_MAIN.equals(intent.getAction()) &&
            intent.hasCategory(Intent.CATEGORY_LAUNCHER) && intent.getComponent() != null &&
            PKG.equals(intent.getComponent().getPackageName()) && SPLASH.equals(intent.getComponent().getClassName());
    }
}
