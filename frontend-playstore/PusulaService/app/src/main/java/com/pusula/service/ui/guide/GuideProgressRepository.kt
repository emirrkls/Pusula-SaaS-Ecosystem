package com.pusula.service.ui.guide

import android.content.Context
import com.google.gson.Gson
import com.pusula.service.data.model.guide.GuideRecord
import com.pusula.service.data.model.guide.GuideTopic
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/** Local-only learning state, keyed by hashed subject + company + role, never the JWT itself. */
@Singleton class GuideProgressRepository @Inject constructor(@ApplicationContext context: Context) {
    private val prefs = context.getSharedPreferences("pusula_guide_progress", Context.MODE_PRIVATE)
    private fun key(scope: String, role: String, topic: String) = "$scope|$role|$topic"
    fun read(scope: String, role: String, topic: GuideTopic): GuideRecord = runCatching {
        Gson().fromJson(prefs.getString(key(scope, role, topic.id), null), GuideRecord::class.java)
    }.getOrNull() ?: GuideRecord()
    fun save(scope: String, role: String, topic: GuideTopic, index: Int, completed: Boolean = false) {
        val old = read(scope, role, topic)
        val record = GuideRecord(index.coerceIn(topic.steps.indices), if (completed) topic.revision else old.completedRevision)
        prefs.edit().putString(key(scope, role, topic.id), Gson().toJson(record)).apply()
    }
    fun clear(scope: String) {
        val editor = prefs.edit()
        prefs.all.keys.filter { it.startsWith("$scope|") }.forEach { editor.remove(it) }
        editor.apply()
    }
}
