package daize.pro.cloner.container

import android.content.Context
import android.net.Uri
import android.webkit.URLUtil
import top.niunaijun.blackbox.BlackBoxCore
import top.niunaijun.blackbox.entity.pm.InstallResult
import java.io.File

class ContainerInstallCoordinator(context: Context) {
    private val registry = ContainerRegistry(context.applicationContext)

    data class InstallOutcome(
        val result: InstallResult,
        val packageName: String?,
        val installedUserId: Int,
        val containerRecord: ContainerRecord?,
    )

    fun install(source: String, preferredUserId: Int): InstallOutcome {
        val blackBoxCore = BlackBoxCore.get()
        val resolvedPackageName = resolvePackageName(source)
        val targetUserId = resolveInstallUserId(preferredUserId, resolvedPackageName)

        if (targetUserId != preferredUserId || !userExists(targetUserId)) {
            blackBoxCore.createUser(targetUserId)
        }

        val installResult =
            if (URLUtil.isValidUrl(source)) {
                blackBoxCore.installPackageAsUser(Uri.parse(source), targetUserId)
            } else {
                blackBoxCore.installPackageAsUser(source, targetUserId)
            }

        val packageName = installResult.packageName ?: resolvedPackageName
        val containerRecord =
            if (installResult.success && !packageName.isNullOrBlank()) {
                registry.findContainer(packageName, targetUserId)
                    ?: registry.createContainer(
                        packageName = packageName,
                        displayName = packageName,
                        virtualUserId = targetUserId,
                        source = source,
                    )
            } else {
                null
            }

        return InstallOutcome(
            result = installResult,
            packageName = packageName,
            installedUserId = targetUserId,
            containerRecord = containerRecord,
        )
    }


    private fun userExists(userId: Int): Boolean {
        return BlackBoxCore.get().users.any { it.id == userId }
    }

    private fun resolveInstallUserId(preferredUserId: Int, packageName: String?): Int {
        if (packageName.isNullOrBlank()) return preferredUserId
        return if (BlackBoxCore.get().isInstalled(packageName, preferredUserId)) {
            nextAvailableUserId()
        } else {
            preferredUserId
        }
    }

    private fun nextAvailableUserId(): Int {
        val users = BlackBoxCore.get().users
        var candidate = (users.maxOfOrNull { it.id } ?: -1) + 1
        val existing = users.map { it.id }.toSet()
        while (existing.contains(candidate)) {
            candidate++
        }
        return candidate
    }

    private fun resolvePackageName(source: String): String? {
        if (URLUtil.isValidUrl(source)) return null

        val file = File(source)
        return if (file.exists()) {
            BlackBoxCore.getPackageManager().getPackageArchiveInfo(source, 0)?.packageName
        } else {
            source
        }
    }
}
