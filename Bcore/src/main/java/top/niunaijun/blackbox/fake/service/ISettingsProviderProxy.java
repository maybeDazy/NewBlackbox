package top.niunaijun.blackbox.fake.service;

import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;

import java.lang.reflect.Method;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.CloneProfileConfig;


public class ISettingsProviderProxy extends ClassInvocationStub {
    public static final String TAG = "ISettingsProviderProxy";

    public ISettingsProviderProxy() {
        super();
    }

    @Override
    protected Object getWho() {
        return null;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @ProxyMethod("getStringForUser")
    public static class GetStringForUser extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                
                String key = extractSettingKey(args);
                if (key != null && key.contains("feature_flag")) {
                    Slog.d(TAG, "Intercepting feature flag query: " + key + ", returning safe default");
                    return "true";
                }
                if (isAndroidIdRequest(key)) {
                    return resolveCloneAndroidId(args);
                }
                
                
                return method.invoke(who, args);
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("Calling uid") && errorMsg.contains("doesn't match source uid")) {
                    Slog.w(TAG, "UID mismatch in getStringForUser, returning safe default: " + errorMsg);
                    return "true"; 
                }
                throw e;
            }
        }
    }

    @ProxyMethod("getString")
    public static class GetString extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                
                String key = extractSettingKey(args);
                if (key != null && key.contains("feature_flag")) {
                    Slog.d(TAG, "Intercepting feature flag query: " + key + ", returning safe default");
                    return "true";
                }
                if (isAndroidIdRequest(key)) {
                    return resolveCloneAndroidId(args);
                }
                
                
                return method.invoke(who, args);
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("Calling uid") && errorMsg.contains("doesn't match source uid")) {
                    Slog.w(TAG, "UID mismatch in getString, returning safe default: " + errorMsg);
                    return "true"; 
                }
                throw e;
            }
        }
    }

    @ProxyMethod("getIntForUser")
    public static class GetIntForUser extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("Calling uid") && errorMsg.contains("doesn't match source uid")) {
                    Slog.w(TAG, "UID mismatch in getIntForUser, returning safe default: " + errorMsg);
                    return 1; 
                }
                throw e;
            }
        }
    }

    @ProxyMethod("getInt")
    public static class GetInt extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("Calling uid") && errorMsg.contains("doesn't match source uid")) {
                    Slog.w(TAG, "UID mismatch in getInt, returning safe default: " + errorMsg);
                    return 1; 
                }
                throw e;
            }
        }
    }

    @ProxyMethod("getLongForUser")
    public static class GetLongForUser extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("Calling uid") && errorMsg.contains("doesn't match source uid")) {
                    Slog.w(TAG, "UID mismatch in getLongForUser, returning safe default: " + errorMsg);
                    return 1L; 
                }
                throw e;
            }
        }
    }

    @ProxyMethod("getLong")
    public static class GetLong extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("Calling uid") && errorMsg.contains("doesn't match source uid")) {
                    Slog.w(TAG, "UID mismatch in getLong, returning safe default: " + errorMsg);
                    return 1L; 
                }
                throw e;
            }
        }
    }

    @ProxyMethod("getFloatForUser")
    public static class GetFloatForUser extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("Calling uid") && errorMsg.contains("doesn't match source uid")) {
                    Slog.w(TAG, "UID mismatch in getFloatForUser, returning safe default: " + errorMsg);
                    return 1.0f; 
                }
                throw e;
            }
        }
    }

    @ProxyMethod("getFloat")
    public static class GetFloat extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(who, args);
            } catch (Exception e) {
                String errorMsg = e.getMessage();
                if (errorMsg != null && errorMsg.contains("Calling uid") && errorMsg.contains("doesn't match source uid")) {
                    Slog.w(TAG, "UID mismatch in getFloat, returning safe default: " + errorMsg);
                    return 1.0f; 
                }
                throw e;
            }
        }
    }
    private static boolean isAndroidIdRequest(String key) {
        if (key == null) return false;
        String lower = key.toLowerCase();
        return Settings.Secure.ANDROID_ID.equalsIgnoreCase(lower) || lower.contains("android_id") || lower.contains("ssaid");
    }

    private static String extractSettingKey(Object[] args) {
        if (args == null || args.length == 0) {
            return null;
        }
        for (Object arg : args) {
            if (arg instanceof String) {
                String key = (String) arg;
                String lower = key.toLowerCase();
                if (lower.contains("android_id") || lower.contains("ssaid") || lower.contains("feature_flag") || Settings.Secure.ANDROID_ID.equalsIgnoreCase(key)) {
                    return key;
                }
            }
        }
        return args[0] instanceof String ? (String) args[0] : null;
    }

    private static String resolveCloneAndroidId(Object[] args) {
        String packageName = BActivityThread.getAppPackageName();
        if (packageName == null || packageName.trim().isEmpty() || packageName.equals(BlackBoxCore.getHostPkg())) {
            packageName = extractCallingPackage(args);
        }
        if (packageName == null || packageName.trim().isEmpty()) {
            packageName = BlackBoxCore.get().getCurrentAppPackage();
        }
        if (packageName == null || packageName.trim().isEmpty()) {
            packageName = BlackBoxCore.getHostPkg();
        }
        int userId = BActivityThread.getUserId();
        if (userId < 0) {
            userId = extractUserId(args);
        }
        if (userId < 0) {
            userId = 0;
        }
        return CloneProfileConfig.getAndroidId(packageName, userId);
    }

    private static String extractCallingPackage(Object[] args) {
        if (args == null) return null;
        for (Object arg : args) {
            if (arg instanceof String) {
                String value = (String) arg;
                if (isLikelyPackageName(value)) {
                    return value;
                }
            }
        }
        return null;
    }

    private static int extractUserId(Object[] args) {
        if (args == null) return -1;
        for (Object arg : args) {
            if (arg instanceof Integer) {
                int value = (Integer) arg;
                if (value >= 0 && value <= 99999) {
                    return value;
                }
            }
        }
        return -1;
    }

    private static boolean isLikelyPackageName(String value) {
        if (value == null) return false;
        if (!value.contains(".")) return false;
        String lower = value.toLowerCase();
        if (lower.contains("android_id") || lower.contains("ssaid") || lower.contains("feature_flag") || lower.startsWith("get_") || lower.startsWith("put_")) {
            return false;
        }
        if (value.equals(BlackBoxCore.getHostPkg()) || "android".equals(lower)) {
            return false;
        }
        return true;
    }

    @ProxyMethod("call")
    public static class Call extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            String methodName = null;
            String requestArg = null;
            if (args != null) {
                for (Object arg : args) {
                    if (arg instanceof String) {
                        String value = (String) arg;
                        if (methodName == null && (value.startsWith("GET_") || value.startsWith("PUT_"))) {
                            methodName = value;
                        } else if (requestArg == null) {
                            requestArg = value;
                        }
                    }
                }
            }

            if (isAndroidIdRequest(requestArg) || (isAndroidIdRequest(extractSettingKey(args)) && methodName != null && methodName.startsWith("GET_"))) {
                Bundle bundle = new Bundle();
                bundle.putString("value", resolveCloneAndroidId(args));
                return bundle;
            }
            return method.invoke(who, args);
        }
    }

}
