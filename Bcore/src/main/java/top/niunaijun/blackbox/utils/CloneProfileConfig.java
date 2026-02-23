package top.niunaijun.blackbox.utils;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

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

    public static String getAndroidId(String packageName, int userId) {
        String value = prefs().getString(key("clone_android_id", packageName, userId), null);
        return TextUtils.isEmpty(value) ? null : value;
    }

    public static String getModel(String packageName, int userId) {
        String value = prefs().getString(key("clone_model", packageName, userId), null);
        return TextUtils.isEmpty(value) ? null : value;
    }
}
