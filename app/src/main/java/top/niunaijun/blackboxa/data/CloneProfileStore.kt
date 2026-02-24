package top.niunaijun.blackboxa.data

import top.niunaijun.blackboxa.app.AppManager

object CloneProfileStore {
    private const val PREFIX_NAME = "clone_name"
    private const val PREFIX_PROCESS = "clone_process"
    private const val PREFIX_ANDROID_ID = "clone_android_id"
    private const val PREFIX_MODEL = "clone_model"

    private fun key(prefix: String, packageName: String, userId: Int): String {
        return "${prefix}_${userId}_$packageName"
    }

    fun getDisplayName(packageName: String, userId: Int): String? {
        return AppManager.mRemarkSharedPreferences.getString(key(PREFIX_NAME, packageName, userId), null)
    }

    fun setDisplayName(packageName: String, userId: Int, name: String?) {
        AppManager.mRemarkSharedPreferences.edit()
            .putString(key(PREFIX_NAME, packageName, userId), name)
            .apply()
    }

    fun getProcessName(packageName: String, userId: Int): String? {
        return AppManager.mRemarkSharedPreferences.getString(key(PREFIX_PROCESS, packageName, userId), null)
    }

    fun setProcessName(packageName: String, userId: Int, processName: String?) {
        AppManager.mRemarkSharedPreferences.edit()
            .putString(key(PREFIX_PROCESS, packageName, userId), processName)
            .apply()
    }

    fun getAndroidId(packageName: String, userId: Int): String? {
        return AppManager.mRemarkSharedPreferences.getString(key(PREFIX_ANDROID_ID, packageName, userId), null)
    }

    fun setAndroidId(packageName: String, userId: Int, androidId: String?) {
        AppManager.mRemarkSharedPreferences.edit()
            .putString(key(PREFIX_ANDROID_ID, packageName, userId), androidId)
            .apply()
    }

    fun getModel(packageName: String, userId: Int): String? {
        return AppManager.mRemarkSharedPreferences.getString(key(PREFIX_MODEL, packageName, userId), null)
    }

    fun setModel(packageName: String, userId: Int, model: String?) {
        AppManager.mRemarkSharedPreferences.edit()
            .putString(key(PREFIX_MODEL, packageName, userId), model)
            .apply()
    }
}
