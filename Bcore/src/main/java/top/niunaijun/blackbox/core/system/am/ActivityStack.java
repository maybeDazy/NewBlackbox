package top.niunaijun.blackbox.core.system.am;

import android.app.ActivityManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.IInterface;
import android.os.Looper;
import android.os.Message;
import android.os.RemoteException;
import android.util.Log;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import black.android.app.BRActivityManagerNative;
import black.android.app.BRIActivityManager;
import black.com.android.internal.BRRstyleable;
import top.niunaijun.blackbox.BlackBoxCore;
import top.niunaijun.blackbox.core.system.BProcessManagerService;
import top.niunaijun.blackbox.core.system.ProcessRecord;
import top.niunaijun.blackbox.core.system.pm.BPackageManagerService;
import top.niunaijun.blackbox.core.system.pm.PackageManagerCompat;
import top.niunaijun.blackbox.proxy.ProxyActivity;
import top.niunaijun.blackbox.proxy.ProxyManifest;
import top.niunaijun.blackbox.proxy.record.ProxyActivityRecord;
import top.niunaijun.blackbox.utils.ComponentUtils;
import top.niunaijun.blackbox.utils.Slog;
import top.niunaijun.blackbox.utils.compat.ActivityManagerCompat;

import static android.content.pm.PackageManager.GET_ACTIVITIES;


@SuppressWarnings({"deprecation", "unchecked"})
public class ActivityStack {
    public static final String TAG = "ActivityStack";

    private final ActivityManager mAms;
    private final Map<Integer, TaskRecord> mTasks = new LinkedHashMap<>();
    private final Set<ActivityRecord> mLaunchingActivities = new HashSet<>();

    public static final int LAUNCH_TIME_OUT = 0;
    private final Handler mHandler = new Handler(Looper.getMainLooper()) {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case LAUNCH_TIME_OUT:
                    ActivityRecord record = (ActivityRecord) msg.obj;
                    if (record != null) {
                        mLaunchingActivities.remove(record);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    public ActivityStack() {
        mAms = (ActivityManager) BlackBoxCore.getContext().getSystemService(Context.ACTIVITY_SERVICE);
    }

    public boolean containsFlag(Intent intent, int flag) {
        return (intent.getFlags() & flag) != 0;
    }

    public int startActivitiesLocked(int userId, Intent[] intents, String[] resolvedTypes, IBinder resultTo, Bundle options) {
        if (intents == null) {
            throw new NullPointerException("intents is null");
        }
        if (resolvedTypes == null) {
            throw new NullPointerException("resolvedTypes is null");
        }
        if (intents.length != resolvedTypes.length) {
            throw new IllegalArgumentException("intents are length different than resolvedTypes");
        }
        for (int i = 0; i < intents.length; i++) {
            startActivityLocked(userId, intents[i], resolvedTypes[i], resultTo, null, -1, 0, options);
        }
        return 0;
    }

    public int startActivityLocked(int userId, Intent intent, String resolvedType, IBinder resultTo, String resultWho, int requestCode, int flags, Bundle options) {
        synchronized (mTasks) {
            synchronizeTasks();
        }

        ResolveInfo resolveInfo = BPackageManagerService.get().resolveActivity(intent, GET_ACTIVITIES, resolvedType, userId);
        if (resolveInfo == null || resolveInfo.activityInfo == null) {
            return 0;
        }
        Log.d(TAG, "startActivityLocked : " + resolveInfo.activityInfo);
        ActivityInfo activityInfo = resolveInfo.activityInfo;

        // Loop-breaker: use flattenToShortString() for consistent key with onFinishActivity
        String compKey = new ComponentName(activityInfo.packageName, activityInfo.name).flattenToShortString();

        // Clear expired suppressions
        long now = System.currentTimeMillis();
        if (!mSuppressedComponents.isEmpty() && now > mSuppressExpireTime) {
            Log.d(TAG, "LOOP BREAKER: Suppression expired, clearing " + mSuppressedComponents.size() + " components");
            mSuppressedComponents.clear();
            mFinishTracker.clear();
        }

        // Check suppression set
        if (mSuppressedComponents.contains(compKey)) {
            Log.e(TAG, "LOOP BREAKER: Suppressing launch of " + compKey + " (in suppression set)");
            return 0;
        }

        ActivityRecord sourceRecord = findActivityRecordByToken(userId, resultTo);
        if (sourceRecord == null) {
            resultTo = null;
        }
        TaskRecord sourceTask = null;
        if (sourceRecord != null) {
            sourceTask = sourceRecord.task;
        }

        String taskAffinity = ComponentUtils.getTaskAffinity(activityInfo);

        int launchModeFlags = 0;
        boolean singleTop = containsFlag(intent, Intent.FLAG_ACTIVITY_SINGLE_TOP) 
                || activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TOP
                || activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK;
        boolean newTask = containsFlag(intent, Intent.FLAG_ACTIVITY_NEW_TASK);
        boolean clearTop = containsFlag(intent, Intent.FLAG_ACTIVITY_CLEAR_TOP);
        boolean clearTask = containsFlag(intent, Intent.FLAG_ACTIVITY_CLEAR_TASK);

        if ((flags & Intent.FLAG_ACTIVITY_FORWARD_RESULT) != 0 && sourceRecord != null) {
            // Transfer the result target from the source activity to the new one.
            if (requestCode >= 0) {
                 // Forward result usually expects no new requestCode, but if one is provided?
                 // Android docs say: "When starting an activity with this flag, the requestCode must be >= 0."
                 // Actually, it says calling startActivityForResult is required? Sdk says:
                 // "startActivity(Intent)" uses -1.
                 // If we have FORWARD_RESULT, we should use sourceRecord.resultTo.
            }
            resultTo = sourceRecord.resultTo;
            resultWho = sourceRecord.resultWho;
            requestCode = sourceRecord.requestCode;
            
            // Log flag detection
            Log.d(TAG, "DEBUG-LOOP: FORWARD_RESULT detected from " + sourceRecord.component.flattenToShortString() 
                + " to " + compKey + ". resultTo=" + resultTo);
        }

        // Log.d(TAG, "DEBUG-AUTH: start " + compKey + " reqCode=" + requestCode + " flags=0x" + Integer.toHexString(flags) + " caller=" + (sourceRecord != null ? sourceRecord.component.flattenToShortString() : "null") + " data=" + intent.getDataString());

        TaskRecord taskRecord = null;
        switch (activityInfo.launchMode) {
            case ActivityInfo.LAUNCH_SINGLE_TOP:
            case ActivityInfo.LAUNCH_MULTIPLE:
            case ActivityInfo.LAUNCH_SINGLE_TASK:
                taskRecord = findTaskRecordByTaskAffinityLocked(userId, taskAffinity);
                if (taskRecord == null && !newTask) {
                    taskRecord = sourceTask;
                }
                break;
            case ActivityInfo.LAUNCH_SINGLE_INSTANCE:
                taskRecord = findTaskRecordByTaskAffinityLocked(userId, taskAffinity);
                break;
        }

        
        if (taskRecord == null || taskRecord.needNewTask()) {
            return startActivityInNewTaskLocked(userId, intent, activityInfo, resultTo, launchModeFlags);
        }
        
        mAms.moveTaskToFront(taskRecord.id, 0);

        boolean notStartToFront = false;
        if (clearTop || singleTop || clearTask) {
            notStartToFront = true;
        }

        boolean startTaskToFront = !notStartToFront
                && ComponentUtils.intentFilterEquals(taskRecord.rootIntent, intent)
                && taskRecord.rootIntent.getFlags() == intent.getFlags();

        // FIX: Prevent infinite self-start loops for activities (like Coupang MainActivity)
        // If the top activity is the same as the one being started, and the caller is also the same,
        // force singleTop behavior to deliver new intent instead of creating a new instance.
        ActivityRecord topActivityRecord = taskRecord.getTopActivityRecord();
        ActivityRecord targetActivityRecord = findActivityRecordByComponentName(userId, ComponentUtils.toComponentName(activityInfo));
        
        if (topActivityRecord != null && topActivityRecord.component.equals(ComponentUtils.toComponentName(activityInfo))) {
             if (sourceRecord != null && sourceRecord.component.equals(topActivityRecord.component)) {
                 Log.d(TAG, "DEBUG-LOOP: Self-start detected for " + compKey + ". Forcing singleTop / newIntent behavior.");
                 // Force singleTop consideration
                 singleTop = true;
                 // Disable startTaskToFront so we proceed to deliverNewIntentLocked logic
                 startTaskToFront = false;
             }
        }

        if (startTaskToFront)
            return 0;
        ActivityRecord newIntentRecord = null;
        boolean ignore = false;

        if (clearTop) {
            if (targetActivityRecord != null) {
                
                synchronized (targetActivityRecord.task.activities) {
                    for (int i = targetActivityRecord.task.activities.size() - 1; i >= 0; i--) {
                        ActivityRecord next = targetActivityRecord.task.activities.get(i);
                        if (next != targetActivityRecord) {
                            next.finished = true;
                            if (next.processRecord != null && next.token != null) {
                                try {
                                    if(next.processRecord != null && next.processRecord.bActivityThread != null && next.token != null) {
                                      next.processRecord.bActivityThread.finishActivity(next.token);
                                    }
                                } catch (RemoteException | NullPointerException e) {
                                    if (!(e instanceof android.os.DeadObjectException)) {
                                          e.printStackTrace();
                                    }
                                }
                            }
                            Log.d(TAG, "makerFinish: " + next.component.toString());
                        } else {
                            if (singleTop) {
                                newIntentRecord = targetActivityRecord;
                            } else {
                                
                                targetActivityRecord.finished = true;
                            }
                            break;
                        }
                    }
                }
            }
        }

        if (singleTop && !clearTop) {
            ComponentName toComponentName = ComponentUtils.toComponentName(activityInfo);
            if (topActivityRecord.component.equals(toComponentName)) {
                newIntentRecord = topActivityRecord;
            } else {
                synchronized (mLaunchingActivities) {
                    for (ActivityRecord launchingActivity : mLaunchingActivities) {
                        if (!launchingActivity.finished && launchingActivity.component.equals(intent.getComponent())) {
                            
                            ignore = true;
                        }
                    }
                }
            }
        }

        if (activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_TASK && !clearTop) {
            ComponentName toComponentName = ComponentUtils.toComponentName(activityInfo);
            if (topActivityRecord.component.equals(toComponentName)) {
                newIntentRecord = topActivityRecord;
            } else {
                ActivityRecord record = findActivityRecordByComponentName(userId, ComponentUtils.toComponentName(activityInfo));
                if (record != null) {
                    
                    newIntentRecord = record;
                    
                    synchronized (taskRecord.activities) {
                        for (int i = taskRecord.activities.size() - 1; i >= 0; i--) {
                            ActivityRecord next = taskRecord.activities.get(i);
                            if (next != record) {
                                next.finished = true;
                                if (next.processRecord != null && next.token != null) {
                                    try {
                                        next.processRecord.bActivityThread.finishActivity(next.token);
                                    } catch (RemoteException ignored) {
                                    }
                                }
                            } else {
                                break;
                            }
                        }
                    }
                }
            }
        }

        if (activityInfo.launchMode == ActivityInfo.LAUNCH_SINGLE_INSTANCE) {
            newIntentRecord = topActivityRecord;
        }

        
        if (clearTask && newTask) {
            for (ActivityRecord activity : taskRecord.activities) {
                activity.finished = true;
            }
            finishAllActivity(userId);
        }
        

        if (newIntentRecord != null) {
            
            deliverNewIntentLocked(newIntentRecord, intent);
            return 0;
        } else if (ignore) {
            return 0;
        }

        IBinder callerToken = resultTo;
        if ((flags & Intent.FLAG_ACTIVITY_FORWARD_RESULT) != 0 && sourceRecord != null) {
            callerToken = sourceRecord.token;
        }
        if (callerToken == null) {
            ActivityRecord top = taskRecord.getTopActivityRecord();
            if (top != null) {
                callerToken = top.token;
            }
        } else if (sourceTask != null) {
            ActivityRecord top = sourceTask.getTopActivityRecord();
            if (top != null) {
                callerToken = top.token;
            }
        }

        IBinder resultToken = null;
        if ((flags & Intent.FLAG_ACTIVITY_FORWARD_RESULT) != 0) {
            resultToken = resultTo;
        } else if (requestCode >= 0) {
            resultToken = callerToken;
        }

        return startActivityInSourceTask(intent,
                resolvedType, callerToken, resultToken, resultWho, requestCode, flags, options, userId, topActivityRecord, activityInfo, launchModeFlags);
    }

    private void deliverNewIntentLocked(ActivityRecord activityRecord, Intent intent) {
        // Update the record's intent to the new one, as this is now the effective intent
        activityRecord.intent = intent;
        try {
            if (activityRecord.processRecord != null && activityRecord.processRecord.bActivityThread != null) {
                activityRecord.processRecord.bActivityThread.handleNewIntent(activityRecord.token, intent);
            }
        } catch (RemoteException | NullPointerException e) {
             if (!(e instanceof android.os.DeadObjectException)) {
                e.printStackTrace();
            }
        }
    }

    private Intent startActivityProcess(int userId, Intent intent, ActivityInfo
            info, ActivityRecord record) {
        ProxyActivityRecord stubRecord = new ProxyActivityRecord(userId, info, intent, record);
        ProcessRecord targetApp = BProcessManagerService.get().startProcessLocked(info.packageName, info.processName, userId, -1, Binder.getCallingPid());
        if (targetApp == null) {
            throw new RuntimeException("Unable to create process, name:" + info.name);
        }
        return getStartStubActivityIntentInner(intent, targetApp.bpid, userId, stubRecord, info);
    }

    private int startActivityInNewTaskLocked(int userId, Intent intent, ActivityInfo
            activityInfo, IBinder resultTo, int launchMode) {
        ActivityRecord record = newActivityRecord(intent, activityInfo, resultTo, userId);
        Intent shadow = startActivityProcess(userId, intent, activityInfo, record);

        shadow.addFlags(Intent.FLAG_ACTIVITY_MULTIPLE_TASK);
        shadow.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        shadow.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        shadow.addFlags(launchMode);

        BlackBoxCore.getContext().startActivity(shadow);
        return 0;
    }

    private int startActivityInSourceTask(Intent intent, String resolvedType,
                                          IBinder callerToken, IBinder resultToken, String resultWho, int requestCode, int flags,
                                          Bundle options,
                                          int userId, ActivityRecord sourceRecord, ActivityInfo activityInfo, int launchMode) {
        ActivityRecord selfRecord = newActivityRecord(intent, activityInfo, resultToken, userId);
        Intent shadow = startActivityProcess(userId, intent, activityInfo, selfRecord);
        shadow.setAction(UUID.randomUUID().toString());
        shadow.addFlags(launchMode);
        if (callerToken == null) {
            shadow.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        }
        return realStartActivityLocked(sourceRecord.processRecord.appThread, shadow, resolvedType, callerToken, resultWho, requestCode, flags, options);
    }

    private int realStartActivityLocked(IInterface appThread, Intent intent, String resolvedType,
                                        IBinder resultTo, String resultWho, int requestCode, int flags,
                                        Bundle options) {
        try {
            flags &= ~ActivityManagerCompat.START_FLAG_DEBUG;
            flags &= ~ActivityManagerCompat.START_FLAG_NATIVE_DEBUGGING;
            flags &= ~ActivityManagerCompat.START_FLAG_TRACK_ALLOCATION;

            BRIActivityManager.get(BRActivityManagerNative.get().getDefault()).startActivity(appThread, BlackBoxCore.getHostPkg(), intent,
                    resolvedType, resultTo, resultWho, requestCode, flags, null, options);
        } catch (Throwable e) {
            e.printStackTrace();
        }
        return 0;
    }

    private ActivityRecord getTopActivityRecord() {
        synchronized (mTasks) {
            synchronizeTasks();
        }
        List<TaskRecord> tasks = new LinkedList<>(mTasks.values());
        if (tasks.isEmpty())
            return null;
        return tasks.get(tasks.size() - 1).getTopActivityRecord();
    }

    private Intent getStartStubActivityIntentInner(Intent intent, int vpid,
                                                   int userId, ProxyActivityRecord target,
                                                   ActivityInfo activityInfo) {
        Intent shadow = new Intent();
        TypedArray typedArray = null;
        try {
            Resources resources = PackageManagerCompat.getResources(BlackBoxCore.getContext(), activityInfo.applicationInfo);
            int id;
            if (activityInfo.theme != 0) {
                id = activityInfo.theme;
            } else {
                id = activityInfo.applicationInfo.theme;
            }
            assert resources != null;
            typedArray = resources.newTheme().obtainStyledAttributes(id, BRRstyleable.get().Window());
            boolean windowIsTranslucent = typedArray.getBoolean(BRRstyleable.get().Window_windowIsTranslucent(), false);
            if (windowIsTranslucent) {
                shadow.setComponent(new ComponentName(BlackBoxCore.getHostPkg(), ProxyManifest.TransparentProxyActivity(vpid)));
            } else {
                shadow.setComponent(new ComponentName(BlackBoxCore.getHostPkg(), ProxyManifest.getProxyActivity(vpid)));
            }
            Slog.d(TAG, activityInfo + ", windowIsTranslucent: " + windowIsTranslucent);
        } catch (Throwable e) {
            e.printStackTrace();
            shadow.setComponent(new ComponentName(BlackBoxCore.getHostPkg(), ProxyManifest.getProxyActivity(vpid)));
        } finally {
            if (typedArray != null) {
                typedArray.recycle();
            }
        }
        ProxyActivityRecord.saveStub(shadow, intent, target.mActivityInfo, target.mActivityRecord, target.mUserId);
        return shadow;
    }

    private void finishAllActivity(int userId) {
        for (TaskRecord task : mTasks.values()) {
            for (ActivityRecord activity : task.activities) {
                if (activity.userId == userId) {
                    if (activity.finished) {
                        try {
                            activity.processRecord.bActivityThread.finishActivity(activity.token);
                        } catch (RemoteException ignored) {
                        }
                    }
                }
            }
        }
    }

    ActivityRecord newActivityRecord(Intent intent, ActivityInfo info, IBinder resultTo,
                                     int userId) {
        ActivityRecord targetRecord = ActivityRecord.create(intent, info, resultTo, userId);
        synchronized (mLaunchingActivities) {
            mLaunchingActivities.add(targetRecord);
            Message obtain = Message.obtain(mHandler, LAUNCH_TIME_OUT, targetRecord);
            mHandler.sendMessageDelayed(obtain, 2000);
        }
        return targetRecord;
    }

    private ActivityRecord findActivityRecordByComponentName(int userId, ComponentName
            componentName) {
        ActivityRecord record = null;
        for (TaskRecord next : mTasks.values()) {
            if (userId == next.userId) {
                for (ActivityRecord activity : next.activities) {
                    if (activity.component.equals(componentName)) {
                        record = activity;
                        break;
                    }
                }
            }
        }
        return record;
    }

    private ActivityRecord findActivityRecordByToken(int userId, IBinder token) {
        ActivityRecord record = null;
        if (token != null) {
            for (TaskRecord next : mTasks.values()) {
                if (userId == next.userId) {
                    for (ActivityRecord activity : next.activities) {
                        if (activity.token == token) {
                            record = activity;
                            break;
                        }
                    }
                }
            }
        }
        return record;
    }

    private TaskRecord findTaskRecordByTaskAffinityLocked(int userId, String taskAffinity) {
        synchronized (mTasks) {
            for (TaskRecord next : mTasks.values()) {
                if (userId == next.userId && next.taskAffinity.equals(taskAffinity))
                    return next;
            }
            return null;
        }
    }

    private TaskRecord findTaskRecordByTokenLocked(int userId, IBinder token) {
        synchronized (mTasks) {
            for (TaskRecord next : mTasks.values()) {
                if (userId == next.userId) {
                    for (ActivityRecord activity : next.activities) {
                        if (activity.token == token) {
                            return next;
                        }
                    }
                }
            }
            return null;
        }
    }

    public void onActivityCreated(ProcessRecord processRecord, int taskId, IBinder
            token, ActivityRecord record) {
        synchronized (mLaunchingActivities) {
            mLaunchingActivities.remove(record);
            mHandler.removeMessages(LAUNCH_TIME_OUT, record);
        }
        synchronized (mTasks) {
            synchronizeTasks();
            TaskRecord taskRecord = mTasks.get(taskId);
            if (taskRecord == null) {
                taskRecord = new TaskRecord(taskId, record.userId, ComponentUtils.getTaskAffinity(record.info));
                taskRecord.rootIntent = record.intent;
                mTasks.put(taskId, taskRecord);
            }
            record.token = token;
            record.processRecord = processRecord;
            record.task = taskRecord;
            taskRecord.addTopActivity(record);
            Log.d(TAG, "onActivityCreated : " + record.component.toString());
        }
    }

    public void onActivityResumed(int userId, IBinder token) {
        synchronized (mTasks) {
            synchronizeTasks();
            ActivityRecord activityRecord = findActivityRecordByToken(userId, token);
            if (activityRecord == null) {
                return;
            }
            Log.d(TAG, "onActivityResumed : " + activityRecord.component.toString());
            activityRecord.task.removeActivity(activityRecord);
            activityRecord.task.addTopActivity(activityRecord);
        }
    }

    public void onActivityDestroyed(int userId, IBinder token) {
        synchronized (mTasks) {
            synchronizeTasks();
            ActivityRecord activityRecord = findActivityRecordByToken(userId, token);
            if (activityRecord == null) {
                return;
            }
            activityRecord.finished = true;
            Log.d(TAG, "onActivityDestroyed : " + activityRecord.component.toString());
            activityRecord.task.removeActivity(activityRecord);
        }
    }

    // Track rapid finish→restart loop detection
    private final Map<String, long[]> mFinishTracker = new LinkedHashMap<>();
    private final Set<String> mSuppressedComponents = new HashSet<>();
    private volatile long mSuppressExpireTime = 0;
    // Allow more rapid restarts (15) within a shorter window (2s) to handle language changes
    private static final int LOOP_THRESHOLD = 15;
    private static final long LOOP_WINDOW_MS = 2000;
    private static final long SUPPRESS_DURATION_MS = 5000; // suppress launches for 5s after detection

    /**
     * @return true if the finish was SUPPRESSED (loop detected), false if finish proceeded normally
     */
    public boolean onFinishActivity(int userId, IBinder token) {
        synchronized (mTasks) {
            synchronizeTasks();
            ActivityRecord activityRecord = findActivityRecordByToken(userId, token);
            if (activityRecord == null) {
                return false;
            }
            // Use flattenToShortString ("pkg/cls") for consistent key — no object hash
            String compKey = activityRecord.component.flattenToShortString();
            Log.d(TAG, "onFinishActivity : " + compKey);

            // If already suppressed, BLOCK the finish entirely
            if (mSuppressedComponents.contains(compKey)) {
                Log.e(TAG, "LOOP BREAKER: Blocking finish() for suppressed component: " + compKey);
                return true; // suppressed
            }

            activityRecord.finished = true;

            // Detect rapid finish loop (same Activity finishing >= N times in LOOP_WINDOW_MS)
            long now = System.currentTimeMillis();
            long[] tracker = mFinishTracker.get(compKey);
            if (tracker == null) {
                tracker = new long[]{0, 0}; // [count, firstTimestamp]
                mFinishTracker.put(compKey, tracker);
            }
            if (now - tracker[1] > LOOP_WINDOW_MS) {
                // Reset window
                tracker[0] = 1;
                tracker[1] = now;
            } else {
                tracker[0]++;
            }
            if (tracker[0] >= LOOP_THRESHOLD && !mSuppressedComponents.contains(compKey)) {
                Log.e(TAG, "LOOP DETECTED: " + compKey + " finished " + tracker[0]
                        + " times in " + (now - tracker[1]) + "ms. SUPPRESSING future launches.");
                Log.e(TAG, Log.getStackTraceString(new Throwable("Finish loop stacktrace")));
                // Add to suppression set — do NOT reset tracker count
                mSuppressedComponents.add(compKey);
                mSuppressExpireTime = now + SUPPRESS_DURATION_MS;
            }
            return false; // not suppressed, finish proceeded
        }
    }

    public String getCallingPackage(IBinder token, int userId) {
        synchronized (mTasks) {
            synchronizeTasks();
            ActivityRecord activityRecordByToken = findActivityRecordByToken(userId, token);
            if (activityRecordByToken != null) {
                ActivityRecord resultTo = findActivityRecordByToken(userId, activityRecordByToken.resultTo);
                if (resultTo != null) {
                    return resultTo.info.packageName;
                }
            }
            return BlackBoxCore.getHostPkg();
        }
    }

    public ComponentName getCallingActivity(IBinder token, int userId) {
        synchronized (mTasks) {
            synchronizeTasks();
            ActivityRecord activityRecordByToken = findActivityRecordByToken(userId, token);
            if (activityRecordByToken != null) {
                ActivityRecord resultTo = findActivityRecordByToken(userId, activityRecordByToken.resultTo);
                Log.d(TAG, "DEBUG-LOOP: getCallingActivity for " + activityRecordByToken.component.flattenToShortString() 
                    + " resultToToken=" + activityRecordByToken.resultTo 
                    + " resultRecord=" + (resultTo == null ? "null" : resultTo.component.flattenToShortString()));
                
                if (resultTo != null) {
                    return resultTo.component;
                }
            } else {
                Log.d(TAG, "DEBUG-LOOP: getCallingActivity token not found or null");
            }
            // Return null when no real calling activity exists (Activity was not started for result).
            // The previous ProxyActivity fallback was incorrect and caused apps like Coupang
            // to enter infinite finish/restart loops in o0() which checks getCallingActivity() != null.
            return null;
        }
    }

    @SuppressWarnings("deprecation")
    private void synchronizeTasks() {
        List<ActivityManager.RecentTaskInfo> recentTasks = mAms.getRecentTasks(100, 0);
        Map<Integer, TaskRecord> newTacks = new LinkedHashMap<>();
        for (int i = recentTasks.size() - 1; i >= 0; i--) {
            ActivityManager.RecentTaskInfo next = recentTasks.get(i);
            TaskRecord taskRecord = mTasks.get(next.id);
            if (taskRecord == null)
                continue;
            newTacks.put(next.id, taskRecord);
        }
        mTasks.clear();
        mTasks.putAll(newTacks);
    }
}
