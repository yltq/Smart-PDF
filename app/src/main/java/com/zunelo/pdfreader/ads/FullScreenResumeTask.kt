package com.zunelo.pdfreader.ads

import android.content.Context
import android.net.Uri
import com.zunelo.pdfreader.data.DocumentItem
import org.json.JSONObject

data class FullScreenResumeTask(
    val action: Action,
    val payload: String = "{}"
) {
    enum class Action {
        OPEN_DOCUMENT,
        RETURN_HOME,
        SHOW_RESULT,
        SHOW_CLEAN_RESULT,
        APPLY_NEW_USER_LANGUAGE,
        COMPLETE_NEW_USER,
        OPEN_UNINSTALL_STEP_TWO
    }

    fun payloadJson(): JSONObject = runCatching { JSONObject(payload) }.getOrDefault(JSONObject())

    companion object {
        fun openDocument(item: DocumentItem) = FullScreenResumeTask(Action.OPEN_DOCUMENT, documentJson(item).toString())

        fun returnHome() = FullScreenResumeTask(Action.RETURN_HOME)

        fun showResult(item: DocumentItem, resultAction: String) = FullScreenResumeTask(
            Action.SHOW_RESULT,
            documentJson(item).put("result_action", resultAction).toString()
        )

        fun showCleanResult(freedBytes: Long, deletedCount: Int, failedCount: Int) = FullScreenResumeTask(
            Action.SHOW_CLEAN_RESULT,
            JSONObject()
                .put("freed_bytes", freedBytes)
                .put("deleted_count", deletedCount)
                .put("failed_count", failedCount)
                .toString()
        )

        fun applyNewUserLanguage(languageTag: String) = FullScreenResumeTask(
            Action.APPLY_NEW_USER_LANGUAGE,
            JSONObject().put("language_tag", languageTag).toString()
        )

        fun completeNewUser() = FullScreenResumeTask(Action.COMPLETE_NEW_USER)

        fun openUninstallStepTwo() = FullScreenResumeTask(Action.OPEN_UNINSTALL_STEP_TWO)

        fun document(payload: JSONObject): DocumentItem? {
            val uri = payload.optString("uri")
            if (uri.isBlank()) return null
            return DocumentItem(
                uri = Uri.parse(uri),
                name = payload.optString("name", "Document"),
                size = payload.optLong("size"),
                modified = payload.optLong("modified"),
                mime = payload.optString("mime", "application/octet-stream")
            )
        }

        private fun documentJson(item: DocumentItem) = JSONObject()
            .put("uri", item.uri.toString())
            .put("name", item.name)
            .put("size", item.size)
            .put("modified", item.modified)
            .put("mime", item.mime)
    }
}

object FullScreenResumeStore {
    private const val PREFERENCES = "full_screen_resume"
    private const val KEY_ACTION = "action"
    private const val KEY_PAYLOAD = "payload"

    fun save(context: Context, task: FullScreenResumeTask) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_ACTION, task.action.name)
            .putString(KEY_PAYLOAD, task.payload)
            .apply()
    }

    fun take(context: Context): FullScreenResumeTask? {
        val preferences = context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val action = preferences.getString(KEY_ACTION, null)
            ?.let { runCatching { FullScreenResumeTask.Action.valueOf(it) }.getOrNull() }
        val payload = preferences.getString(KEY_PAYLOAD, "{}").orEmpty()
        preferences.edit().clear().apply()
        return action?.let { FullScreenResumeTask(it, payload) }
    }

    fun clear(context: Context) {
        context.applicationContext.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
            .edit()
            .clear()
            .apply()
    }
}
