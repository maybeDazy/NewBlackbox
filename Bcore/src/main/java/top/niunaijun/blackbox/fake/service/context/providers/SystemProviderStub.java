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
                // Check if we are querying android_id
                for (Object arg : args) {
                    if (arg instanceof String && "android_id".equals(arg)) {
                       if (top.niunaijun.blackbox.app.BActivityThread.getAppConfig() != null &&
                            top.niunaijun.blackbox.app.BActivityThread.getAppConfig().spoofProps != null) {
                            String spoofId = top.niunaijun.blackbox.app.BActivityThread.getAppConfig().spoofProps.get("ANDROID_ID");
                            if (spoofId != null && !spoofId.isEmpty()) {
                                top.niunaijun.blackbox.utils.Slog.d("SystemProviderStub", "Intercepted android_id in SystemProviderStub.call(), returning: " + spoofId);
                                android.os.Bundle bundle = new android.os.Bundle();
                                bundle.putString("value", spoofId);
                                return bundle;
                            }
                        }
                    }
                }

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

        for (Object arg : args) {
            if (arg instanceof String) {
                String value = (String) arg;
                if (callMethod == null && value.startsWith("GET_")) {
                    callMethod = value;
                } else if (requestArg == null) {
                    requestArg = value;
                }
            } else if (arg instanceof Bundle && extras == null) {
                extras = (Bundle) arg;
            }
        }

        if (callMethod == null || requestArg == null) {
            return null;
        }
        if (!("GET_secure".equalsIgnoreCase(callMethod) || callMethod.toUpperCase().startsWith("GET_"))) {
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
        Slog.d(TAG, "Hooked settings call for ANDROID_ID pkg=" + packageName + ", user=" + userId + ", value=" + androidId);
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
