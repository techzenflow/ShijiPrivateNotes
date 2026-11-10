package com.shiji.notes.data

import com.shiji.notes.security.Security
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupCodecTest {
    private val sourceKey = ByteArray(32) { (it * 3 + 1).toByte() }
    private val targetKey = ByteArray(32) { (it * 5 + 7).toByte() }

    @Test fun encryptedBackupRestoresAllRecordTypesUnderNewMasterKey() {
        val note = NoteEntity(id=11, title="private title", body="secret body", createdAt=10, updatedAt=20,
            deletedAt=30, isPinned=true, isFavorite=true).encryptFields(sourceKey)
        val version = NoteVersionEntity(id=21, noteId=11, versionNo=2, title="old title", body="old secret",
            savedAt=15, reason="snapshot").encryptFields(sourceKey)
        val vault = VaultItemEntity(id=31,
            label=Security.encryptNoteField(sourceKey,"mail"), site=Security.encryptNoteField(sourceKey,"example.test"),
            usernameCipher=Security.encrypt(sourceKey,"private-user"), passwordCipher=Security.encrypt(sourceKey,"private-pass"),
            notesCipher=Security.encrypt(sourceKey,"vault note"), createdAt=12, updatedAt=22)

        val bytes = BackupCodec.create(listOf(note), listOf(version), listOf(vault), sourceKey, "backup-pass-123".toCharArray())
        val raw = String(bytes, Charsets.ISO_8859_1)
        assertFalse(raw.contains("secret body"))
        assertFalse(raw.contains("private-pass"))

        val restored = BackupCodec.restore(bytes, "backup-pass-123".toCharArray(), targetKey)
        assertEquals(note.decryptFields(sourceKey), restored.notes.single().decryptFields(targetKey))
        assertEquals(version.decryptFields(sourceKey), restored.versions.single().decryptFields(targetKey))
        val restoredVault = restored.vault.single()
        assertEquals("mail", Security.decryptNoteField(targetKey, restoredVault.label))
        assertEquals("example.test", Security.decryptNoteField(targetKey, restoredVault.site))
        assertEquals("private-user", Security.decrypt(targetKey, restoredVault.usernameCipher))
        assertEquals("private-pass", Security.decrypt(targetKey, restoredVault.passwordCipher))
        assertEquals("vault note", Security.decrypt(targetKey, restoredVault.notesCipher))
    }

    @Test fun wrongBackupPasswordIsRejected() {
        val bytes = BackupCodec.create(emptyList(), emptyList(), emptyList(), sourceKey, "backup-pass-123".toCharArray())
        org.junit.Assert.assertThrows(IllegalArgumentException::class.java) {
            BackupCodec.restore(bytes, "incorrect-password".toCharArray(), targetKey)
        }
    }
}
