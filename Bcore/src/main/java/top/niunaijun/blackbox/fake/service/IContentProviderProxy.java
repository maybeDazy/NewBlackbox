package top.niunaijun.blackbox.fake.service;

import java.lang.reflect.Method;

import top.niunaijun.blackbox.fake.hook.ClassInvocationStub;
import top.niunaijun.blackbox.fake.hook.MethodHook;
import top.niunaijun.blackbox.fake.hook.ProxyMethod;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.AttributionSourceUtils;


public class IContentProviderProxy extends ClassInvocationStub {
    public static final String TAG = "IContentProviderProxy";

    public IContentProviderProxy() {
        super();
    }

    @Override
    protected Object getWho() {
        
        return null;
    }

    @Override
    protected void inject(Object base, Object proxy) {
        
        Slog.d(TAG, "IContentProvider proxy initialized for UID mismatch prevention");
    }

    @Override
    public boolean isBadEnv() {
        return false;
    }

    
    @ProxyMethod("query")
    public static class Query extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                
                AttributionSourceUtils.fixAttributionSourceInArgs(args);
                
                
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "Error in query hook: " + e.getMessage());
                
                return null;
            }
        }
    }

    
    @ProxyMethod("insert")
    public static class Insert extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                
                AttributionSourceUtils.fixAttributionSourceInArgs(args);
                
                
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "Error in insert hook: " + e.getMessage());
                
                return null;
            }
        }
    }

    
    @ProxyMethod("update")
    public static class Update extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                
                AttributionSourceUtils.fixAttributionSourceInArgs(args);
                
                
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "Error in update hook: " + e.getMessage());
                
                return 0;
            }
        }
    }

    
    @ProxyMethod("delete")
    public static class Delete extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                
                AttributionSourceUtils.fixAttributionSourceInArgs(args);
                
                
                return method.invoke(who, args);
            } catch (Exception e) {
                Slog.w(TAG, "Error in delete hook: " + e.getMessage());
                
                return 0;
            }
        }
    }

    
    @ProxyMethod("call")
    public static class Call extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                // Check if we are querying android_id
                if (args != null) {
                    for (Object arg : args) {
                        if (arg instanceof String && "android_id".equals(arg)) {
                            top.niunaijun.blackbox.utils.Slog.d(TAG, "Intercepted android_id in call()");
                            if (top.niunaijun.blackbox.app.BActivityThread.getAppConfig() != null &&
                                    top.niunaijun.blackbox.app.BActivityThread.getAppConfig().spoofProps != null) {
                                String spoofId = top.niunaijun.blackbox.app.BActivityThread.getAppConfig().spoofProps.get("ANDROID_ID");
                                if (spoofId != null && !spoofId.isEmpty()) {
                                    top.niunaijun.blackbox.utils.Slog.d(TAG, "Returning spoofed android_id from call(): " + spoofId);
                                    // call returns a Bundle
                                    android.os.Bundle bundle = new android.os.Bundle();
                                    bundle.putString("value", spoofId);
                                    return bundle;
                                }
                            }
                        }
                    }
                }

                AttributionSourceUtils.fixAttributionSourceInArgs(args);
                
                
                return method.invoke(who, args);
            } catch (SecurityException e) {
                
                String message = e.getMessage();
                if (message != null && message.contains("Calling uid") && message.contains("doesn't match source uid")) {
                    Slog.w(TAG, "UID mismatch in call method, returning safe default: " + message);
                    return null; 
                }
                throw e;
            } catch (Exception e) {
                Slog.w(TAG, "Error in call hook: " + e.getMessage());
                
                return null;
            }
        }
    }

    
    @ProxyMethod("getString")
    public static class GetString extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                
                AttributionSourceUtils.fixAttributionSourceInArgs(args);
                
                
                return method.invoke(who, args);
            } catch (SecurityException e) {
                
                String message = e.getMessage();
                if (message != null && message.contains("Calling uid") && message.contains("doesn't match source uid")) {
                    Slog.w(TAG, "UID mismatch in getString, returning safe default: " + message);
                    return ""; 
                }
                throw e;
            } catch (Exception e) {
                Slog.w(TAG, "Error in getString hook: " + e.getMessage());
                
                return "";
            }
        }
    }

    
    @ProxyMethod("getStringForUser")
    public static class GetStringForUser extends MethodHook {
        @Override
        protected Object hook(Object who, Method method, Object[] args) throws Throwable {
            try {
                
                AttributionSourceUtils.fixAttributionSourceInArgs(args);
                
                
                return method.invoke(who, args);
            } catch (SecurityException e) {
                
                String message = e.getMessage();
                if (message != null && message.contains("Calling uid") && message.contains("doesn't match source uid")) {
                    Slog.w(TAG, "UID mismatch in getStringForUser, returning safe default: " + message);
                    return ""; 
                }
                throw e;
            } catch (Exception e) {
                Slog.w(TAG, "Error in getStringForUser hook: " + e.getMessage());
                
                return "";
            }
        }
    }
}
