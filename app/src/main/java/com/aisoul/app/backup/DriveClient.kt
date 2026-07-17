package com.aisoul.app.backup

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * IMPLEMENTATION §8 — Drive REST v3 over the app's own OkHttp client; the
 * Google Drive Java library is deliberately avoided (D-010). With the
 * `drive.file` scope the app can only ever see files it created — the
 * visible "AiSoul Backups" folder and the archives inside it.
 */
data class DriveArchive(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val createdAtMillis: Long,
)

class DriveClient(private val http: OkHttpClient, private val json: Json) {

    class DriveException(message: String) : Exception(message)

    suspend fun ensureBackupFolder(token: String): String {
        val query = "name = '$FOLDER_NAME' and mimeType = '$FOLDER_MIME' and trashed = false"
        val found = filesList(token, query).firstOrNull()
        if (found != null) return found.jsonObject["id"]!!.jsonPrimitive.content

        val metadata = """{"name":"$FOLDER_NAME","mimeType":"$FOLDER_MIME"}"""
        val response = execute(
            Request.Builder()
                .url("$API/files?fields=id")
                .header("Authorization", "Bearer $token")
                .post(metadata.toRequestBody(JSON_MIME))
                .build(),
        )
        return parse(response)["id"]!!.jsonPrimitive.content
    }

    suspend fun upload(token: String, folderId: String, name: String, bytes: ByteArray) {
        val metadata = json.encodeToString(
            JsonObject.serializer(),
            JsonObject(
                mapOf(
                    "name" to kotlinx.serialization.json.JsonPrimitive(name),
                    "parents" to JsonArray(listOf(kotlinx.serialization.json.JsonPrimitive(folderId))),
                    "appProperties" to JsonObject(mapOf("aisoul" to kotlinx.serialization.json.JsonPrimitive("backup"))),
                ),
            ),
        )
        val body: RequestBody = MultipartBody.Builder()
            .setType("multipart/related".toMediaType())
            .addPart(metadata.toRequestBody(JSON_MIME))
            .addPart(bytes.toRequestBody("application/octet-stream".toMediaType()))
            .build()
        execute(
            Request.Builder()
                .url("$UPLOAD_API/files?uploadType=multipart&fields=id")
                .header("Authorization", "Bearer $token")
                .post(body)
                .build(),
        )
    }

    suspend fun listArchives(token: String, folderId: String): List<DriveArchive> {
        val query = "'$folderId' in parents and trashed = false"
        return filesList(token, query, orderBy = "createdTime desc")
            .mapNotNull { element ->
                val file = element.jsonObject
                runCatching {
                    DriveArchive(
                        id = file["id"]!!.jsonPrimitive.content,
                        name = file["name"]!!.jsonPrimitive.content,
                        sizeBytes = file["size"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L,
                        createdAtMillis = parseRfc3339(file["createdTime"]?.jsonPrimitive?.content),
                    )
                }.getOrNull()
            }
    }

    suspend fun download(token: String, fileId: String): ByteArray = withContext(Dispatchers.IO) {
        http.newCall(
            Request.Builder()
                .url("$API/files/$fileId?alt=media")
                .header("Authorization", "Bearer $token")
                .build(),
        ).execute().use { response ->
            if (!response.isSuccessful) throw DriveException(driveError(response.code, response.body?.string()))
            response.body?.bytes() ?: throw DriveException("empty download")
        }
    }

    suspend fun delete(token: String, fileId: String) {
        execute(
            Request.Builder()
                .url("$API/files/$fileId")
                .header("Authorization", "Bearer $token")
                .delete()
                .build(),
        )
    }

    /** SPEC §9 — keep the last 10 archives, prune older. */
    suspend fun pruneBeyond(token: String, folderId: String, keep: Int = 10) {
        listArchives(token, folderId)
            .drop(keep)
            .forEach { runCatching { delete(token, it.id) } }
    }

    /** who is connected — shown in backup settings */
    suspend fun accountEmail(token: String): String? = runCatching {
        val response = execute(
            Request.Builder()
                .url("$API/about?fields=user(emailAddress)")
                .header("Authorization", "Bearer $token")
                .build(),
        )
        parse(response)["user"]?.jsonObject?.get("emailAddress")?.jsonPrimitive?.content
    }.getOrNull()

    private suspend fun filesList(token: String, query: String, orderBy: String? = null): JsonArray {
        val url = "$API/files".toHttpUrl().newBuilder()
            .addQueryParameter("q", query)
            .addQueryParameter("spaces", "drive")
            .addQueryParameter("fields", "files(id,name,size,createdTime)")
            .addQueryParameter("pageSize", "50")
            .apply { orderBy?.let { addQueryParameter("orderBy", it) } }
            .build()
        val response = execute(
            Request.Builder().url(url).header("Authorization", "Bearer $token").build(),
        )
        return parse(response)["files"]?.jsonArray ?: JsonArray(emptyList())
    }

    private suspend fun execute(request: Request): String = withContext(Dispatchers.IO) {
        http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw DriveException(driveError(response.code, body))
            body
        }
    }

    private fun parse(body: String): JsonObject =
        runCatching { json.parseToJsonElement(body).jsonObject }
            .getOrElse { throw DriveException("drive sent something unreadable") }

    private fun driveError(code: Int, body: String?): String {
        val message = runCatching {
            json.parseToJsonElement(body.orEmpty()).jsonObject["error"]
                ?.jsonObject?.get("message")?.jsonPrimitive?.content
        }.getOrNull()
        return when (code) {
            401 -> "drive authorization expired. reconnect in backup settings."
            403 -> message ?: "drive said no (403)."
            else -> message ?: "drive error $code"
        }
    }

    private fun parseRfc3339(value: String?): Long = runCatching {
        java.time.Instant.parse(value).toEpochMilli()
    }.getOrDefault(0L)

    private companion object {
        const val API = "https://www.googleapis.com/drive/v3"
        const val UPLOAD_API = "https://www.googleapis.com/upload/drive/v3"
        const val FOLDER_NAME = "AiSoul Backups"
        const val FOLDER_MIME = "application/vnd.google-apps.folder"
        val JSON_MIME = "application/json; charset=utf-8".toMediaType()
    }
}
