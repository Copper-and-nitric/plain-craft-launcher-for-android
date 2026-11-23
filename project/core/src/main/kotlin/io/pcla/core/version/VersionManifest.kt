package io.pcla.core.version

import io.pcla.core.json.MiniJson

/** One row of the official version_manifest_v2.json. */
data class VersionEntry(
    val id: String,
    val type: String,
    val url: String,
    /** ISO-8601 release time, e.g. `2026-11-20T10:12:00+00:00`. Older manifests may omit it. */
    val releaseTime: String? = null,
)

object VersionManifest {
    fun parse(text: String): List<VersionEntry> {
        val root = MiniJson.parse(text) as? Map<*, *> ?: error("manifest 顶层不是对象")
        val list = root["versions"] as? List<*> ?: return emptyList()
        return list.mapNotNull { item ->
            val m = item as? Map<*, *> ?: return@mapNotNull null
            val id = m["id"] as? String ?: return@mapNotNull null
            val url = m["url"] as? String ?: return@mapNotNull null
            VersionEntry(
                id = id,
                type = m["type"] as? String ?: "release",
                url = url,
                releaseTime = m["releaseTime"] as? String,
            )
        }
    }

    fun find(entries: List<VersionEntry>, id: String): VersionEntry? =
        entries.firstOrNull { it.id == id }
            ?: entries.firstOrNull { it.id.equals(id, ignoreCase = true) }
}
