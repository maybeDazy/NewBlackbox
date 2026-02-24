package top.niunaijun.blackboxa.app

import android.annotation.SuppressLint
import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackboxa.container.ContainerInitializer


class App : Application() {

    companion object {

        @SuppressLint("StaticFieldLeak")
        @Volatile
        private lateinit var mContext: Context

        @JvmStatic
        fun getContext(): Context {
            return mContext
        }
    }

    override fun attachBaseContext(base: Context?) {
        try {
            super.attachBaseContext(base)

            try {
                BlackBoxCore.get().closeCodeInit()
            } catch (e: Exception) {
                Log.e("App", "Error in closeCodeInit: ${e.message}")
            }

            try {
                BlackBoxCore.get().onBeforeMainApplicationAttach(this, base)
            } catch (e: Exception) {
                Log.e("App", "Error in onBeforeMainApplicationAttach: ${e.message}")
            }

            mContext = base!!

            try {
                AppManager.doAttachBaseContext(base)
            } catch (e: Exception) {
                Log.e("App", "Error in doAttachBaseContext: ${e.message}")
            }

            try {

                BlackBoxCore.get().onAfterMainApplicationAttach(this, base)

            } catch (e: Exception) {

                Log.e("App", "Error in onAfterMainApplicationAttach: ${e.message}")

            }
        } catch (e: Exception) {
            Log.e("App", "Critical error in attachBaseContext: ${e.message}")
            if (base != null) {
                mContext = base
            }
        }
    }

    override fun onCreate() {
        try {
            super.onCreate()
            AppManager.doOnCreate(mContext)
            if (isMainProcess()) {
                ContainerInitializer.warmUp(mContext)
            }
        } catch (e: Exception) {
            Log.e("App", "Error in onCreate: ${e.message}")
        }
    }

    private fun isMainProcess(): Boolean {
        return try {
            val processName = getCurrentProcessName()
            processName == null || processName == packageName
        } catch (e: Exception) {
            Log.w("App", "Unable to determine process name, assuming main process: ${e.message}")
            true
        }
    }

    private fun getCurrentProcessName(): String? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Application.getProcessName()
        }

        val pid = Process.myPid()
        val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return null
        return activityManager.runningAppProcesses?.firstOrNull { it.pid == pid }?.processName
    }
}
