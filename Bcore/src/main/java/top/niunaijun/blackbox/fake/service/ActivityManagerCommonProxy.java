package top.niunaijun.blackbox.fake.service;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.SystemClock;

import java.io.File;
import java.lang.reflect.Method;
import java.util.List;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.fake.provider.FileProviderHandler;
import top.niunaijun.blackbox.utils.ComponentUtils;
import top.niunaijun.blackbox.utils.MethodParameterUtils;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BuildCompat;
import top.niunaijun.blackbox.utils.compat.StartActivityCompat;

import static android.content.pm.PackageManager.GET_META_DATA;


public class ActivityManagerCommonProxy {
    public static final String TAG = "CommonStub";
    private static final String ACTION_REQUEST_PERMISSIONS = "android.content.pm.action.REQUEST_PERMISSIONS";
    // Some apps trigger multiple startActivity calls as part of startup handshakes.
    // Keep throttling disabled by default to avoid introducing perceived launch lag.
    private static final boolean ENABLE_ACTIVITY_THROTTLE = false;
    private static long sLastPermissionRequestUptime;
    private static String sLastPermissionRequestKey;
    private static long sLastProxyLaunchUptime;
    private static String sLastProxyLaunchKey;
    private static long sLastSelfIntentLaunchUptime;
    private static String sLastSelfIntentLaunchKey;


    @ProxyMethod("startActivity")
    public static class StartActivity extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            Intent intent = getIntent(args);
            Slog.d(TAG, "Hook in : " + intent);
            assert intent != null;
            
            
            if (isRapidDuplicateProxyLaunch(intent)) {
                Slog.w(TAG, "Dropped rapid duplicate proxy launch: " + intent);
                return 0;
            }

            if (intent.getParcelableExtra("_B_|_target_") != null) {
                return method.invoke(who, args);
            }

            if (isPermissionRequestIntent(intent)) {
                if (shouldThrottlePermissionRequest(intent)) {
                    Slog.w(TAG, "Throttled duplicated permission request intent: " + intent);
                    return 0;
                }
                return method.invoke(who, args);
            }

            if (shouldThrottleSelfComponentLaunch(intent)) {
                Slog.w(TAG, "Throttled rapid duplicate self deep-link launch: " + intent);
                return 0;
            }

            if (ComponentUtils.isRequestInstall(intent)) {
                File file = FileProviderHandler.convertFile(BActivityThread.getApplication(), intent.getData());
                
                
                if (file != null && file.exists()) {
                    try {
                        PackageInfo packageInfo = BlackBoxCore.getPackageManager().getPackageArchiveInfo(file.getAbsolutePath(), 0);
                        if (packageInfo != null) {
                            String packageName = packageInfo.packageName;
                            String hostPackageName = BlackBoxCore.getHostPkg();
                            if (packageName.equals(hostPackageName)) {
                                Slog.w(TAG, "Blocked attempt to install BlackBox app from within BlackBox: " + packageName);
                                
                                return 0;
                            }
                        }
                    } catch (Exception e) {
                        Slog.w(TAG, "Could not verify if this is BlackBox app: " + e.getMessage());
                    }
                }
                
                if (BlackBoxCore.get().requestInstallPackage(file, BActivityThread.getUserId())) {
                    return 0;
                }
                intent.setData(FileProviderHandler.convertFileUri(BActivityThread.getApplication(), intent.getData()));
                return method.invoke(who, args);
            }
            String dataString = intent.getDataString();
            if (dataString != null && dataString.equals("package:" + BActivityThread.getAppPackageName())) {
                intent.setData(Uri.parse("package:" + BlackBoxCore.getHostPkg()));
            }

            ResolveInfo resolveInfo = BlackBoxCore.getBPackageManager().resolveActivity(
                    intent,
                    GET_META_DATA,
                    StartActivityCompat.getResolvedType(args),
                    BActivityThread.getUserId());
            if (resolveInfo == null) {
                String origPackage = intent.getPackage();
                if (intent.getPackage() == null && intent.getComponent() == null) {
                    intent.setPackage(BActivityThread.getAppPackageName());
                } else {
                    origPackage = intent.getPackage();
                }
                resolveInfo = BlackBoxCore.getBPackageManager().resolveActivity(
                        intent,
                        GET_META_DATA,
                        StartActivityCompat.getResolvedType(args),
                        BActivityThread.getUserId());
                if (resolveInfo == null) {
                    intent.setPackage(origPackage);
                    if (shouldBlockHostFallback(intent)) {
                        Slog.w(TAG, "Blocked host fallback for in-app deep link to avoid intent leak: " + intent);
                        return 0;
                    }
                    return method.invoke(who, args);
                }
            }


            intent.setExtrasClassLoader(who.getClass().getClassLoader());
            intent.setComponent(new ComponentName(resolveInfo.activityInfo.packageName, resolveInfo.activityInfo.name));
            BlackBoxCore.getBActivityManager().startActivityAms(BActivityThread.getUserId(),
                    StartActivityCompat.getIntent(args),
                    StartActivityCompat.getResolvedType(args),
                    StartActivityCompat.getResultTo(args),
                    StartActivityCompat.getResultWho(args),
                    StartActivityCompat.getRequestCode(args),
                    StartActivityCompat.getFlags(args),
                    StartActivityCompat.getOptions(args));
            return 0;
        }

        private Intent getIntent(Object[] args) {
            int index;
            if (BuildCompat.isR()) {
                index = 3;
            } else {
                index = 2;
            }
            if (args[index] instanceof Intent) {
                return (Intent) args[index];
            }
            for (Object arg : args) {
                if (arg instanceof Intent) {
                    return (Intent) arg;
                }
            }
            return null;
        }
    }

    @ProxyMethod("startActivities")
    public static class StartActivities extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            int index = getIntents();
            Intent[] intents = (Intent[]) args[index++];
            String[] resolvedTypes = (String[]) args[index++];
            IBinder resultTo = (IBinder) args[index++];
            Bundle options = (Bundle) args[index];
            
            if (!ComponentUtils.isSelf(intents)) {
                return method.invoke(who, args);
            }

            for (Intent intent : intents) {
                intent.setExtrasClassLoader(who.getClass().getClassLoader());
            }
            return BlackBoxCore.getBActivityManager().startActivities(BActivityThread.getUserId(),
                    intents, resolvedTypes, resultTo, options);
        }

        public int getIntents() {
            if (BuildCompat.isR()) {
                return 3;
            }
            return 2;
        }
    }

    @ProxyMethod("startIntentSenderForResult")
    public static class StartIntentSenderForResult extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("activityResumed")
    public static class ActivityResumed extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            BlackBoxCore.getBActivityManager().onActivityResumed((IBinder) args[0]);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("activityDestroyed")
    public static class ActivityDestroyed extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            BlackBoxCore.getBActivityManager().onActivityDestroyed((IBinder) args[0]);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("finishActivity")
    public static class FinishActivity extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null) {
                if (args.length > 1 && args[1] instanceof Integer) {
                    int code = (Integer) args[1];
                    String dataStr = "null";
                    if (args.length > 2 && args[2] instanceof Intent) {
                        dataStr = String.valueOf(args[2]);
                    }
                    android.util.Log.d("DEBUG-AUTH", "finishActivity: code=" + code + ", data=" + dataStr);
                }
            }
            boolean suppressed = BlackBoxCore.getBActivityManager().onFinishActivity((IBinder) args[0]);
            if (suppressed) {
                // Loop detected — do NOT forward finish to real system.
                // Return true to tell the caller finish "succeeded" without actually destroying the activity.
                android.util.Log.e("FinishActivity", "LOOP BREAKER: finish() call blocked by suppression");
                return true;
            }
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getAppTasks")
    public static class GetAppTasks extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            MethodParameterUtils.replaceFirstAppPkg(args);
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("getCallingPackage")
    public static class getCallingPackage extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return BlackBoxCore.getBActivityManager().getCallingPackage((IBinder) args[0], BActivityThread.getUserId());
        }
    }

    @ProxyMethod("getCallingActivity")
    public static class getCallingActivity extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            return BlackBoxCore.getBActivityManager().getCallingActivity((IBinder) args[0], BActivityThread.getUserId());
        }
    }

    private static boolean isRapidDuplicateProxyLaunch(Intent intent) {
        if (!ENABLE_ACTIVITY_THROTTLE) {
            return false;
        }
        if (intent == null) return false;
        ComponentName component = intent.getComponent();
        if (component == null) return false;

        String cls = component.getClassName();
        if (cls == null || !cls.contains("top.niunaijun.blackbox.proxy.ProxyActivity$P")) {
            return false;
        }

        Intent target = intent.getParcelableExtra("_B_|_target_");
        String targetCmp = target != null && target.getComponent() != null
                ? target.getComponent().flattenToShortString() : "null";
        String key = BActivityThread.getAppPackageName() + "@" + BActivityThread.getUserId() + "@" + cls + "@" + targetCmp;

        long now = SystemClock.elapsedRealtime();
        synchronized (ActivityManagerCommonProxy.class) {
            boolean duplicated = key.equals(sLastProxyLaunchKey) && (now - sLastProxyLaunchUptime) < 800;
            sLastProxyLaunchKey = key;
            sLastProxyLaunchUptime = now;
            return duplicated;
        }
    }

    private static boolean isPermissionRequestIntent(Intent intent) {
        if (intent == null) return false;

        if (ACTION_REQUEST_PERMISSIONS.equals(intent.getAction())) {
            return true;
        }

        String pkg = intent.getPackage();
        if (pkg != null && pkg.contains("permissioncontroller")) {
            return true;
        }

        ComponentName component = intent.getComponent();
        return component != null && component.getPackageName() != null
                && component.getPackageName().contains("permissioncontroller");
    }

    private static boolean shouldThrottlePermissionRequest(Intent intent) {
        if (!ENABLE_ACTIVITY_THROTTLE) {
            return false;
        }
        String key = BActivityThread.getAppPackageName() + "@" + BActivityThread.getUserId();
        long now = SystemClock.elapsedRealtime();
        synchronized (ActivityManagerCommonProxy.class) {
            boolean duplicated = key.equals(sLastPermissionRequestKey) && (now - sLastPermissionRequestUptime) < 1200;
            sLastPermissionRequestKey = key;
            sLastPermissionRequestUptime = now;
            return duplicated;
        }
    }

    private static boolean shouldBlockHostFallback(Intent intent) {
        if (intent == null) {
            return false;
        }
        if (!Intent.ACTION_VIEW.equals(intent.getAction())) {
            return false;
        }

        String appPkg = BActivityThread.getAppPackageName();
        String intentPkg = intent.getPackage();
        if (appPkg == null || intentPkg == null || !appPkg.equals(intentPkg)) {
            return false;
        }

        Uri data = intent.getData();
        if (data == null) {
            return false;
        }

        String scheme = data.getScheme();
        if (scheme == null) {
            return false;
        }

        String normalized = scheme.toLowerCase();
        return !("http".equals(normalized) || "https".equals(normalized));
    }

    private static boolean shouldThrottleSelfComponentLaunch(Intent intent) {
        if (!ENABLE_ACTIVITY_THROTTLE) {
            return false;
        }
        if (intent == null) {
            return false;
        }
        ComponentName component = intent.getComponent();
        if (component == null) {
            return false;
        }

        String appPkg = BActivityThread.getAppPackageName();
        if (appPkg == null || !appPkg.equals(component.getPackageName())) {
            return false;
        }

        // Only throttle explicit VIEW deep-link loops. Do not block normal in-app button navigation.
        if (!Intent.ACTION_VIEW.equals(intent.getAction())) {
            return false;
        }

        String data = intent.getDataString();
        if (data == null || data.isEmpty()) {
            return false;
        }
        Uri uri = intent.getData();
        if (uri != null) {
            String scheme = uri.getScheme();
            if (scheme != null) {
                String normalized = scheme.toLowerCase();
                if ("http".equals(normalized) || "https".equals(normalized)) {
                    return false;
                }
            }
        }

        // Explicit in-app deep-link launches that loop rapidly (tab/deeplink bounce) can cause
        // repeated proxy transitions; keep a conservative short-window dedupe.
        String key = appPkg + "@" + BActivityThread.getUserId() + "@"
                + component.flattenToShortString() + "@" + (data == null ? "" : data);

        long now = SystemClock.elapsedRealtime();
        synchronized (ActivityManagerCommonProxy.class) {
            boolean duplicated = key.equals(sLastSelfIntentLaunchKey) && (now - sLastSelfIntentLaunchUptime) < 300;
            sLastSelfIntentLaunchKey = key;
            sLastSelfIntentLaunchUptime = now;
            return duplicated;
        }
    }

}
