package daize.pro.cloner.container

import android.content.Context
import android.util.Log

object ContainerInitializer {
    private const val TAG = "ContainerInitializer"

    @Volatile
    private var initialized = false

    fun warmUp(context: Context) {
        if (initialized) return

        synchronized(this) {
            if (initialized) return
            try {
                ContainerRegistry(context.applicationContext).warmUp()
                initialized = true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to warm up container registry: ${e.message}")
            }
        }
    }
}
