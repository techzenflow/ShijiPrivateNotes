package com.shiji.notes.security

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import com.shiji.notes.data.NoteEntity
import com.shiji.notes.data.NoteVersionEntity
import com.shiji.notes.data.encryptFields
import com.shiji.notes.data.decryptFields

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SecurityTest {
    private val key = ByteArray(32) { (it * 7 + 3).toByte() }

    @Test fun encryptDecryptRoundTrip() {
        val plain = "时迹私人笔记\nprivate text 🔐"
        val encrypted = Security.encrypt(key, plain)
        assertNotEquals(plain, encrypted)
        assertEquals(plain, Security.decrypt(key, encrypted))
    }

    @Test fun samePlaintextUsesDifferentRandomIv() {
        val first = Security.encrypt(key, "same")
        val second = Security.encrypt(key, "same")
        assertNotEquals(first, second)
        assertEquals("same", Security.decrypt(key, first))
        assertEquals("same", Security.decrypt(key, second))
    }

    @Test fun wrongKeyCannotDecrypt() {
        val encrypted = Security.encrypt(key, "secret")
        val wrongKey = ByteArray(32) { 9 }
        assertThrows(Exception::class.java) { Security.decrypt(wrongKey, encrypted) }
    }

    @Test fun encryptedFieldIsIdempotentAndLegacyPlaintextReadable() {
        val field = Security.encryptNoteField(key, "note body")
        assertTrue(field.startsWith("SHIJI1:"))
        assertEquals(field, Security.encryptNoteField(key, field))
        assertEquals("note body", Security.decryptNoteField(key, field))
        assertEquals("legacy", Security.decryptNoteField(key, "legacy"))
    }

    @Test fun noteAndHistoryFieldsAreEncryptedAtRestAndRoundTrip() {
        val note = NoteEntity(id=4, title="private title", body="private body", createdAt=1, updatedAt=2)
        val storedNote = note.encryptFields(key)
        assertTrue(storedNote.title.startsWith("SHIJI1:"))
        assertTrue(storedNote.body.startsWith("SHIJI1:"))
        assertFalse(storedNote.title.contains("private"))
        assertEquals(note, storedNote.decryptFields(key))

        val version = NoteVersionEntity(id=9, noteId=4, versionNo=2, title="old title", body="old body", savedAt=3, reason="snapshot")
        val storedVersion = version.encryptFields(key)
        assertTrue(storedVersion.body.startsWith("SHIJI1:"))
        assertFalse(storedVersion.body.contains("old body"))
        assertEquals(version, storedVersion.decryptFields(key))
    }

    @Test fun masterPasswordSetupAndUnlock() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        context.getSharedPreferences("shiji_security", Context.MODE_PRIVATE).edit().clear().commit()
        Security.setupMaster(context, "correct horse".toCharArray())
        assertNotNull(Security.unlock(context, "correct horse".toCharArray()))
        assertNull(Security.unlock(context, "wrong password".toCharArray()))
    }
}
