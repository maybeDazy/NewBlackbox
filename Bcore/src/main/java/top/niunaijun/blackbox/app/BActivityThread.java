package top.niunaijun.blackbox.app;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.app.Service;
import android.app.job.JobService;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.ContentProviderClient;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Build;
import android.os.ConditionVariable;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.RemoteException;
import android.os.StrictMode;
import android.text.TextUtils;
import android.util.Log;
import android.webkit.WebView;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.Security;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import black.android.app.ActivityThreadAppBindDataContext;
import black.android.app.BRActivity;
import black.android.app.BRActivityManagerNative;
import black.android.app.BRActivityThread;
import black.android.app.BRActivityThreadActivityClientRecord;
import black.android.app.BRActivityThreadAppBindData;
import black.android.app.BRActivityThreadNMR1;
import black.android.app.BRActivityThreadQ;
import black.android.app.BRContextImpl;
import black.android.app.BRLoadedApk;
import black.android.app.BRService;
import black.android.app.LoadedApk;
import black.android.content.BRBroadcastReceiver;
import black.android.content.BRContentProviderClient;
import black.android.graphics.BRCompatibility;
import black.android.security.net.config.BRNetworkSecurityConfigProvider;
import black.com.android.internal.content.BRReferrerIntent;
import black.dalvik.system.BRVMRuntime;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.app.configuration.AppLifecycleCallback;
import top.niunaijun.blackbox.app.dispatcher.AppServiceDispatcher;
import top.niunaijun.blackbox.core.CrashHandler;
import top.niunaijun.blackbox.core.IBActivityThread;
import top.niunaijun.blackbox.core.IOCore;
import top.niunaijun.blackbox.core.NativeCore;
import top.niunaijun.blackbox.core.env.VirtualRuntime;
import top.niunaijun.blackbox.core.system.user.BUserHandle;
import top.niunaijun.blackbox.entity.AppConfig;
import top.niunaijun.blackbox.entity.am.ReceiverData;

import top.niunaijun.blackbox.fake.delegate.AppInstrumentation;
import top.niunaijun.blackbox.fake.delegate.ContentProviderDelegate;

import top.niunaijun.blackbox.fake.hook.HookManager;
import top.niunaijun.blackbox.fake.service.HCallbackProxy;
import top.niunaijun.blackbox.utils.Reflector;
import top.niunaijun.blackbox.utils.SafeContextWrapper;
import top.niunaijun.blackbox.utils.GlobalContextWrapper;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.ActivityManagerCompat;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.BuildCompat;
import top.niunaijun.blackbox.utils.compat.ContextCompat;
import top.niunaijun.blackbox.utils.compat.StrictModeCompat;
import top.niunaijun.blackbox.core.system.JarManager;


public class BActivityThread extends IBActivityThread.Stub {
    public static final String TAG = "BActivityThread";

    private static BActivityThread sBActivityThread;
    private AppBindData mBoundApplication;
    private Application mInitialApplication;
    private AppConfig mAppConfig;
    private final List<ProviderInfo> mProviders = new ArrayList<>();
    private final Handler mH = BlackBoxCore.get().getHandler();
    private static final Object mConfigLock = new Object();

    public static boolean isThreadInit() {
        return sBActivityThread != null;
    }

    public static BActivityThread currentActivityThread() {
        if (sBActivityThread == null) {
            synchronized (BActivityThread.class) {
                if (sBActivityThread == null) {
                    sBActivityThread = new BActivityThread();
                }
            }
        }
        return sBActivityThread;
    }

    public static AppConfig getAppConfig() {
        synchronized (mConfigLock) {
            return currentActivityThread().mAppConfig;
        }
    }

    public static List<ProviderInfo> getProviders() {
        return currentActivityThread().mProviders;
    }

    public static String getAppProcessName() {
        if (getAppConfig() != null) {
            return getAppConfig().processName;
        } else if (currentActivityThread().mBoundApplication != null) {
            return currentActivityThread().mBoundApplication.processName;
        } else {
            return null;
        }
    }

    public static String getAppPackageName() {
        if (getAppConfig() != null) {
            return getAppConfig().packageName;
        } else if (currentActivityThread().mInitialApplication != null) {
            return currentActivityThread().mInitialApplication.getPackageName();
        } else {
            return null;
        }
    }

    public static Application getApplication() {
        return currentActivityThread().mInitialApplication;
    }

    public static int getAppPid() {
        return getAppConfig() == null ? -1 : getAppConfig().bpid;
    }

    public static int getBUid() {
        return getAppConfig() == null ? BUserHandle.AID_APP_START : getAppConfig().buid;
    }

    public static int getBAppId() {
        return BUserHandle.getAppId(getBUid());
    }

    public static int getCallingBUid() {
        return getAppConfig() == null ? BlackBoxCore.getHostUid() : getAppConfig().callingBUid;
    }

    public static int getUid() {
        return getAppConfig() == null ? -1 : getAppConfig().uid;
    }

    public static int getUserId() {
        return getAppConfig() == null ? 0 : getAppConfig().userId;
    }

    public void initProcess(AppConfig appConfig) {
        synchronized (mConfigLock) {
            if (this.mAppConfig != null && !this.mAppConfig.packageName.equals(appConfig.packageName)) {
                
                throw new RuntimeException("reject init process: " + appConfig.processName + ", this process is : " + this.mAppConfig.processName);
            }
            this.mAppConfig = appConfig;
            handleDeviceSpoofing(appConfig);
            IBinder iBinder = asBinder();
            try {
                iBinder.linkToDeath(new DeathRecipient() {
                    @Override
                    public void binderDied() {
                        synchronized (mConfigLock) {
                            try {
                                iBinder.linkToDeath(this, 0);
                            } catch (RemoteException ignored) {
                            }
                            mAppConfig = null;
                        }
                    }
                }, 0);
            } catch (RemoteException e) {
                e.printStackTrace();
            }
        }
    }

    public boolean isInit() {
        return mBoundApplication != null;
    }

    public Service createService(ServiceInfo serviceInfo, IBinder token) {
        if (!BActivityThread.currentActivityThread().isInit()) {
            BActivityThread.currentActivityThread().bindApplication(serviceInfo.packageName, serviceInfo.processName);
        }
        ClassLoader classLoader = BRLoadedApk.get(mBoundApplication.info).getClassLoader();
        Service service;
        try {
            service = (Service) classLoader.loadClass(serviceInfo.name).newInstance();
        } catch (ClassNotFoundException e) {
            
            if (serviceInfo.name.contains("google.android.gms") || 
                serviceInfo.name.contains("google.android.location")) {
                Slog.w(TAG, "Google Play Services class not found, skipping: " + serviceInfo.name);
                return null;
            }
            e.printStackTrace();
            Slog.e(TAG, "Unable to instantiate service " + serviceInfo.name
                    + ": " + e.toString());
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            Slog.e(TAG, "Unable to instantiate service " + serviceInfo.name
                    + ": " + e.toString());
            return null;
        }

        try {
            Context context = BlackBoxCore.getContext().createPackageContext(
                    serviceInfo.packageName,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY
            );
            BRContextImpl.get(context).setOuterContext(service);
            BRService.get(service).attach(
                    context,
                    BlackBoxCore.mainThread(),
                    serviceInfo.name,
                    token,
                    mInitialApplication,
                    BRActivityManagerNative.get().getDefault()
            );
            ContextCompat.fix(context);
            service.onCreate();
            return service;
        } catch (Exception e) {
            
            if (serviceInfo.name.contains("google.android.gms") || 
                serviceInfo.name.contains("google.android.location")) {
                Slog.w(TAG, "Google Play Services service creation failed, skipping: " + serviceInfo.name);
                return null;
            }
            Slog.w(TAG, "Service creation failed, but continuing: " + serviceInfo.name + " - " + e.getMessage());
            return null;
        }
    }

    public JobService createJobService(ServiceInfo serviceInfo) {
        if (!BActivityThread.currentActivityThread().isInit()) {
            BActivityThread.currentActivityThread().bindApplication(serviceInfo.packageName, serviceInfo.processName);
        }
        ClassLoader classLoader = BRLoadedApk.get(mBoundApplication.info).getClassLoader();
        JobService service;
        try {
            service = (JobService) classLoader.loadClass(serviceInfo.name).newInstance();
        } catch (ClassNotFoundException e) {
            
            if (serviceInfo.name.contains("google.android.gms") || 
                serviceInfo.name.contains("google.android.location")) {
                Slog.w(TAG, "Google Play Services JobService class not found, skipping: " + serviceInfo.name);
                return null;
            }
            e.printStackTrace();
            Slog.e(TAG, "Unable to create JobService " + serviceInfo.name
                    + ": " + e.toString());
            return null;
        } catch (Exception e) {
            e.printStackTrace();
            Slog.e(TAG, "Unable to create JobService " + serviceInfo.name
                    + ": " + e.toString());
            return null;
        }

        try {
            Context context = BlackBoxCore.getContext().createPackageContext(
                    serviceInfo.packageName,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY
            );
            BRContextImpl.get(context).setOuterContext(service);
            BRService.get(service).attach(
                    context,
                    BlackBoxCore.mainThread(),
                    serviceInfo.name,
                    BActivityThread.currentActivityThread().getActivityThread(),
                    mInitialApplication,
                    BRActivityManagerNative.get().getDefault()
            );
            ContextCompat.fix(context);
            service.onCreate();
            service.onBind(null);
            return service;
        } catch (Exception e) {
            
            if (serviceInfo.name.contains("google.android.gms") || 
                serviceInfo.name.contains("google.android.location")) {
                Slog.w(TAG, "Google Play Services JobService creation failed, skipping: " + serviceInfo.name);
                return null;
            }
            Slog.w(TAG, "JobService creation failed, but continuing: " + serviceInfo.name + " - " + e.getMessage());
            return null;
        }
    }

    public void bindApplication(final String packageName, final String processName) {
        if (Looper.myLooper() != Looper.getMainLooper()) {
            final ConditionVariable conditionVariable = new ConditionVariable();
            BlackBoxCore.get().getHandler().post(() -> {
                
                Object bindData = createBindApplicationData(packageName, processName);
                handleBindApplication(packageName, processName);
                conditionVariable.open();
            });
            conditionVariable.block();
        } else {
            
            Object bindData = createBindApplicationData(packageName, processName);
            handleBindApplication(packageName, processName);
        }
    }
    
    
    private Object createBindApplicationData(String packageName, String processName) {
        try {
            
            PackageInfo packageInfo = BlackBoxCore.getBPackageManager().getPackageInfo(packageName, PackageManager.GET_PROVIDERS, getUserId());
            ApplicationInfo applicationInfo = packageInfo.applicationInfo;
            
            
            
            return new Object() {
                public ApplicationInfo getInfo() { return applicationInfo; }
                public List<ProviderInfo> getProviders() { 
                    return packageInfo.providers != null ? Arrays.asList(packageInfo.providers) : new ArrayList<>();
                }
            };
        } catch (Exception e) {
            Slog.e(TAG, "Error creating bind application data", e);
            
            return new Object() {
                public ApplicationInfo getInfo() { return null; }
                public List<ProviderInfo> getProviders() { return new ArrayList<>(); }
            };
        }
    }

    public synchronized void handleBindApplication(String packageName, String processName) {
        if (isInit())
            return;
            
        Log.d(TAG, "handleBindApplication: " + packageName + " / " + processName);

        // Pre-application locale injection (sets JVM-level defaults before any app code runs)
        if (isCoupangPackage(packageName)) {
            applyKoreanLocaleDefaults();
        }

        try {
            CrashHandler.create();
        } catch (Throwable ignored) {
        }

        PackageInfo packageInfo = BlackBoxCore.getBPackageManager().getPackageInfo(packageName, PackageManager.GET_PROVIDERS, BActivityThread.getUserId());
        ApplicationInfo applicationInfo = packageInfo.applicationInfo;
        if (packageInfo.providers == null) {
            packageInfo.providers = new ProviderInfo[]{};
        }
        mProviders.addAll(Arrays.asList(packageInfo.providers));

        Object boundApplication = BRActivityThread.get(BlackBoxCore.mainThread()).mBoundApplication();

        Context packageContext = createPackageContext(applicationInfo);
        Object loadedApk = BRContextImpl.get(packageContext).mPackageInfo();
        BRLoadedApk.get(loadedApk)._set_mSecurityViolation(false);
        
        BRLoadedApk.get(loadedApk)._set_mApplicationInfo(applicationInfo);

        int targetSdkVersion = applicationInfo.targetSdkVersion;
        if (targetSdkVersion < Build.VERSION_CODES.GINGERBREAD) {
            StrictMode.ThreadPolicy newPolicy = new StrictMode.ThreadPolicy.Builder(StrictMode.getThreadPolicy()).permitNetwork().build();
            StrictMode.setThreadPolicy(newPolicy);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            if (targetSdkVersion < Build.VERSION_CODES.N) {
                StrictModeCompat.disableDeathOnFileUriExposure();
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            WebView.setDataDirectorySuffix(getUserId() + ":" + packageName + ":" + processName);
        }

        VirtualRuntime.setupRuntime(processName, applicationInfo);

        BRVMRuntime.get(BRVMRuntime.get().getRuntime()).setTargetSdkVersion(applicationInfo.targetSdkVersion);
        if (BuildCompat.isS()) {
            BRCompatibility.get().setTargetSdkVersion(applicationInfo.targetSdkVersion);
        }

        NativeCore.init(Build.VERSION.SDK_INT);
        assert packageContext != null;
        IOCore.get().enableRedirect(packageContext);

        AppBindData bindData = new AppBindData();
        bindData.appInfo = applicationInfo;
        bindData.processName = processName;
        bindData.info = loadedApk;
        bindData.providers = mProviders;

        ActivityThreadAppBindDataContext activityThreadAppBindData = BRActivityThreadAppBindData.get(boundApplication);
        activityThreadAppBindData._set_instrumentationName(new ComponentName(bindData.appInfo.packageName, Instrumentation.class.getName()));
        activityThreadAppBindData._set_appInfo(bindData.appInfo);
        activityThreadAppBindData._set_info(bindData.info);
        activityThreadAppBindData._set_processName(bindData.processName);
        activityThreadAppBindData._set_providers(bindData.providers);

        mBoundApplication = bindData;

        
        if (BRNetworkSecurityConfigProvider.getRealClass() != null) {
            Security.removeProvider("AndroidNSSP");
            BRNetworkSecurityConfigProvider.get().install(packageContext);
        }
        Application application;
        try {
            onBeforeCreateApplication(packageName, processName, packageContext);
            
            
            try {
                application = BRLoadedApk.get(loadedApk).makeApplication(false, null);
            } catch (Exception makeAppException) {
                Slog.e(TAG, "Failed to makeApplication, trying fallback approach", makeAppException);
                application = null;
            }
            
            
            if (application == null) {
                Slog.w(TAG, "makeApplication returned null, attempting fallback creation");
                
                
                try {
                    application = BRLoadedApk.get(loadedApk).makeApplication(true, null);
                } catch (Exception e) {
                    Slog.e(TAG, "Fallback makeApplication also failed", e);
                }
                
                
                if (application == null) {
                    Slog.w(TAG, "Creating minimal application context as fallback");
                    try {
                        
                        application = (Application) packageContext;
                        if (application == null) {
                            Slog.e(TAG, "Even package context is null, this is critical");
                            throw new RuntimeException("Unable to create application context");
                        }
                    } catch (Exception contextException) {
                        Slog.e(TAG, "Failed to create fallback application context", contextException);
                        throw new RuntimeException("Unable to makeApplication - all fallback attempts failed", contextException);
                    }
                }
            }
            
            if (application == null) {
                Slog.e(TAG, "makeApplication application Error! All attempts failed");
                throw new RuntimeException("Unable to create application - all creation methods failed");
            }
            
            mInitialApplication = application;
            BRActivityThread.get(BlackBoxCore.mainThread())._set_mInitialApplication(mInitialApplication);
            ContextCompat.fix((Context) BRActivityThread.get(BlackBoxCore.mainThread()).getSystemContext());
            ContextCompat.fix(mInitialApplication);
            installProviders(mInitialApplication, bindData.processName, bindData.providers);

            // Post-makeApplication hooks: now the guest ClassLoader is available
            if (isCoupangPackage(packageName)) {
                // applyLocaleToAppResources(application);
                // injectCoupangPreferences(application);
                // blockProcessKill();
                hookDeviceProtectedStorage(application, packageName);
            }

            onBeforeApplicationOnCreate(packageName, processName, application);
            AppInstrumentation.get().callApplicationOnCreate(application);
            onAfterApplicationOnCreate(packageName, processName, application);

            HookManager.get().checkEnv(HCallbackProxy.class);
        } catch (Exception e) {
            Slog.e(TAG, "Critical error in handleBindApplication", e);
            throw new RuntimeException("Unable to makeApplication", e);
        }
    }
    
    
    private void initializeJarEnvironment() {
        try {
            Slog.d(TAG, "Initializing JAR environment for DEX loading");
            
            
            JarManager jarManager = JarManager.getInstance();
            if (!jarManager.isReady()) {
                Slog.d(TAG, "JarManager not ready, initializing synchronously");
                jarManager.initializeSync();
            }
            
            
            File emptyJar = jarManager.getEmptyJar();
            if (emptyJar == null || !emptyJar.exists()) {
                Slog.w(TAG, "Empty JAR not available, attempting to recreate");
                jarManager.clearCache();
                jarManager.initializeSync();
                emptyJar = jarManager.getEmptyJar();
            }
            
            if (emptyJar != null && emptyJar.exists()) {
                Slog.d(TAG, "Empty JAR verified: " + emptyJar.getAbsolutePath());
            } else {
                Slog.w(TAG, "Empty JAR still not available after retry");
            }
            
        } catch (Exception e) {
            Slog.e(TAG, "Error initializing JAR environment", e);
        }
    }
    
    
    private Application createApplicationWithFallback(android.content.pm.ApplicationInfo appInfo) {
        try {
            Application application = createApplication(appInfo);
            if (application != null) {
                Slog.d(TAG, "Application created successfully: " + appInfo.className);
                return application;
            }
        } catch (Exception e) {
            Slog.w(TAG, "Failed to create application normally: " + e.getMessage());
        }

        Slog.d(TAG, "Using minimal application fallback for " + appInfo.packageName);
        return createMinimalApplication(createPackageContext(appInfo), appInfo.packageName);
    }
    
    
    private void installContentProvidersWithFallback(Application application, Object data) {
        try {
            List<android.content.pm.ProviderInfo> providers = getProviderInfoList(data);
            if (providers == null || providers.isEmpty()) {
                Slog.d(TAG, "No content providers to install");
                return;
            }
            
            Slog.d(TAG, "Installing " + providers.size() + " content providers");
            
            for (android.content.pm.ProviderInfo providerInfo : providers) {
                try {
                    installContentProvider(application, providerInfo);
                    Slog.d(TAG, "Successfully installed provider: " + providerInfo.name);
                } catch (Exception e) {
                    Slog.w(TAG, "Failed to install provider " + providerInfo.name + ": " + e.getMessage());
                    
                }
            }
            
        } catch (Exception e) {
            Slog.e(TAG, "Error installing content providers", e);
        }
    }
    
    
    private android.content.pm.ApplicationInfo getApplicationInfo(Object data) {
        try {
            
            if (data != null) {
                try {
                    
                    Method getInfoMethod = data.getClass().getMethod("getInfo");
                    ApplicationInfo appInfo = (ApplicationInfo) getInfoMethod.invoke(data);
                    if (appInfo != null) {
                        return appInfo;
                    }
                } catch (Exception e) {
                    Slog.w(TAG, "Error getting info from custom data object: " + e.getMessage());
                }
            }
            
            
            String packageName = BlackBoxCore.getAppPackageName();
            if (packageName != null) {
                PackageInfo packageInfo = BlackBoxCore.getBPackageManager().getPackageInfo(packageName, 0, getUserId());
                return packageInfo.applicationInfo;
            }
            
            return null;
        } catch (Exception e) {
            Slog.e(TAG, "Error getting application info", e);
            return null;
        }
    }
    
    
    private ClassLoader getClassLoader(android.content.pm.ApplicationInfo appInfo) {
        try {
            
            String sourceDir = appInfo.sourceDir;
            if (sourceDir != null) {
                return new dalvik.system.PathClassLoader(sourceDir, ClassLoader.getSystemClassLoader());
            }
            
            
            return ClassLoader.getSystemClassLoader();
        } catch (Exception e) {
            Slog.w(TAG, "Error getting class loader: " + e.getMessage());
            return ClassLoader.getSystemClassLoader();
        }
    }

    
    private Application createApplication(android.content.pm.ApplicationInfo appInfo) {
        try {
            
            ClassLoader classLoader = getClassLoader(appInfo);
            Class<?> appClass = classLoader.loadClass(appInfo.className);
            Application application = (Application) appClass.newInstance();
            
            
            ensureApplicationBaseContext(application, appInfo);
            
            return application;
        } catch (Exception e) {
            Slog.e(TAG, "Error creating application: " + e.getMessage());
            return null;
        }
    }
    
    
    private void ensureApplicationBaseContext(Application application, android.content.pm.ApplicationInfo appInfo) {
        try {
            
            if (application.getBaseContext() != null) {
                Slog.d(TAG, "Application already has base context: " + appInfo.className);
                return;
            }
            
            
            Context packageContext = createPackageContext(appInfo);
            if (packageContext == null) {
                Slog.w(TAG, "Could not create package context for application: " + appInfo.className + ", using fallback");
                
                packageContext = createFallbackContext(appInfo.packageName);
            }
            
            
            if (packageContext == null) {
                Slog.e(TAG, "Failed to create any context for application: " + appInfo.className);
                return;
            }
            
            
            try {
                Method attachBaseContext = Application.class.getDeclaredMethod("attachBaseContext", Context.class);
                attachBaseContext.setAccessible(true);
                attachBaseContext.invoke(application, packageContext);
                Slog.d(TAG, "Successfully attached base context to application: " + appInfo.className);
            } catch (Exception e) {
                Slog.w(TAG, "Could not attach base context to application: " + e.getMessage());
            }
            
        } catch (Exception e) {
            Slog.e(TAG, "Error ensuring application base context: " + e.getMessage());
        }
    }
    
    
    private Context createFallbackContext(String packageName) {
        try {
            Context baseContext = BlackBoxCore.getContext();
            if (baseContext == null) {
                Slog.e(TAG, "BlackBoxCore.getContext() is null, cannot create fallback context");
                return null;
            }
            
            
            return new ContextWrapper(baseContext) {
                @Override
                public String getPackageName() {
                    return packageName;
                }
                
                @Override
                public android.content.pm.PackageManager getPackageManager() {
                    try {
                        return baseContext.getPackageManager();
                    } catch (Exception e) {
                        Slog.w(TAG, "Error getting package manager from base context: " + e.getMessage());
                        return null;
                    }
                }
                
                @Override
                public android.content.res.Resources getResources() {
                    try {
                        return baseContext.getResources();
                    } catch (Exception e) {
                        Slog.w(TAG, "Error getting resources from base context: " + e.getMessage());
                        try {
                            return android.content.res.Resources.getSystem();
                        } catch (Exception e2) {
                            Slog.e(TAG, "Error getting system resources: " + e2.getMessage());
                            return null;
                        }
                    }
                }
                
                @Override
                public ClassLoader getClassLoader() {
                    try {
                        return baseContext.getClassLoader();
                    } catch (Exception e) {
                        Slog.w(TAG, "Error getting class loader from base context: " + e.getMessage());
                        try {
                            return ClassLoader.getSystemClassLoader();
                        } catch (Exception e2) {
                            Slog.e(TAG, "Error getting system class loader: " + e2.getMessage());
                            return null;
                        }
                    }
                }
                
                @Override
                public Context getApplicationContext() {
                    try {
                        return baseContext.getApplicationContext();
                    } catch (Exception e) {
                        Slog.w(TAG, "Error getting application context from base context: " + e.getMessage());
                        return this;
                    }
                }
            };
        } catch (Exception e) {
            Slog.e(TAG, "Failed to create fallback context for " + packageName + ": " + e.getMessage());
            return null;
        }
    }
    
    
    private List<android.content.pm.ProviderInfo> getProviderInfoList(Object data) {
        try {
            
            if (data != null) {
                try {
                    
                    Method getProvidersMethod = data.getClass().getMethod("getProviders");
                    List<ProviderInfo> providers = (List<ProviderInfo>) getProvidersMethod.invoke(data);
                    if (providers != null) {
                        return providers;
                    }
                } catch (Exception e) {
                    Slog.w(TAG, "Error getting providers from custom data object: " + e.getMessage());
                }
            }
            
            
            return new ArrayList<>();
        } catch (Exception e) {
            Slog.e(TAG, "Error getting provider info list", e);
            return new ArrayList<>();
        }
    }
    
    
    private void installContentProvider(Application application, android.content.pm.ProviderInfo providerInfo) {
        try {
            
            if (application == null) {
                Slog.w(TAG, "Application is null, cannot install content provider: " + providerInfo.name);
                return;
            }
            
            
            ClassLoader classLoader = application.getClassLoader();
            if (classLoader == null) {
                Slog.w(TAG, "Application class loader is null, using system class loader for: " + providerInfo.name);
                classLoader = ClassLoader.getSystemClassLoader();
            }
            
            
            android.content.ContentProvider provider = (android.content.ContentProvider) classLoader
                .loadClass(providerInfo.name).newInstance();
            
            
            provider.attachInfo(application, providerInfo);
            
            
            
            Slog.d(TAG, "Content provider installed: " + providerInfo.name);
            
        } catch (Exception e) {
            Slog.e(TAG, "Error installing content provider " + providerInfo.name, e);
        }
    }
    
    
    private void setApplication(Application application) {
        try {
            mInitialApplication = application;
            BRActivityThread.get(BlackBoxCore.mainThread())._set_mInitialApplication(application);
            Slog.d(TAG, "Application set in ActivityThread successfully");
        } catch (Exception e) {
            Slog.e(TAG, "Error setting application in ActivityThread", e);
        }
    }

    private void handleSecurityException(SecurityException se, String packageName, String processName, Context packageContext) {
        Slog.w(TAG, "Handling SecurityException for " + packageName);
        
        
        try {
                            Application basicApp = createMinimalApplication(packageContext, packageName);
            if (basicApp != null) {
                mInitialApplication = basicApp;
                BRActivityThread.get(BlackBoxCore.mainThread())._set_mInitialApplication(mInitialApplication);
                ContextCompat.fix(mInitialApplication);
                
                
                Slog.w(TAG, "Created basic application, skipping problematic operations");
                return;
            }
        } catch (Exception e) {
            Slog.e(TAG, "Failed to create basic application after SecurityException: " + e.getMessage());
        }
        
        
        throw new RuntimeException("Unable to handle SecurityException", se);
    }

    private void installProvidersWithErrorHandling(Context context, String processName, List<ProviderInfo> providers) {
        long origId = Binder.clearCallingIdentity();
        try {
            for (ProviderInfo providerInfo : providers) {
                try {
                    if (processName.equals(providerInfo.processName) ||
                            providerInfo.processName.equals(context.getPackageName()) || providerInfo.multiprocess) {
                        installProvider(BlackBoxCore.mainThread(), context, providerInfo, null);
                    }
                } catch (SecurityException se) {
                    Slog.w(TAG, "SecurityException installing provider " + providerInfo.name + ": " + se.getMessage());
                    
                } catch (Throwable t) {
                    Slog.w(TAG, "Error installing provider " + providerInfo.name + ": " + t.getMessage());
                    
                }
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
            try {
                ContentProviderDelegate.init();
            } catch (Exception e) {
                Slog.w(TAG, "Error initializing ContentProviderDelegate: " + e.getMessage());
            }
        }
    }

    public static Context createPackageContext(ApplicationInfo info) {
        try {
            return BlackBoxCore.getContext().createPackageContext(info.packageName,
                    Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    
    private static Context createMinimalPackageContext(ApplicationInfo info) {
        try {
            
            Context baseContext = BlackBoxCore.getContext();
            
            
            try {
                Context packageContext = baseContext.createPackageContext(info.packageName, 0);
                if (packageContext != null) {
                    Slog.d(TAG, "Successfully created package context with minimal flags for " + info.packageName);
                    return packageContext;
                }
            } catch (Exception e) {
                Slog.w(TAG, "Failed to create package context with minimal flags for " + info.packageName + ": " + e.getMessage());
            }
            
            
            try {
                Context packageContext = baseContext.createPackageContext(info.packageName, Context.CONTEXT_IGNORE_SECURITY);
                if (packageContext != null) {
                    Slog.d(TAG, "Successfully created package context with ignore security for " + info.packageName);
                    return packageContext;
                }
            } catch (Exception e) {
                Slog.w(TAG, "Failed to create package context with ignore security for " + info.packageName + ": " + e.getMessage());
            }
            
            
            try {
                Context packageContext = baseContext.createPackageContext(info.packageName, Context.CONTEXT_INCLUDE_CODE);
                if (packageContext != null) {
                    Slog.d(TAG, "Successfully created package context with include code for " + info.packageName);
                    return packageContext;
                }
            } catch (Exception e) {
                Slog.w(TAG, "Failed to create package context with include code for " + info.packageName + ": " + e.getMessage());
            }
            
        } catch (Exception e) {
            Slog.e(TAG, "Failed to create minimal package context for " + info.packageName + ": " + e.getMessage());
        }
        
        
        Slog.w(TAG, "Using base context as fallback for " + info.packageName);
        return createWrappedBaseContext(info.packageName);
    }

    
    private static Context createWrappedBaseContext(String packageName) {
        try {
            Context baseContext = BlackBoxCore.getContext();
            
            
            return new ContextWrapper(baseContext) {
                @Override
                public String getPackageName() {
                    return packageName;
                }
                
                @Override
                public PackageManager getPackageManager() {
                    return baseContext.getPackageManager();
                }
                
                @Override
                public Resources getResources() {
                    return baseContext.getResources();
                }
                
                @Override
                public ClassLoader getClassLoader() {
                    return baseContext.getClassLoader();
                }
                
                @Override
                public Context getApplicationContext() {
                    return baseContext.getApplicationContext();
                }
            };
        } catch (Exception e) {
            Slog.e(TAG, "Failed to create wrapped base context for " + packageName + ": " + e.getMessage());
            
            return BlackBoxCore.getContext();
        }
    }

    private void installProviders(Context context, String processName, List<ProviderInfo> provider) {
        long origId = Binder.clearCallingIdentity();
        try {
            for (ProviderInfo providerInfo : provider) {
                try {
                    if (processName.equals(providerInfo.processName) ||
                            providerInfo.processName.equals(context.getPackageName()) || providerInfo.multiprocess) {
                        installProvider(BlackBoxCore.mainThread(), context, providerInfo, null);
                    }
                } catch (Throwable ignored) {
                }
            }
        } finally {
            Binder.restoreCallingIdentity(origId);
            ContentProviderDelegate.init();
        }
    }

    public Object getPackageInfo() {
        return mBoundApplication.info;
    }

    public static void installProvider(Object mainThread, Context context, ProviderInfo providerInfo, Object holder) throws Throwable {
        Method installProvider = Reflector.findMethodByFirstName(mainThread.getClass(), "installProvider");
        if (installProvider != null) {
            installProvider.setAccessible(true);
            installProvider.invoke(mainThread, context, holder, providerInfo, false, true, true);
        }
    }



    @Override
    public IBinder getActivityThread() {
        return BRActivityThread.get(BlackBoxCore.mainThread()).getApplicationThread();
    }

    @Override
    public void bindApplication() {
        if (!isInit()) {
            bindApplication(getAppPackageName(), getAppProcessName());
        }
    }

    @Override
    public void stopService(Intent intent) {
        AppServiceDispatcher.get().stopService(intent);
    }

    @Override
    public void restartJobService(String selfId) throws RemoteException {

    }

    @Override
    public IBinder acquireContentProviderClient(ProviderInfo providerInfo) throws RemoteException {
        if (!isInit()) {
            bindApplication(BActivityThread.getAppConfig().packageName, BActivityThread.getAppConfig().processName);
        }
        String[] split = providerInfo.authority.split(";");
        for (String auth : split) {
            ContentProviderClient contentProviderClient = BlackBoxCore.getContext()
                    .getContentResolver().acquireContentProviderClient(auth);
            IInterface iInterface = BRContentProviderClient.get(contentProviderClient).mContentProvider();
            if (iInterface == null)
                continue;
            return iInterface.asBinder();
        }
        return null;
    }

    @Override
    public IBinder peekService(Intent intent) {
        return AppServiceDispatcher.get().peekService(intent);
    }

    @Override
    public void finishActivity(final IBinder token) {
        mH.post(() -> {
            Map<IBinder, Object> activities = BRActivityThread.get(BlackBoxCore.mainThread()).mActivities();
            if (activities.isEmpty())
                return;
            Object clientRecord = activities.get(token);
            if (clientRecord == null)
                return;
            Activity activity = getActivityByToken(token);

            while (activity.getParent() != null) {
                activity = activity.getParent();
            }

            int resultCode = BRActivity.get(activity).mResultCode();
            Intent resultData = BRActivity.get(activity).mResultData();
            ActivityManagerCompat.finishActivity(token, resultCode, resultData);
            BRActivity.get(activity)._set_mFinished(true);
        });
    }

    @Override
    public void handleNewIntent(final IBinder token, final Intent intent) {
        mH.post(() -> {
            Intent newIntent;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP_MR1) {
                newIntent = BRReferrerIntent.get()._new(intent, BlackBoxCore.getHostPkg());
            } else {
                newIntent = intent;
            }
            try {
                Object mainThread = BlackBoxCore.mainThread();
                Map activities = BRActivityThread.get(mainThread).mActivities();
                Object record = activities.get(token);
                if (record != null) {
                    // Use reflection to get activity reference from ActivityClientRecord
                    // because BRActivityThread.ActivityClientRecord is not resolving
                    try {
                        java.lang.reflect.Field activityField = record.getClass().getDeclaredField("activity");
                        activityField.setAccessible(true);
                        android.app.Activity activity = (android.app.Activity) activityField.get(record);
                        if (activity != null) {
                            activity.setIntent(newIntent);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            } catch (Throwable e) {
                e.printStackTrace();
            }
            Object mainThread = BlackBoxCore.mainThread();
            if (BRActivityThread.get(BlackBoxCore.mainThread())._check_performNewIntents(null, null) != null) {
                BRActivityThread.get(mainThread).performNewIntents(
                        token,
                        Collections.singletonList(newIntent)
                );
            } else if (BRActivityThreadNMR1.get(mainThread)._check_performNewIntents(null, null, false) != null) {
                BRActivityThreadNMR1.get(mainThread).performNewIntents(
                        token,
                        Collections.singletonList(newIntent),
                        true);
            } else if (BRActivityThreadQ.get(mainThread)._check_handleNewIntent(null, null) != null) {
                BRActivityThreadQ.get(mainThread).handleNewIntent(token, Collections.singletonList(newIntent));
            }
        });
    }

    @Override
    public void scheduleReceiver(ReceiverData data) throws RemoteException {
        if (!isInit()) {
            bindApplication();
        }
        mH.post(() -> {
            BroadcastReceiver mReceiver = null;
            Intent intent = data.intent;
            ActivityInfo activityInfo = data.activityInfo;
            BroadcastReceiver.PendingResult pendingResult = data.data.build();

            try {
                Context baseContext = mInitialApplication.getBaseContext();
                ClassLoader classLoader = baseContext.getClassLoader();
                intent.setExtrasClassLoader(classLoader);

                mReceiver = (BroadcastReceiver) classLoader.loadClass(activityInfo.name).newInstance();
                BRBroadcastReceiver.get(mReceiver).setPendingResult(pendingResult);
                mReceiver.onReceive(baseContext, intent);
                BroadcastReceiver.PendingResult finish = BRBroadcastReceiver.get(mReceiver).getPendingResult();
                if (finish != null) {
                    finish.finish();
                }
                BlackBoxCore.getBActivityManager().finishBroadcast(data.data);
            } catch (Throwable throwable) {
                throwable.printStackTrace();
                Slog.e(TAG,
                        "Error receiving broadcast " + intent
                                + " in " + mReceiver);
            }
        });
    }

    public static Activity getActivityByToken(IBinder token) {
        Map<IBinder, Object> iBinderObjectMap =
                BRActivityThread.get(BlackBoxCore.mainThread()).mActivities();
        return BRActivityThreadActivityClientRecord.get(iBinderObjectMap.get(token)).activity();
    }

    private void onBeforeCreateApplication(String packageName, String processName, Context context) {
        for (AppLifecycleCallback appLifecycleCallback : BlackBoxCore.get().getAppLifecycleCallbacks()) {
            appLifecycleCallback.beforeCreateApplication(packageName, processName, context, BActivityThread.getUserId());
        }
    }

    private void onBeforeApplicationOnCreate(String packageName, String processName, Application application) {
        for (AppLifecycleCallback appLifecycleCallback : BlackBoxCore.get().getAppLifecycleCallbacks()) {
            appLifecycleCallback.beforeApplicationOnCreate(packageName, processName, application, BActivityThread.getUserId());
        }
    }

    private void onAfterApplicationOnCreate(String packageName, String processName, Application application) {
        for (AppLifecycleCallback appLifecycleCallback : BlackBoxCore.get().getAppLifecycleCallbacks()) {
            appLifecycleCallback.afterApplicationOnCreate(packageName, processName, application, BActivityThread.getUserId());
        }
    }

    
    public static void ensureActivityContext(Activity activity) {
        if (activity == null) {
            return;
        }
        
        try {
            
            Context currentContext = activity.getBaseContext();
            if (currentContext != null) {
                Slog.d(TAG, "Activity already has context: " + activity.getClass().getName());
                return;
            }
            
            Slog.w(TAG, "Activity has null context, ensuring valid context: " + activity.getClass().getName());
            
            
            Context validContext = null;
            try {
                validContext = getApplication();
                if (validContext == null) {
                    validContext = BlackBoxCore.getContext();
                }
            } catch (Exception e) {
                Slog.w(TAG, "Could not get application context: " + e.getMessage());
                validContext = BlackBoxCore.getContext();
            }
            
            if (validContext != null) {
                
                try {
                    Context packageContext = validContext.createPackageContext(
                        activity.getPackageName(),
                        Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY
                    );
                    
                    
                    java.lang.reflect.Method attachBaseContext = Activity.class.getDeclaredMethod("attachBaseContext", Context.class);
                    attachBaseContext.setAccessible(true);
                    attachBaseContext.invoke(activity, packageContext);
                    Slog.d(TAG, "Successfully attached package context to activity: " + activity.getClass().getName());
                } catch (Exception e) {
                    Slog.w(TAG, "Could not attach base context to activity: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            Slog.e(TAG, "Error ensuring activity context: " + e.getMessage());
        }
    }

    
    public static void hookActivityThread() {
        try {
            
            Object activityThread = BlackBoxCore.mainThread();
            if (activityThread != null) {
                
                Instrumentation instrumentation = BRActivityThread.get(activityThread).mInstrumentation();
                if (instrumentation != null) {
                    Slog.d(TAG, "Found ActivityThread instrumentation, ensuring it's our AppInstrumentation");
                    
                    
                    if (!(instrumentation instanceof AppInstrumentation)) {
                        Slog.w(TAG, "ActivityThread instrumentation is not our AppInstrumentation, attempting to replace");
                        
                        
                        try {
                            AppInstrumentation appInstrumentation = AppInstrumentation.get();
                            appInstrumentation.injectHook();
                            Slog.d(TAG, "Successfully replaced ActivityThread instrumentation with AppInstrumentation");
                        } catch (Exception e) {
                            Slog.w(TAG, "Could not replace ActivityThread instrumentation: " + e.getMessage());
                        }
                    } else {
                        Slog.d(TAG, "ActivityThread instrumentation is already our AppInstrumentation");
                    }
                } else {
                    Slog.w(TAG, "ActivityThread instrumentation is null");
                }
            } else {
                Slog.w(TAG, "ActivityThread is null");
            }
        } catch (Exception e) {
            Slog.e(TAG, "Error hooking ActivityThread: " + e.getMessage());
        }
    }

    
    private Application createMinimalApplication(Context packageContext, String packageName) {
        try {
            Slog.d(TAG, "Creating minimal application for " + packageName);
            
            
            Application app = new Application() {
                @Override
                public void onCreate() {
                    super.onCreate();
                    Slog.d(TAG, "Minimal application onCreate called for " + packageName);
                }
                
                @Override
                public String getPackageName() {
                    return packageName;
                }
                
                @Override
                public Context getApplicationContext() {
                    return this;
                }
            };
            
            
            if (packageContext != null) {
                try {
                    Method attachBaseContext = Application.class.getDeclaredMethod("attachBaseContext", Context.class);
                    attachBaseContext.setAccessible(true);
                    attachBaseContext.invoke(app, packageContext);
                    Slog.d(TAG, "Successfully attached base context to minimal application for " + packageName);
                } catch (Exception e) {
                    Slog.w(TAG, "Could not attach base context to minimal application: " + e.getMessage());
                }
            } else {
                Slog.w(TAG, "Package context is null, cannot attach base context to minimal application");
            }
            
            Slog.d(TAG, "Minimal application created successfully for " + packageName);
            return app;
        } catch (Exception e) {
            Slog.e(TAG, "Error creating minimal application for " + packageName, e);
            return null;
        }
    }

    // ============================================================================================
    // Coupang-specific: Locale, AppRestarter, DE storage hooks
    // ============================================================================================

    private static boolean isCoupangPackage(String packageName) {
        return "com.coupang.mobile".equals(packageName)
                || (packageName != null && packageName.contains("coupang"));
    }

    /**
     * Phase 1: Set JVM-level Locale defaults BEFORE any app code loads.
     * This runs before makeApplication(), so Locale.getDefault() already returns ko_KR
     * when the Application class initializer runs.
     */
    private static void applyKoreanLocaleDefaults() {
        try {
            java.util.Locale koKR = new java.util.Locale("ko", "KR");
            java.util.Locale.setDefault(koKR);

            // Force internal Locale fields via reflection (Android may cache these)
            for (String fieldName : new String[]{"defaultLocale", "sDefault"}) {
                try {
                    java.lang.reflect.Field f = java.util.Locale.class.getDeclaredField(fieldName);
                    f.setAccessible(true);
                    f.set(null, koKR);
                } catch (Throwable ignored) {}
            }

            // Patch system Resources configuration
            android.content.res.Resources sysRes = android.content.res.Resources.getSystem();
            android.content.res.Configuration cfg = new android.content.res.Configuration(sysRes.getConfiguration());
            cfg.locale = koKR;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                cfg.setLocales(new android.os.LocaleList(koKR));
            }
            try {
                java.lang.reflect.Field f = android.content.res.Configuration.class.getDeclaredField("userSetLocale");
                f.setAccessible(true);
                f.setBoolean(cfg, true);
            } catch (Throwable ignored) {}
            sysRes.updateConfiguration(cfg, sysRes.getDisplayMetrics());

            Log.d(TAG, "[Coupang] Phase1 locale set: " + java.util.Locale.getDefault());
        } catch (Throwable e) {
            Log.e(TAG, "[Coupang] Phase1 locale failed", e);
        }
    }

    /**
     * Phase 2: Patch the Application's own Resources to return ko_KR.
     * This runs AFTER makeApplication(), so the app's Context and Resources exist.
     */
    private static void applyLocaleToAppResources(Application app) {
        try {
            java.util.Locale koKR = new java.util.Locale("ko", "KR");

            // 2a. Patch app's own Resources.getConfiguration()
            android.content.res.Resources appRes = app.getResources();
            if (appRes != null) {
                android.content.res.Configuration appCfg = new android.content.res.Configuration(appRes.getConfiguration());
                appCfg.locale = koKR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    appCfg.setLocales(new android.os.LocaleList(koKR));
                }
                try {
                    java.lang.reflect.Field f = android.content.res.Configuration.class.getDeclaredField("userSetLocale");
                    f.setAccessible(true);
                    f.setBoolean(appCfg, true);
                } catch (Throwable ignored) {}
                appRes.updateConfiguration(appCfg, appRes.getDisplayMetrics());
                Log.d(TAG, "[Coupang] Phase2 app Resources patched: " + appRes.getConfiguration().locale);
            }

            // 2b. Patch the ActivityThread's mConfiguration field
            try {
                Object activityThread = BlackBoxCore.mainThread();
                java.lang.reflect.Field mConfigField = activityThread.getClass().getDeclaredField("mConfiguration");
                mConfigField.setAccessible(true);
                android.content.res.Configuration threadCfg = (android.content.res.Configuration) mConfigField.get(activityThread);
                if (threadCfg != null) {
                    threadCfg.locale = koKR;
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        threadCfg.setLocales(new android.os.LocaleList(koKR));
                    }
                    Log.d(TAG, "[Coupang] Phase2 ActivityThread.mConfiguration patched");
                }
            } catch (Throwable e) {
                Log.w(TAG, "[Coupang] Phase2 ActivityThread.mConfiguration patch failed: " + e.getMessage());
            }

            // 2c. Patch the ContextImpl's mOverrideConfiguration if available
            try {
                Context baseCtx = app.getBaseContext();
                java.lang.reflect.Field overrideCfgField = baseCtx.getClass().getDeclaredField("mOverrideConfiguration");
                overrideCfgField.setAccessible(true);
                android.content.res.Configuration overrideCfg = (android.content.res.Configuration) overrideCfgField.get(baseCtx);
                if (overrideCfg == null) {
                    overrideCfg = new android.content.res.Configuration();
                }
                overrideCfg.locale = koKR;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    overrideCfg.setLocales(new android.os.LocaleList(koKR));
                }
                overrideCfgField.set(baseCtx, overrideCfg);
                Log.d(TAG, "[Coupang] Phase2 ContextImpl.mOverrideConfiguration patched");
            } catch (Throwable ignored) {
                // Not all implementations have this field
            }

            Log.d(TAG, "[Coupang] Phase2 complete. Locale.getDefault()=" + java.util.Locale.getDefault()
                    + ", app.config.locale=" + (appRes != null ? appRes.getConfiguration().locale : "null"));
        } catch (Throwable e) {
            Log.e(TAG, "[Coupang] Phase2 locale failed", e);
        }
    }

    /**
     * Phase 3: Pre-populate COUPANG_PREF SharedPreferences with values that bypass
     * ManageMainStateUseCaseImpl.e() language/region checks.
     *
     * Root cause: ManageMainStateUseCaseImpl checks:
     *   1. homeSection == null → StartLocaleSelection (loops because intro API fails)
     *   2. !isForceLanguageChanged && !isForceLanguageChangedV2 && !hasSelectedLang → ChangeLanguage → AppRestart
     *
     * Fix: Set all bypass flags so the code reaches StartHome path.
     * Also set region to "KR" so LanguageCheckerProvider returns ko_KR as expected.
     */
    private static void injectCoupangPreferences(Application app) {
        try {
            // Get the COUPANG_PREF SharedPreferences (MODE_PRIVATE = 0)
            android.content.SharedPreferences prefs = app.getSharedPreferences("COUPANG_PREF", 0);
            android.content.SharedPreferences.Editor editor = prefs.edit();

            // Set language bypass flags
            editor.putBoolean("FORCE_LANGUAGE_CHANGED", true);
            editor.putBoolean("FORCE_LANGUAGE_CHANGED_V2", true);
            editor.putBoolean("HAS_SELECTED_LANGUAGE", true);
            editor.putString("ECOMMERCE_LANGUAGE_KEY", "ko");
            editor.putBoolean("LANGUAGE_SELECTION_POPUP_SHOWN", true);

            // Set region to KR so LanguageCheckerProvider returns correct device language
            editor.putString("application_region", "KR");

            boolean success = editor.commit();
            Log.d(TAG, "[Coupang] SharedPreferences injected: " + (success ? "SUCCESS" : "FAILED"));

            // Verify
            Log.d(TAG, "[Coupang] FORCE_LANGUAGE_CHANGED=" + prefs.getBoolean("FORCE_LANGUAGE_CHANGED", false));
            Log.d(TAG, "[Coupang] FORCE_LANGUAGE_CHANGED_V2=" + prefs.getBoolean("FORCE_LANGUAGE_CHANGED_V2", false));
            Log.d(TAG, "[Coupang] HAS_SELECTED_LANGUAGE=" + prefs.getBoolean("HAS_SELECTED_LANGUAGE", false));
            Log.d(TAG, "[Coupang] ECOMMERCE_LANGUAGE_KEY=" + prefs.getString("ECOMMERCE_LANGUAGE_KEY", ""));
            Log.d(TAG, "[Coupang] application_region=" + prefs.getString("application_region", ""));
        } catch (Throwable e) {
            Log.e(TAG, "[Coupang] SharedPreferences injection failed", e);
        }
    }

    /**
     * Phase 3b: Block Runtime.exit() and System.exit() to prevent Coupang's restart mechanism.
     *
     * Coupang's MainActivity.s0() calls:
     *   startActivity(intent);  // FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK
     *   Runtime.getRuntime().exit(0);
     *
     * In a virtual environment, exit(0) kills the virtual process, causing a respawn
     * which triggers the same checks again → infinite loop.
     *
     * We install a SecurityManager to intercept checkExit(), preventing the kill.
     */
    private static void blockProcessKill() {
        try {
            // Install a custom SecurityManager that blocks exit
            final SecurityManager existing = System.getSecurityManager();
            System.setSecurityManager(new SecurityManager() {
                @Override
                public void checkExit(int status) {
                    // Walk the call stack to detect Coupang's restart call
                    StackTraceElement[] stack = Thread.currentThread().getStackTrace();
                    for (StackTraceElement frame : stack) {
                        String cls = frame.getClassName();
                        if (cls.contains("coupang") || cls.contains("MainActivity")) {
                            Log.e(TAG, "[Coupang] BLOCKED Runtime.exit(" + status + ") from " + cls + "." + frame.getMethodName());
                            throw new SecurityException("[BlackBox] exit() blocked to prevent restart loop");
                        }
                    }
                    // Allow non-Coupang exits
                    if (existing != null) {
                        existing.checkExit(status);
                    }
                }

                @Override
                public void checkPermission(java.security.Permission perm) {
                    // Allow everything else
                    if (existing != null) {
                        try { existing.checkPermission(perm); } catch (SecurityException ignored) {}
                    }
                }

                @Override
                public void checkPermission(java.security.Permission perm, Object context) {
                    if (existing != null) {
                        try { existing.checkPermission(perm, context); } catch (SecurityException ignored) {}
                    }
                }
            });
            Log.d(TAG, "[Coupang] SecurityManager installed to block Runtime.exit()");
        } catch (Throwable e) {
            // SecurityManager may not be supported on all Android versions
            Log.w(TAG, "[Coupang] SecurityManager install failed: " + e.getMessage());
            // Fallback: try to hook Runtime.exit via reflection
            blockProcessKillFallback();
        }
    }

    /**
     * Fallback: If SecurityManager fails, use the Runtime shutdown hook approach
     * to detect and suppress exit.
     */
    private static void blockProcessKillFallback() {
        try {
            // Add a shutdown hook that logs but can't prevent the exit
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                Log.e(TAG, "[Coupang] SHUTDOWN HOOK: Process being killed!");
                Log.e(TAG, Log.getStackTraceString(new Throwable("Shutdown stacktrace")));
            }));
            Log.d(TAG, "[Coupang] Shutdown hook installed (fallback)");
        } catch (Throwable e) {
            Log.w(TAG, "[Coupang] Shutdown hook install failed: " + e.getMessage());
        }
    }

    /**
     * Phase 4: Hook Device Encrypted (DE) storage access to log and verify redirection.
     * Patches Context.createDeviceProtectedStorageContext() to log all access.
     */
    private static void hookDeviceProtectedStorage(Application app, String packageName) {
        try {
            // Log the DE data dir that IOCore should be redirecting
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                Context deContext = app.createDeviceProtectedStorageContext();
                if (deContext != null) {
                    File deDataDir = deContext.getDataDir();
                    File deFilesDir = deContext.getFilesDir();
                    File deCacheDir = deContext.getCacheDir();
                    Log.d(TAG, "[Coupang-DE] DeviceProtected dataDir  = " + (deDataDir != null ? deDataDir.getAbsolutePath() : "null"));
                    Log.d(TAG, "[Coupang-DE] DeviceProtected filesDir = " + (deFilesDir != null ? deFilesDir.getAbsolutePath() : "null"));
                    Log.d(TAG, "[Coupang-DE] DeviceProtected cacheDir = " + (deCacheDir != null ? deCacheDir.getAbsolutePath() : "null"));

                    // Check if SharedPreferences can be created in DE storage
                    try {
                        android.content.SharedPreferences deSp = deContext.getSharedPreferences("coupang_de_test", Context.MODE_PRIVATE);
                        boolean writeOk = deSp.edit().putBoolean("test_write", true).commit();
                        Log.d(TAG, "[Coupang-DE] SharedPreferences write test: " + (writeOk ? "SUCCESS" : "FAILED"));
                        // Clean up test
                        deSp.edit().clear().commit();
                    } catch (Throwable e) {
                        Log.e(TAG, "[Coupang-DE] SharedPreferences write test EXCEPTION: " + e.getMessage());
                    }
                } else {
                    Log.w(TAG, "[Coupang-DE] createDeviceProtectedStorageContext returned null!");
                }
            }

            // Also log regular data dir for comparison
            Log.d(TAG, "[Coupang-DE] Regular dataDir = " + app.getDataDir().getAbsolutePath());
            Log.d(TAG, "[Coupang-DE] Regular filesDir = " + app.getFilesDir().getAbsolutePath());

        } catch (Throwable e) {
            Log.e(TAG, "[Coupang-DE] DE storage hook failed", e);
        }
    }

    private void handleDeviceSpoofing(AppConfig config) {
        if (config.spoofProps == null) return;
        Map<String, String> props = config.spoofProps;
        Log.d(TAG, "Device spoofing for " + config.packageName + ": " + props);
        try {
            Class<?> buildClass = android.os.Build.class;
            Class<?> buildVersionClass = android.os.Build.VERSION.class;

            for (Map.Entry<String, String> entry : props.entrySet()) {
                String key = entry.getKey();
                String value = entry.getValue();
                try {
                    if ("SDK_INT".equals(key) || "RELEASE".equals(key)) {
                        Field field = buildVersionClass.getDeclaredField(key);
                        field.setAccessible(true);
                        if ("SDK_INT".equals(key)) field.set(null, Integer.parseInt(value));
                        else field.set(null, value);
                    } else {
                        Field field = buildClass.getDeclaredField(key);
                        field.setAccessible(true);
                        field.set(null, value);
                    }
                } catch (Exception e) {
                    Log.w(TAG, "Failed to spoof " + key, e);
                }
            }
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static class AppBindData {
        String processName;
        ApplicationInfo appInfo;
        List<ProviderInfo> providers;
        Object info;
    }
}
