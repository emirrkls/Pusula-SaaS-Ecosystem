package com.pusula.service.data.model.guide

import com.google.gson.Gson
import com.google.gson.JsonParser
import java.security.MessageDigest
import java.text.Normalizer
import java.util.Base64
import java.util.Locale

data class GuideCategory(val id: String, val title: String, val summary: String)
data class GuideStep(val title: String, val body: String)
data class GuideTopic(
    val id: String, val category: String, val title: String, val summary: String,
    val revision: Int, val roles: List<String>, val platforms: List<String>,
    val requiredFeature: String?, val isNew: Boolean, val locations: Map<String, String>,
    val steps: List<GuideStep>
) {
    fun matches(query: String): Boolean = normalized(
        listOf(title, summary).plus(locations.values).plus(steps.flatMap { listOf(it.title, it.body) }).joinToString(" ")
    ).contains(normalized(query.trim()))
}
private fun normalized(value: String): String = Normalizer.normalize(value.lowercase(Locale.ROOT).replace('ı', 'i'), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")

data class GuideCatalog(val schemaVersion: Int, val revision: Int, val categories: List<GuideCategory>, val topics: List<GuideTopic>) {
    fun visible(role: String, platform: String = "android", category: String? = null, query: String = "") =
        topics.filter { role in it.roles && platform in it.platforms && (category == null || category == it.category) && it.matches(query) }
    companion object {
        fun decode(json: String): GuideCatalog {
            // Validate before Gson's reflective adapter can introduce null into non-null Kotlin fields.
            val root = JsonParser.parseString(json).asJsonObject
            require(root["schemaVersion"].asInt == 1 && root["revision"].asInt > 0)
            val categories = root.getAsJsonArray("categories")
            require(categories.size() > 0)
            val ids = categories.map { it.asJsonObject["id"].asString }
            require(ids.size == ids.toSet().size)
            categories.forEach { for (key in listOf("id", "title", "summary")) require(it.asJsonObject[key].asString.isNotBlank()) }
            val topics = root.getAsJsonArray("topics")
            require(topics.size() in 1..500)
            val topicIds = mutableSetOf<String>()
            for (element in topics) {
                val t = element.asJsonObject
                for (key in listOf("id", "category", "title", "summary")) require(t[key].asString.isNotBlank())
                require(topicIds.add(t["id"].asString) && t["category"].asString in ids && t["revision"].asInt > 0)
                val roles = t.getAsJsonArray("roles").map { it.asString }
                val platforms = t.getAsJsonArray("platforms").map { it.asString }
                require(roles.isNotEmpty() && roles.all { it in listOf("COMPANY_ADMIN", "SUPER_ADMIN", "TECHNICIAN") })
                require(platforms.isNotEmpty() && platforms.all { it in listOf("ios", "android") })
                require(platforms.all { t.getAsJsonObject("locations")[it].asString.isNotBlank() })
                require(t.getAsJsonArray("steps").size() in 2..6)
                t.getAsJsonArray("steps").forEach { require(it.asJsonObject["title"].asString.isNotBlank() && it.asJsonObject["body"].asString.isNotBlank()) }
                t["isNew"].asBoolean
            }
            return Gson().fromJson(root, GuideCatalog::class.java)
        }
    }
}
data class GuideCursor(val count: Int, val index: Int = 0) {
    val boundedIndex get() = index.coerceIn(0, (count - 1).coerceAtLeast(0))
    val isLast get() = boundedIndex == (count - 1).coerceAtLeast(0)
    fun next() = copy(index = (boundedIndex + 1).coerceAtMost((count - 1).coerceAtLeast(0)))
    fun back() = copy(index = (boundedIndex - 1).coerceAtLeast(0))
}
data class GuideRecord(val step: Int = 0, val completedRevision: Int = 0) {
    fun complete(topic: GuideTopic) = completedRevision >= topic.revision
    fun start(topic: GuideTopic) = if (complete(topic)) 0 else GuideCursor(topic.steps.size, step).boundedIndex
}
object GuideIdentity {
    fun scope(token: String?, company: Int?): String? = runCatching {
        val payload = token?.split('.')?.getOrNull(1) ?: return null
        val subject = JsonParser.parseString(String(Base64.getUrlDecoder().decode(payload), Charsets.UTF_8)).asJsonObject["sub"].asString
        require(subject.isNotBlank())
        MessageDigest.getInstance("SHA-256").digest("${company ?: 0}|$subject".toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }.getOrNull()
}
