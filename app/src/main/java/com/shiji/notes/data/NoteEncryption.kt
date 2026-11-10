package com.shiji.notes.data

import com.shiji.notes.security.Security

fun NoteEntity.encryptFields(key: ByteArray) = copy(
    title = Security.encryptNoteField(key, title),
    body = Security.encryptNoteField(key, body),
    category = Security.encryptNoteField(key, category),
    tags = Security.encryptNoteField(key, tags)
)
fun NoteEntity.decryptFields(key: ByteArray) = copy(
    title = Security.decryptNoteField(key, title),
    body = Security.decryptNoteField(key, body),
    category = Security.decryptNoteField(key, category),
    tags = Security.decryptNoteField(key, tags)
)
fun NoteVersionEntity.encryptFields(key: ByteArray) = copy(
    title = Security.encryptNoteField(key, title),
    body = Security.encryptNoteField(key, body),
    category = Security.encryptNoteField(key, category),
    tags = Security.encryptNoteField(key, tags)
)
fun NoteVersionEntity.decryptFields(key: ByteArray) = copy(
    title = Security.decryptNoteField(key, title),
    body = Security.decryptNoteField(key, body),
    category = Security.decryptNoteField(key, category),
    tags = Security.decryptNoteField(key, tags)
)

fun NoteEntity.matchesQuery(query: String): Boolean {
    val normalized = query.trim()
    if (normalized.isEmpty()) return true
    return title.contains(normalized, ignoreCase = true) || body.contains(normalized, ignoreCase = true) || category.contains(normalized, ignoreCase = true) || tags.contains(normalized, ignoreCase = true)
}
