package com.aisoul.app.backup

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * SPEC §9 — one archive of the whole /harness tree, encrypted client-side,
 * to the user's own Drive folder or out through the system file picker.
 * API keys never travel (they live only in Keystore-wrapped DataStore).
 */
class BackupManager(
    context: Context,
    private val harnessRoot: File,
    private val archiver: Archiver,
    private val crypto: BackupCrypto,
    private val drive: DriveClient,
    private val settings: BackupSettings,
) {

    class NoPassphraseException : Exception("set a backup passphrase first")

    private val appContext = context.applicationContext

    fun archiveName(): String =
        "aisoul-backup-${SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())}.zip.enc"

    suspend fun buildEncryptedArchive(): ByteArray = withContext(Dispatchers.Default) {
        val passphrase = settings.passphrase() ?: throw NoPassphraseException()
        crypto.encrypt(archiver.zipTree(harnessRoot), passphrase)
    }

    /** archive → upload → prune beyond 10. Returns a short human summary. */
    suspend fun backupToDrive(token: String): String {
        val blob = buildEncryptedArchive()
        val folderId = drive.ensureBackupFolder(token)
        drive.upload(token, folderId, archiveName(), blob)
        drive.pruneBeyond(token, folderId)
        val summary = "backed up ${blob.size / 1024} kb"
        settings.recordBackup(summary, succeeded = true)
        return summary
    }

    suspend fun listDriveArchives(token: String): List<DriveArchive> =
        drive.listArchives(token, drive.ensureBackupFolder(token))

    suspend fun downloadArchive(token: String, id: String): ByteArray = drive.download(token, id)

    suspend fun accountEmail(token: String): String? = drive.accountEmail(token)

    // ---- SAF fallback (SPEC §9) — same archive format, no Google account ----

    suspend fun exportTo(uri: Uri) = withContext(Dispatchers.IO) {
        val blob = buildEncryptedArchive()
        appContext.contentResolver.openOutputStream(uri)?.use { it.write(blob) }
            ?: throw IllegalStateException("couldn't open the chosen location")
    }

    suspend fun readBlob(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        appContext.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalStateException("couldn't read the chosen file")
    }

    // ---- restore ----

    suspend fun decrypt(blob: ByteArray, passphrase: String): ByteArray =
        withContext(Dispatchers.Default) { crypto.decrypt(blob, passphrase) }

    fun preview(zipBytes: ByteArray): ArchivePreview = archiver.preview(zipBytes)

    /** always behind the typed confirmation in the UI (SPEC §6: restore asks, in every mode) */
    suspend fun restore(zipBytes: ByteArray) = withContext(Dispatchers.IO) {
        archiver.restore(harnessRoot, zipBytes)
    }
}
