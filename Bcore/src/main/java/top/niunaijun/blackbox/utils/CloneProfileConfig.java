package top.niunaijun.blackbox.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.Locale;
import java.util.UUID;

import top.niunaijun.blackbox.BlackBoxCore;

public final class CloneProfileConfig {
    private static final String PREFS_NAME = "UserRemark";

    private CloneProfileConfig() {
    }

    private static SharedPreferences prefs() {
        return BlackBoxCore.getContext().getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    private static String key(String prefix, String packageName, int userId) {
        return prefix + "_" + userId + "_" + packageName;
    }

    public static String getProcessName(String packageName, int userId, String defaultValue) {
        String value = prefs().getString(key("clone_process", packageName, userId), null);
        return TextUtils.isEmpty(value) ? defaultValue : value;
    }

    public static synchronized String getAndroidId(String packageName, int userId) {
        SharedPreferences sharedPreferences = prefs();
        String prefKey = key("clone_android_id", packageName, userId);
        String value = sharedPreferences.getString(prefKey, null);
        if (!TextUtils.isEmpty(value)) {
            return normalizeAndroidId(value);
        }

        String generated = UUID.randomUUID().toString().replace("-", "").toLowerCase(Locale.US);
        if (generated.length() > 16) {
            generated = generated.substring(0, 16);
        }
        sharedPreferences.edit().putString(prefKey, generated).apply();
        return generated;
    }

    private static String normalizeAndroidId(String value) {
        String normalized = value.replace("-", "").trim().toLowerCase(Locale.US);
        if (normalized.length() > 16) {
            return normalized.substring(0, 16);
        }
        return normalized;
    }

    public static String getModel(String packageName, int userId) {
        String value = prefs().getString(key("clone_model", packageName, userId), null);
        return TextUtils.isEmpty(value) ? null : value;
    }
}
