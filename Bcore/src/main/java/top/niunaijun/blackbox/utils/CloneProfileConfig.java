package top.niunaijun.blackbox.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.Locale;
import java.security.SecureRandom;

import top.niunaijun.blackbox.BlackBoxCore;

public final class CloneProfileConfig {
    private static final String PREFS_NAME = "UserRemark";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

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
            String normalized = normalizeAndroidId(value);
            if (isValidAndroidId(normalized)) {
                return normalized;
            }
        }

        String generated = generateRandomAndroidId();
        sharedPreferences.edit().putString(prefKey, generated).apply();
        return generated;
    }

    private static String normalizeAndroidId(String value) {
        if (value == null) {
            return null;
        }
        return value.replace("-", "").trim().toLowerCase(Locale.US);
    }

    private static boolean isValidAndroidId(String value) {
        if (TextUtils.isEmpty(value) || value.length() != 16) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            boolean isHex = (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f');
            if (!isHex) {
                return false;
            }
        }
        return true;
    }

    private static String generateRandomAndroidId() {
        char[] chars = new char[16];
        for (int i = 0; i < chars.length; i++) {
            int n = SECURE_RANDOM.nextInt(16);
            chars[i] = Character.forDigit(n, 16);
        }
        return new String(chars);
    }

    public static String getModel(String packageName, int userId) {
        String value = prefs().getString(key("clone_model", packageName, userId), null);
        return TextUtils.isEmpty(value) ? null : value;
    }
}
