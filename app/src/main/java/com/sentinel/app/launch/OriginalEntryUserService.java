package com.sentinel.app.launch;

import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.util.Log;
import androidx.annotation.Keep;
import java.lang.reflect.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Runs as the Shizuku shell user. No caller-supplied component, URI or shell command is accepted. */
@Keep
public final class OriginalEntryUserService extends Binder {
    public static final String TOKEN = "com.sentinel.app.OriginalEntry";
    private volatile boolean enabled;
    private volatile String failure = "";
    private final AtomicBoolean pending = new AtomicBoolean();
    private final AtomicBoolean fallback = new AtomicBoolean();
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private Object manager;
    private Method setter;
    private Object controller;

    private boolean eligible(Intent intent, String pkg) {
        try {
            if (!OriginalEntryPolicy.PKG.equals(pkg) || Build.VERSION.SDK_INT != 31) return false;
            Object pm = Class.forName("android.app.ActivityThread").getMethod("getPackageManager").invoke(null);
            Method info = pm.getClass().getMethod("getPackageInfo", String.class, int.class, int.class);
            Method activity = pm.getClass().getMethod("getActivityInfo", ComponentName.class, int.class, int.class);
            info.setAccessible(true); activity.setAccessible(true);
            PackageInfo p = (PackageInfo) info.invoke(pm, pkg, 0, 0);
            ActivityInfo a = (ActivityInfo) activity.invoke(pm, new ComponentName(pkg, OriginalEntryPolicy.OPEN), 0, 0);
            return p != null && OriginalEntryPolicy.matches(intent, pkg, Build.VERSION.SDK_INT,
                p.getLongVersionCode(), p.versionName, a != null && a.enabled && a.exported &&
                a.applicationInfo.enabled && a.permission == null);
        } catch (Exception e) { failure = "版本或入口检查失败，已放行普通启动"; return false; }
    }
    private synchronized void enable(boolean value) throws Exception {
        if (value == enabled) return;
        if (value) {
            if (Build.VERSION.SDK_INT != 31) throw new IllegalStateException("仅验证 Android 12 / API 31");
            Class<?> iface = Class.forName("android.app.IActivityController");
            Binder callback = new Binder() {
                @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
                    if (code == INTERFACE_TRANSACTION) { reply.writeString("android.app.IActivityController"); return true; }
                    if (code < 1 || code > 6) return super.onTransact(code, data, reply, flags);
                    data.enforceInterface("android.app.IActivityController");
                    int result = code <= 3 ? 1 : code <= 5 ? 0 : -1;
                    if (code == 1) {
                        Intent intent = data.readInt() != 0 ? Intent.CREATOR.createFromParcel(data) : null;
                        String pkg = data.readString();
                        if (enabled && eligible(intent, pkg)) {
                            if (fallback.compareAndSet(true, false)) return replyResult(reply, 1);
                            if (pending.compareAndSet(false, true)) {
                                try { worker.execute(() -> redirect()); }
                                catch (RejectedExecutionException e) { pending.set(false); return replyResult(reply, 1); }
                            }
                            result = 0;
                        }
                    }
                    return replyResult(reply, result);
                }
            };
            controller = Proxy.newProxyInstance(iface.getClassLoader(), new Class<?>[]{iface},
                (p, m, a) -> m.getName().equals("asBinder") ? callback : null);
            manager = Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);
            setter = manager.getClass().getMethod("setActivityController", iface, boolean.class);
            setter.setAccessible(true);
            enabled = true;
            try { setter.invoke(manager, controller, false); failure = ""; }
            catch (Exception e) { enabled = false; throw e; }
        } else {
            enabled = false;
            setter.invoke(manager, null, false);
        }
    }
    private static boolean replyResult(Parcel reply, int result) {
        reply.writeNoException(); reply.writeInt(result); return true;
    }
    private void redirect() {
        try {
            Thread.sleep(100);
            if (!enabled) { fallback.set(true); normal(); return; }
            if (!run("/system/bin/am", "start", "-a", Intent.ACTION_VIEW, "-c", Intent.CATEGORY_BROWSABLE,
                "-d", "cctvvideo://cctv.com/HomeActivity?from=third_h5", "-n",
                OriginalEntryPolicy.PKG + "/" + OriginalEntryPolicy.OPEN, "-f", "0x10200000")) {
                failure = "快捷启动失败，已回退普通启动"; fallback.set(true); normal();
            } else Log.i("SentinelOriginalEntry", "ORIGINAL_ICON_REDIRECTED");
        } catch (Exception e) {
            failure = "快捷启动异常，已回退普通启动";
            fallback.set(true); normal();
        } finally { pending.set(false); }
    }
    private void normal() {
        try { run("/system/bin/am", "start", "-a", Intent.ACTION_MAIN, "-c", Intent.CATEGORY_LAUNCHER,
            "-n", OriginalEntryPolicy.PKG + "/" + OriginalEntryPolicy.SPLASH, "-f", "0x10200000"); }
        catch (Exception e) { Log.w("SentinelOriginalEntry", "普通启动失败", e); }
        finally { fallback.set(false); }
    }
    private boolean run(String... args) throws Exception {
        java.lang.Process p = new ProcessBuilder(args).redirectErrorStream(true).start();
        CompletableFuture<String> output = CompletableFuture.supplyAsync(() -> {
            try (java.io.InputStream stream = p.getInputStream()) {
                java.io.ByteArrayOutputStream kept = new java.io.ByteArrayOutputStream();
                byte[] buffer = new byte[1024]; int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (kept.size() < 16384) kept.write(buffer, 0, Math.min(count, 16384 - kept.size()));
                }
                return kept.toString("UTF-8");
            } catch (Exception e) { return "Error: response unavailable"; }
        });
        try { return p.waitFor(5, TimeUnit.SECONDS) && commandSucceeded(p.exitValue(), output.get(1, TimeUnit.SECONDS)); }
        finally { p.destroyForcibly(); }
    }
    static boolean commandSucceeded(int exitCode, String output) {
        return exitCode == 0 && !java.util.regex.Pattern.compile("(?m)^(Error|Exception)").matcher(output).find();
    }
    @Override protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
        if (code == INTERFACE_TRANSACTION) { reply.writeString(TOKEN); return true; }
        if (code == 16777115) {
            try { enable(false); } catch (Exception ignored) { }
            worker.shutdownNow(); System.exit(0); return true;
        }
        if (code != 1 && code != 2) return super.onTransact(code, data, reply, flags);
        data.enforceInterface(TOKEN);
        try {
            if (code == 1) enable(data.readInt() != 0);
            reply.writeNoException(); reply.writeString(enabled ? "已启用原图标自动转接" + (failure.isEmpty() ? "" : "；" + failure) : "已关闭原图标自动转接");
        } catch (Exception e) { reply.writeException(new IllegalStateException("原图标转接不可用：" + e.getClass().getSimpleName())); }
        return true;
    }
}
