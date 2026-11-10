package com.shiji.notes.data

import com.shiji.notes.security.Security

fun NoteEntity.encryptFields(key: ByteArray) = copy(
    title = Security.encryptNoteField(key, title),
    body = Security.encryptNoteField(key, body)
)
fun NoteEntity.decryptFields(key: ByteArray) = copy(
    title = Security.decryptNoteField(key, title),
    body = Security.decryptNoteField(key, body)
)
fun NoteVersionEntity.encryptFields(key: ByteArray) = copy(
    title = Security.encryptNoteField(key, title),
    body = Security.encryptNoteField(key, body)
)
fun NoteVersionEntity.decryptFields(key: ByteArray) = copy(
    title = Security.decryptNoteField(key, title),
    body = Security.decryptNoteField(key, body)
)

fun NoteEntity.matchesQuery(query: String): Boolean {
    val normalized = query.trim()
    if (normalized.isEmpty()) return true
    return title.contains(normalized, ignoreCase = true) || body.contains(normalized, ignoreCase = true)
}
