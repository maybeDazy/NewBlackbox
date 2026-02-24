package daize.pro.cloner.bean

import android.graphics.drawable.Drawable


data class AppInfo(
    val name: String,
    val icon: Drawable?,
    val packageName: String,
    val sourceDir: String,
    val isXpModule: Boolean,
    val userId: Int = 0,
    val processName: String = packageName
)
