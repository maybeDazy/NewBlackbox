package top.niunaijun.blackbox.proxy;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;

import androidx.annotation.Nullable;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.HookManager;
import top.niunaijun.blackbox.fake.service.HCallbackProxy;
import top.niunaijun.blackbox.proxy.record.ProxyActivityRecord;
import top.niunaijun.blackbox.proxy.record.ProxyPendingRecord;
import top.niunaijun.blackbox.utils.Slog;


public class ProxyActivity extends Activity {
    public static final String TAG = "ProxyActivity";
    private static final long RELAUNCH_WINDOW_MS = 15_000L;
    private static final int MAX_RELAUNCH_COUNT = 3;
    private static final Map<String, RelaunchGuard> sRelaunchGuards = new HashMap<>();

    private static final class RelaunchGuard {
        long windowStartMs;
        int count;
    }

    
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(TAG, "onCreate");
        finish();

        HookManager.get().checkEnv(HCallbackProxy.class);


        ProxyActivityRecord record = ProxyActivityRecord.create(getIntent());
        if (record.mTarget != null) {
            record.mTarget.setExtrasClassLoader(BlackBoxCore.getApplication().getClassLoader());

            String guardKey = resolveGuardKey(record);
            if (!allowRelaunch(guardKey)) {
                Slog.w(TAG, "Skip target relaunch due to guard: " + guardKey);
                return;
            }

            startActivity(record.mTarget);
            return;
        }
    }

    private static String resolveGuardKey(ProxyActivityRecord record) {
        if (record == null) {
            return "unknown";
        }
        if (record.mActivityInfo != null) {
            return record.mActivityInfo.packageName + "/" + record.mActivityInfo.name;
        }
        if (record.mTarget == null) {
            return "unknown";
        }
        if (record.mTarget.getComponent() != null) {
            return record.mTarget.getComponent().flattenToShortString();
        }
        if (record.mTarget.getPackage() != null) {
            return record.mTarget.getPackage();
        }
        return record.mTarget.getAction() != null ? record.mTarget.getAction() : "unknown";
    }

    private static synchronized boolean allowRelaunch(String key) {
        long now = SystemClock.elapsedRealtime();
        RelaunchGuard guard = sRelaunchGuards.get(key);
        if (guard == null) {
            guard = new RelaunchGuard();
            guard.windowStartMs = now;
            guard.count = 1;
            sRelaunchGuards.put(key, guard);
            return true;
        }

        if (now - guard.windowStartMs > RELAUNCH_WINDOW_MS) {
            guard.windowStartMs = now;
            guard.count = 1;
            return true;
        }

        guard.count++;
        return guard.count <= MAX_RELAUNCH_COUNT;
    }


    public static class P0 extends ProxyActivity {

    }

    public static class P1 extends ProxyActivity {

    }

    public static class P2 extends ProxyActivity {

    }

    public static class P3 extends ProxyActivity {

    }

    public static class P4 extends ProxyActivity {

    }

    public static class P5 extends ProxyActivity {

    }

    public static class P6 extends ProxyActivity {

    }

    public static class P7 extends ProxyActivity {

    }

    public static class P8 extends ProxyActivity {

    }

    public static class P9 extends ProxyActivity {

    }

    public static class P10 extends ProxyActivity {

    }

    public static class P11 extends ProxyActivity {

    }

    public static class P12 extends ProxyActivity {

    }

    public static class P13 extends ProxyActivity {

    }

    public static class P14 extends ProxyActivity {

    }

    public static class P15 extends ProxyActivity {

    }

    public static class P16 extends ProxyActivity {

    }

    public static class P17 extends ProxyActivity {

    }

    public static class P18 extends ProxyActivity {

    }

    public static class P19 extends ProxyActivity {

    }

    public static class P20 extends ProxyActivity {

    }

    public static class P21 extends ProxyActivity {

    }

    public static class P22 extends ProxyActivity {

    }

    public static class P23 extends ProxyActivity {

    }

    public static class P24 extends ProxyActivity {

    }

    public static class P25 extends ProxyActivity {

    }

    public static class P26 extends ProxyActivity {

    }

    public static class P27 extends ProxyActivity {

    }

    public static class P28 extends ProxyActivity {

    }

    public static class P29 extends ProxyActivity {

    }

    public static class P30 extends ProxyActivity {

    }

    public static class P31 extends ProxyActivity {

    }

    public static class P32 extends ProxyActivity {

    }

    public static class P33 extends ProxyActivity {

    }

    public static class P34 extends ProxyActivity {

    }

    public static class P35 extends ProxyActivity {

    }

    public static class P36 extends ProxyActivity {

    }

    public static class P37 extends ProxyActivity {

    }

    public static class P38 extends ProxyActivity {

    }

    public static class P39 extends ProxyActivity {

    }

    public static class P40 extends ProxyActivity {

    }

    public static class P41 extends ProxyActivity {

    }

    public static class P42 extends ProxyActivity {

    }

    public static class P43 extends ProxyActivity {

    }

    public static class P44 extends ProxyActivity {

    }

    public static class P45 extends ProxyActivity {

    }

    public static class P46 extends ProxyActivity {

    }

    public static class P47 extends ProxyActivity {

    }

    public static class P48 extends ProxyActivity {

    }

    public static class P49 extends ProxyActivity {

    }
}
