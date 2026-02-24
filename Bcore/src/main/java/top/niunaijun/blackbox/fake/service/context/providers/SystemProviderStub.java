package top.niunaijun.blackbox.fake.service.context.providers;

import android.os.Bundle;
import android.os.IInterface;
import android.provider.Settings;

import java.lang.reflect.Method;

import black.android.content.BRAttributionSource;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.BActivityThread;
import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.utils.CloneProfileConfig;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.ContextCompat;


public class SystemProviderStub extends ClassInvocationStub implements BContentProvider {
    private static final String TAG = "SystemProviderStub";
    private IInterface mBase;

    @Override
    public IInterface wrapper(IInterface contentProviderProxy, String appPkg) {
        mBase = contentProviderProxy;
        injectHook();
        return (IInterface) getProxyInvocation();
    }

    @Override
    protected Object getWho() {
        return mBase;
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {

    }

    @Override
    protected void onBindMethod() {

    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
        if ("asBinder".equals(method.getName())) {
            return method.invoke(mBase, args);
        }

        String methodName = method.getName();

        if ("call".equals(methodName)) {
            if (args != null) {
                Class<?> attributionSourceClass = BRAttributionSource.getRealClass();
                for (Object arg : args) {
                    if (arg != null && attributionSourceClass != null
                            && arg.getClass().getName().equals(attributionSourceClass.getName())) {
                        ContextCompat.fixAttributionSourceState(arg, BlackBoxCore.getHostUid());
                    }
                }
            }

            Bundle androidIdBundle = tryResolveAndroidIdCall(args);
            if (androidIdBundle != null) {
                return androidIdBundle;
            }
            return method.invoke(mBase, args);
        }

        if (args != null && args.length > 0) {
            Object arg = args[0];
            if (arg instanceof String) {
                String authority = (String) arg;

                if (!isSystemProviderAuthority(authority)) {
                    args[0] = BlackBoxCore.getHostPkg();
                }
            } else if (arg != null) {
                Class<?> attrSourceClass = BRAttributionSource.getRealClass();

                if (attrSourceClass != null && arg.getClass().getName().equals(attrSourceClass.getName())) {
                    ContextCompat.fixAttributionSourceState(arg, BlackBoxCore.getHostUid());
                }
            }
        }
        return method.invoke(mBase, args);
    }

    private Bundle tryResolveAndroidIdCall(Object[] args) {
        if (args == null) {
            return null;
        }

        String callMethod = null;
        String requestArg = null;
        Bundle extras = null;

        String[] stringArgs = new String[args.length];
        int stringCount = 0;
        for (Object arg : args) {
            if (arg instanceof String) {
                String value = (String) arg;
                stringArgs[stringCount++] = value;
                if (callMethod == null && value.toUpperCase().startsWith("GET_")) {
                    callMethod = value;
                }
            } else if (arg instanceof Bundle && extras == null) {
                extras = (Bundle) arg;
            }
        }

        if (callMethod == null) {
            return null;
        }

        for (int i = 0; i < stringCount; i++) {
            if (callMethod.equals(stringArgs[i])) {
                if (i + 1 < stringCount) {
                    requestArg = stringArgs[i + 1];
                }
                break;
            }
        }

        if (requestArg == null) {
            for (int i = 0; i < stringCount; i++) {
                String candidate = stringArgs[i];
                if (candidate == null) {
                    continue;
                }
                String lower = candidate.toLowerCase();
                if (Settings.Secure.ANDROID_ID.equalsIgnoreCase(candidate)
                        || lower.contains("android_id")
                        || lower.contains("ssaid")) {
                    requestArg = candidate;
                    break;
                }
            }
        }

        if (requestArg == null) {
            return null;
        }

        String keyLower = requestArg.toLowerCase();
        if (!(Settings.Secure.ANDROID_ID.equalsIgnoreCase(requestArg)
                || keyLower.contains("android_id")
                || keyLower.contains("ssaid"))) {
            return null;
        }

        String packageName = BActivityThread.getAppPackageName();
        if (isEmptyOrHost(packageName)) {
            packageName = readPackageFromExtras(extras);
        }
        if (isEmptyOrHost(packageName)) {
            packageName = BlackBoxCore.get().getCurrentAppPackage();
        }
        if (isEmptyOrHost(packageName)) {
            packageName = BlackBoxCore.getHostPkg();
        }

        int userId = BActivityThread.getUserId();
        if (userId < 0) {
            userId = 0;
        }

        String androidId = CloneProfileConfig.getAndroidId(packageName, userId);
        Bundle result = new Bundle();
        result.putString("value", androidId);
        Slog.d(TAG, "Hooked settings call for ANDROID_ID method=" + callMethod + ", arg=" + requestArg
                + ", pkg=" + packageName + ", user=" + userId + ", value=" + androidId);
        return result;
    }

    private String readPackageFromExtras(Bundle extras) {
        if (extras == null) {
            return null;
        }
        String[] keys = new String[]{"package", "packageName", "calling_package", "caller_package"};
        for (String key : keys) {
            String value = extras.getString(key);
            if (value != null && value.contains(".")) {
                return value;
            }
        }
        return null;
    }

    private boolean isEmptyOrHost(String value) {
        return value == null || value.trim().isEmpty() || value.equals(BlackBoxCore.getHostPkg());
    }

    private boolean isSystemProviderAuthority(String authority) {
        if (authority == null) return false;

        return authority.equals("settings") ||
                authority.equals("media") ||
                authority.equals("downloads") ||
                authority.equals("contacts") ||
                authority.equals("call_log") ||
                authority.equals("telephony") ||
                authority.equals("calendar") ||
                authority.equals("browser") ||
                authority.equals("user_dictionary") ||
                authority.equals("applications") ||
                authority.startsWith("com.android.") ||
                authority.startsWith("android.");
    }
}
