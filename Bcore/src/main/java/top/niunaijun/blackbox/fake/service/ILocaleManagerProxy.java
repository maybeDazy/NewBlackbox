package top.niunaijun.blackbox.fake.service;

import android.content.Context;
import android.os.IBinder;
import android.os.LocaleList;

import java.lang.reflect.Method;

import black.android.os.BRServiceManager;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.BinderInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Reflector;
import top.niunaijun.blackbox.utils.Slog;

public class ILocaleManagerProxy extends BinderInvocationStub {
    private static final String TAG = "ILocaleManagerProxy";
    private static final String SERVICE = "locale";

    public ILocaleManagerProxy() {
        super(BRServiceManager.get().getService(SERVICE));
    }

    @Override
    protected Object getWho() {
        IBinder binder = BRServiceManager.get().getService(SERVICE);
        if (binder == null) {
            return null;
        }
        try {
            return Reflector.on("android.app.ILocaleManager$Stub").call("asInterface", binder);
        } catch (Throwable e) {
            Slog.w(TAG, "Unable to resolve ILocaleManager binder interface: " + e.getMessage());
            return null;
        }
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        replaceSystemService(SERVICE);
        patchLocaleManagerServiceCache(proxyInvocation);
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    private void patchLocaleManagerServiceCache(Object proxyInvocation) {
        patchContextLocaleManager(BlackBoxCore.getContext(), proxyInvocation);
        patchContextLocaleManager(BActivityThread.getApplication(), proxyInvocation);
    }

    private void patchContextLocaleManager(Context context, Object proxyInvocation) {
        if (context == null || proxyInvocation == null) {
            return;
        }
        try {
            Object localeManager = context.getSystemService(SERVICE);
            if (localeManager != null) {
                Reflector.with(localeManager).field("mService").set(proxyInvocation);
            }
        } catch (Throwable e) {
            Slog.d(TAG, "Unable to patch LocaleManager instance: " + e.getMessage());
        }
    }

    @ProxyMethod("getApplicationLocales")
    public static class GetApplicationLocales extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) {
            // Some apps call LocaleManager#getApplicationLocales on Android 13+.
            // In virtualized process this can throw SecurityException due to
            // READ_APP_SPECIFIC_LOCALES. Return empty locales to avoid hard crash.
            return LocaleList.getEmptyLocaleList();
        }
    }
}
