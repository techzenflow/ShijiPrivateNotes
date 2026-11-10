package com.shiji.notes.data

import com.shiji.notes.security.Security
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class BackupData(
    val notes: List<NoteEntity>,
    val versions: List<NoteVersionEntity>,
    val vault: List<VaultItemEntity>
)

/** Password-protected portable backup. Imported rows are re-encrypted with the current master key. */
object BackupCodec {
    private val magic = "SHIJI-BK1".toByteArray(StandardCharsets.US_ASCII)
    private const val SALT_SIZE = 16
    private const val IV_SIZE = 12
    private const val ITERATIONS = 350_000
    private const val MAX_BACKUP_BYTES = 100 * 1024 * 1024

    fun create(notes: List<NoteEntity>, versions: List<NoteVersionEntity>, vault: List<VaultItemEntity>,
               masterKey: ByteArray, backupPassword: CharArray): ByteArray {
        require(backupPassword.size >= 10) { "备份密码至少需要 10 位" }
        val payload = JSONObject().put("format", 1).put("createdAt", System.currentTimeMillis())
            .put("notes", JSONArray().apply {
                notes.forEach { n ->
                    put(JSONObject().put("id", n.id)
                        .put("title", Security.decryptNoteField(masterKey, n.title))
                        .put("body", Security.decryptNoteField(masterKey, n.body))
                        .put("createdAt", n.createdAt).put("updatedAt", n.updatedAt)
                        .put("deletedAt", n.deletedAt ?: JSONObject.NULL)
                        .put("isPinned", n.isPinned).put("isFavorite", n.isFavorite))
                }
            })
            .put("versions", JSONArray().apply {
                versions.forEach { v ->
                    put(JSONObject().put("id", v.id).put("noteId", v.noteId).put("versionNo", v.versionNo)
                        .put("title", Security.decryptNoteField(masterKey, v.title))
                        .put("body", Security.decryptNoteField(masterKey, v.body))
                        .put("savedAt", v.savedAt).put("reason", v.reason))
                }
            })
            .put("vault", JSONArray().apply {
                vault.forEach { v ->
                    put(JSONObject().put("id", v.id)
                        .put("label", Security.decryptNoteField(masterKey, v.label))
                        .put("site", Security.decryptNoteField(masterKey, v.site))
                        .put("username", Security.decrypt(masterKey, v.usernameCipher))
                        .put("password", Security.decrypt(masterKey, v.passwordCipher))
                        .put("notes", Security.decrypt(masterKey, v.notesCipher))
                        .put("createdAt", v.createdAt).put("updatedAt", v.updatedAt))
                }
            })
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val iv = ByteArray(IV_SIZE).also { SecureRandom().nextBytes(it) }
        val key = derive(backupPassword, salt)
        return try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(key, "AES"), GCMParameterSpec(128, iv))
            magic + salt + iv + cipher.doFinal(payload.toString().toByteArray(StandardCharsets.UTF_8))
        } finally {
            key.fill(0)
            backupPassword.fill('\u0000')
        }
    }

    fun restore(bytes: ByteArray, backupPassword: CharArray, masterKey: ByteArray): BackupData {
        require(bytes.size in (magic.size + SALT_SIZE + IV_SIZE + 16)..MAX_BACKUP_BYTES) { "备份文件大小无效" }
        require(bytes.copyOfRange(0, magic.size).contentEquals(magic)) { "不是有效的时迹加密备份文件" }
        val saltStart = magic.size
        val ivStart = saltStart + SALT_SIZE
        val cipherStart = ivStart + IV_SIZE
        val key = derive(backupPassword, bytes.copyOfRange(saltStart, ivStart))
        val plain = try {
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(key, "AES"),
                GCMParameterSpec(128, bytes.copyOfRange(ivStart, cipherStart)))
            cipher.doFinal(bytes.copyOfRange(cipherStart, bytes.size))
        } catch (_: Exception) {
            throw IllegalArgumentException("备份密码错误或文件已损坏")
        } finally {
            key.fill(0)
            backupPassword.fill('\u0000')
        }
        val root = JSONObject(String(plain, StandardCharsets.UTF_8))
        require(root.optInt("format", -1) == 1) { "暂不支持此备份格式" }
        val notesJson = root.getJSONArray("notes")
        val versionsJson = root.getJSONArray("versions")
        val vaultJson = root.getJSONArray("vault")
        require(notesJson.length() <= 100_000 && versionsJson.length() <= 500_000 && vaultJson.length() <= 100_000) {
            "备份记录数量超出限制"
        }
        val notes = (0 until notesJson.length()).map { i ->
            val n = notesJson.getJSONObject(i)
            NoteEntity(id=n.getLong("id"), title=Security.encryptNoteField(masterKey,n.getString("title")),
                body=Security.encryptNoteField(masterKey,n.getString("body")), createdAt=n.getLong("createdAt"),
                updatedAt=n.getLong("updatedAt"), deletedAt=if(n.isNull("deletedAt")) null else n.getLong("deletedAt"),
                isPinned=n.optBoolean("isPinned",false), isFavorite=n.optBoolean("isFavorite",false))
        }
        val versions = (0 until versionsJson.length()).map { i ->
            val v = versionsJson.getJSONObject(i)
            NoteVersionEntity(id=v.getLong("id"), noteId=v.getLong("noteId"), versionNo=v.getInt("versionNo"),
                title=Security.encryptNoteField(masterKey,v.getString("title")),
                body=Security.encryptNoteField(masterKey,v.getString("body")),
                savedAt=v.getLong("savedAt"), reason=v.getString("reason"))
        }
        val vault = (0 until vaultJson.length()).map { i ->
            val v = vaultJson.getJSONObject(i)
            VaultItemEntity(id=v.getLong("id"), label=Security.encryptNoteField(masterKey,v.getString("label")),
                site=Security.encryptNoteField(masterKey,v.getString("site")),
                usernameCipher=Security.encrypt(masterKey,v.getString("username")),
                passwordCipher=Security.encrypt(masterKey,v.getString("password")),
                notesCipher=Security.encrypt(masterKey,v.getString("notes")),
                createdAt=v.getLong("createdAt"), updatedAt=v.getLong("updatedAt"))
        }
        val noteIds = notes.map { it.id }.toSet()
        require(versions.all { it.noteId in noteIds }) { "备份中存在无法关联的历史版本" }
        return BackupData(notes, versions, vault)
    }

    private fun derive(password: CharArray, salt: ByteArray): ByteArray {
        val spec = PBEKeySpec(password, salt, ITERATIONS, 256)
        return try { SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
        finally { spec.clearPassword() }
    }
}
