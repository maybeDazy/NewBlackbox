package top.niunaijun.blackboxa.container

data class ContainerRecord(
    val containerId: String,
    val packageName: String,
    val displayName: String,
    val createdAt: Long,
    val state: String,
    val virtualUserId: Int,
    val source: String?,
)
