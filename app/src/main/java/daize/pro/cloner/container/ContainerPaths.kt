package daize.pro.cloner.container

import android.content.Context
import java.io.File

object ContainerPaths {
    private const val CONTAINERS_DIR = "containers"

    fun containersRoot(context: Context): File {
        return File(context.getDatabasePath("container_registry.db").parentFile, CONTAINERS_DIR)
    }

    fun containerRoot(context: Context, containerId: String): File {
        return File(containersRoot(context), containerId)
    }

    fun webViewRoot(context: Context, containerId: String): File {
        return File(containerRoot(context, containerId), "webview")
    }

    fun virtualDataRoot(context: Context, containerId: String): File {
        return File(containerRoot(context, containerId), "virtual_data")
    }

    fun virtualDeRoot(context: Context, containerId: String): File {
        return File(containerRoot(context, containerId), "virtual_de")
    }
}
