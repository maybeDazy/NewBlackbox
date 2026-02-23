package top.niunaijun.blackbox.fake.delegate;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.util.Log;

import java.lang.reflect.Field;

import black.android.app.BRActivity;
import black.android.app.BRActivityThread;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.HookManager;
import top.niunaijun.blackbox.fake.hook.IInjectHook;
import top.niunaijun.blackbox.fake.service.HCallbackProxy;
import top.niunaijun.blackbox.fake.service.IActivityClientProxy;
import top.niunaijun.blackbox.utils.HackAppUtils;
import top.niunaijun.blackbox.utils.compat.ActivityCompat;
import top.niunaijun.blackbox.utils.compat.ActivityManagerCompat;
import top.niunaijun.blackbox.utils.compat.ContextCompat;

public final class AppInstrumentation extends BaseInstrumentationDelegate implements IInjectHook {

    private static final String TAG = AppInstrumentation.class.getSimpleName();

    private static AppInstrumentation sAppInstrumentation;

    public static AppInstrumentation get() {
        if (sAppInstrumentation == null) {
            synchronized (AppInstrumentation.class) {
                if (sAppInstrumentation == null) {
                    sAppInstrumentation = new AppInstrumentation();
                }
            }
        }
        return sAppInstrumentation;
    }

    public AppInstrumentation() {
    }

    @Override
    public void injectHook() {
        try {
            Instrumentation mInstrumentation = getCurrInstrumentation();
            if (mInstrumentation == this || checkInstrumentation(mInstrumentation))
                return;
            mBaseInstrumentation = (Instrumentation) mInstrumentation;
            BRActivityThread.get(BlackBoxCore.mainThread())._set_mInstrumentation(this);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private Instrumentation getCurrInstrumentation() {
        Object currentActivityThread = BlackBoxCore.mainThread();
        return BRActivityThread.get(currentActivityThread).mInstrumentation();
    }

    @Override
    public boolean isBadEnv() {
        return !checkInstrumentation(getCurrInstrumentation());
    }

    private boolean checkInstrumentation(Instrumentation instrumentation) {
        if (instrumentation instanceof AppInstrumentation) {
            return true;
        }
        Class<?> clazz = instrumentation.getClass();
        if (Instrumentation.class.equals(clazz)) {
            return false;
        }
        do {
            assert clazz != null;
            Field[] fields = clazz.getDeclaredFields();
            for (Field field : fields) {
                if (Instrumentation.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        Object obj = field.get(instrumentation);
                        if ((obj instanceof AppInstrumentation)) {
                            return true;
                        }
                    } catch (Exception e) {
                        return false;
                    }
                }
            }
            clazz = clazz.getSuperclass();
        } while (!Instrumentation.class.equals(clazz));
        return false;
    }

    private void checkHCallback() {
        HookManager.get().checkEnv(HCallbackProxy.class);
    }

    private void checkActivity(Activity activity) {
        Log.d(TAG, "callActivityOnCreate: " + activity.getClass().getName());
        HackAppUtils.enableQQLogOutput(activity.getPackageName(), activity.getClassLoader());
        checkHCallback();
        HookManager.get().checkEnv(IActivityClientProxy.class);
        ActivityInfo info = BRActivity.get(activity).mActivityInfo();
        
        // Force Hardware Acceleration
        try {
            activity.getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED,
                android.view.WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED
            );
        } catch (Throwable t) {
            t.printStackTrace();
        }

        // Force Locale to Korean
        try {
            android.content.res.Resources res = activity.getResources();
            android.content.res.Configuration config = new android.content.res.Configuration(res.getConfiguration());
            java.util.Locale koKR = new java.util.Locale("ko", "KR");
            config.setLocale(koKR);
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                config.setLocales(new android.os.LocaleList(koKR));
            }
            res.updateConfiguration(config, res.getDisplayMetrics());
        } catch (Throwable t) {
            t.printStackTrace();
        }

        ContextCompat.fix(activity);
        ActivityCompat.fix(activity);
        if (info.theme != 0) {
            activity.getTheme().applyStyle(info.theme, true);
        }
        ActivityManagerCompat.setActivityOrientation(activity, info.screenOrientation);
    }

    @Override
    public Application newApplication(ClassLoader cl, String className, Context context) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        ContextCompat.fix(context);

        return super.newApplication(cl, className, context);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle, PersistableBundle persistentState) {
        checkActivity(activity);
        super.callActivityOnCreate(activity, icicle, persistentState);
    }

    @Override
    public void callActivityOnCreate(Activity activity, Bundle icicle) {
        checkActivity(activity);
        super.callActivityOnCreate(activity, icicle);
    }


    private final java.util.WeakHashMap<Activity, Boolean> mProcessedActivities = new java.util.WeakHashMap<>();

    @Override
    public void callActivityOnResume(Activity activity) {
        super.callActivityOnResume(activity);
        try {
            if (mProcessedActivities.containsKey(activity)) {
                return;
            }
            if (BActivityThread.getAppConfig() != null && BActivityThread.getAppConfig().spoofProps != null) {
                String containerName = BActivityThread.getAppConfig().spoofProps.get("CONTAINER_NAME");
                if (containerName != null && !containerName.isEmpty()) {
                    android.view.View decorView = activity.getWindow().getDecorView();
                    if (decorView instanceof android.view.ViewGroup) {
                        android.view.ViewGroup root = (android.view.ViewGroup) decorView;
                        
                        // Check if we already added the overlay
                        boolean found = false;
                        for (int i = 0; i < root.getChildCount(); i++) {
                            android.view.View child = root.getChildAt(i);
                            if ("daize_overlay".equals(child.getTag())) {
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            android.widget.TextView overlay = new android.widget.TextView(activity);
                            overlay.setTag("daize_overlay");
                            overlay.setText(containerName + " - @DaizePro");
                            overlay.setTextColor(android.graphics.Color.WHITE);
                            overlay.setBackgroundColor(0x80000000); // Semi-transparent black
                            overlay.setPadding(20, 10, 20, 10);
                            overlay.setTextSize(12);
                            overlay.setGravity(android.view.Gravity.CENTER);
                            
                            android.widget.FrameLayout.LayoutParams params = new android.widget.FrameLayout.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT,
                                    android.view.ViewGroup.LayoutParams.WRAP_CONTENT
                            );
                            params.gravity = android.view.Gravity.TOP | android.view.Gravity.CENTER_HORIZONTAL;
                            params.topMargin = 100; // Leave space for status bar/action bar
                            
                            root.addView(overlay, params);
                            overlay.bringToFront();
                            
                            mProcessedActivities.put(activity, true);
                        }
                    }
                }
            }
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    @Override
    public void callApplicationOnCreate(Application app) {
        checkHCallback();
        super.callApplicationOnCreate(app);
    }

    public Activity newActivity(ClassLoader cl, String className, Intent intent) throws InstantiationException, IllegalAccessException, ClassNotFoundException {
        try {
            return super.newActivity(cl, className, intent);
        } catch (ClassNotFoundException e) {
            return mBaseInstrumentation.newActivity(cl, className, intent);
        }
    }
}
