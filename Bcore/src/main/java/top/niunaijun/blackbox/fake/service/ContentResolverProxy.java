package top.niunaijun.blackbox.fake.service;

import android.net.Uri;
import android.os.Bundle;

import java.lang.reflect.Method;

import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.utils.CloneProfileConfig;
import top.niunaijun.blackbox.app.BActivityThread;


public class ContentResolverProxy extends ClassInvocationStub {
    public static final String TAG = "ContentResolverProxy";

    public ContentResolverProxy() {
        super();
    }

    @Override
    protected Object getWho() {
        
        return BlackBoxCore.getContext().getContentResolver();
    }

    @Override
    protected void inject(Object baseInvocation, Object proxyInvocation) {
        
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    
    @ProxyMethod("query")
    public static class Query extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof Uri) {
                Uri uri = (Uri) args[0];
                String uriString = uri.toString();
                
                
                if (uriString.contains("audio") || uriString.contains("media") || 
                    uriString.contains("content://media/external/audio") ||
                    uriString.contains("content://media/internal/audio") ||
                    uriString.contains("content://media/external/file") ||
                    uriString.contains("content://media/internal/file")) {
                    
                    Slog.d(TAG, "ContentResolver: Allowing audio query: " + uriString);
                    
                    
                    return method.invoke(who, args);
                }
            }
            
            
            return method.invoke(who, args);
        }
    }

    
    @ProxyMethod("query")
    public static class QueryWithProjection extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 1 && args[0] instanceof Uri) {
                Uri uri = (Uri) args[0];
                String uriString = uri.toString();
                
                
                if (uriString.contains("audio") || uriString.contains("media") || 
                    uriString.contains("content://media/external/audio") ||
                    uriString.contains("content://media/internal/audio") ||
                    uriString.contains("content://media/external/file") ||
                    uriString.contains("content://media/internal/file")) {
                    
                    Slog.d(TAG, "ContentResolver: Allowing audio query with projection: " + uriString);
                    
                    
                    return method.invoke(who, args);
                }
            }
            
            
            return method.invoke(who, args);
        }
    }

    
    @ProxyMethod("insert")
    public static class Insert extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof Uri) {
                Uri uri = (Uri) args[0];
                String uriString = uri.toString();
                
                if (uriString.contains("audio") || uriString.contains("media")) {
                    Slog.d(TAG, "ContentResolver: insert called for audio URI: " + uriString);
                }
            }
            
            return method.invoke(who, args);
        }
    }

    
    @ProxyMethod("update")
    public static class Update extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof Uri) {
                Uri uri = (Uri) args[0];
                String uriString = uri.toString();
                
                if (uriString.contains("audio") || uriString.contains("media")) {
                    Slog.d(TAG, "ContentResolver: update called for audio URI: " + uriString);
                }
            }
            
            return method.invoke(who, args);
        }
    }

    
    @ProxyMethod("delete")
    public static class Delete extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            if (args != null && args.length > 0 && args[0] instanceof Uri) {
                Uri uri = (Uri) args[0];
                String uriString = uri.toString();
                
                if (uriString.contains("audio") || uriString.contains("media")) {
                    Slog.d(TAG, "ContentResolver: delete called for audio URI: " + uriString);
                }
            }
            
            return method.invoke(who, args);
        }
    }

    @ProxyMethod("call")
    public static class Call extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            Uri uri = null;
            String callMethod = null;
            String callArg = null;
            Bundle extras = null;

            if (args != null) {
                for (Object arg : args) {
                    if (arg instanceof Uri && uri == null) {
                        uri = (Uri) arg;
                    } else if (arg instanceof Bundle && extras == null) {
                        extras = (Bundle) arg;
                    } else if (arg instanceof String) {
                        String value = (String) arg;
                        if (callMethod == null && (value.startsWith("GET_") || value.startsWith("PUT_"))) {
                            callMethod = value;
                        } else if (callArg == null) {
                            callArg = value;
                        }
                    }
                }
            }

            if (isSettingsUri(uri) && isAndroidIdCall(callMethod, callArg)) {
                String packageName = BActivityThread.getAppPackageName();
                if (packageName == null || packageName.trim().isEmpty()) {
                    packageName = extractPackageFromBundle(extras);
                }
                if (packageName == null || packageName.trim().isEmpty()) {
                    packageName = BlackBoxCore.get().getCurrentAppPackage();
                }
                if (packageName == null || packageName.trim().isEmpty()) {
                    packageName = BlackBoxCore.getHostPkg();
                }
                int userId = BActivityThread.getUserId();
                if (userId < 0) {
                    userId = 0;
                }

                Bundle result = new Bundle();
                result.putString("value", CloneProfileConfig.getAndroidId(packageName, userId));
                return result;
            }

            return method.invoke(who, args);
        }


        private String extractPackageFromBundle(Bundle extras) {
            if (extras == null) {
                return null;
            }
            String[] keys = new String[]{"package", "packageName", "calling_package", "caller_package", "android:query-arg-sql-selection"};
            for (String key : keys) {
                String value = extras.getString(key);
                if (value != null && value.contains(".")) {
                    return value;
                }
            }
            return null;
        }

        private boolean isSettingsUri(Uri uri) {
            if (uri == null) {
                return false;
            }
            String value = uri.toString();
            return value != null && value.contains("settings");
        }

        private boolean isAndroidIdCall(String methodName, String requestArg) {
            if (requestArg == null) {
                return false;
            }
            String loweredArg = requestArg.toLowerCase();
            if (!(loweredArg.contains("android_id") || loweredArg.contains("ssaid"))) {
                return false;
            }
            if (methodName == null) {
                return true;
            }
            return methodName.startsWith("GET_");
        }
    }

}
