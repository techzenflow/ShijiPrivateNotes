package com.shiji.notes.data

import androidx.room.*
import androidx.room.migration.Migration

@Entity(tableName = "notes")
data class NoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val body: String,
    val createdAt: Long,
    val updatedAt: Long,
    val deletedAt: Long? = null,
    val isPinned: Boolean = false,
    val isFavorite: Boolean = false
)

@Entity(tableName = "note_versions", indices = [Index("noteId"), Index("savedAt")])
data class NoteVersionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val noteId: Long,
    val versionNo: Int,
    val title: String,
    val body: String,
    val savedAt: Long,
    val reason: String
)

@Entity(tableName = "vault_items")
data class VaultItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val site: String,
    val usernameCipher: String,
    val passwordCipher: String,
    val notesCipher: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Dao
interface NotesDao {
    @Query("SELECT * FROM notes WHERE deletedAt IS NULL ORDER BY isPinned DESC, updatedAt DESC")
    fun observeNotes(): kotlinx.coroutines.flow.Flow<List<NoteEntity>>
    @Query("SELECT * FROM notes WHERE id=:id") suspend fun getNote(id: Long): NoteEntity?
    @Query("SELECT * FROM notes") suspend fun getAllNotes(): List<NoteEntity>
    @Query("SELECT * FROM notes WHERE deletedAt IS NOT NULL ORDER BY deletedAt DESC") fun observeTrash(): kotlinx.coroutines.flow.Flow<List<NoteEntity>>
    @Insert suspend fun insertNote(note: NoteEntity): Long
    @Update suspend fun updateNote(note: NoteEntity)
    @Delete suspend fun deleteNote(note: NoteEntity)
    @Query("DELETE FROM note_versions WHERE noteId=:id") suspend fun deleteVersions(id: Long)
    @Query("SELECT * FROM note_versions WHERE noteId=:id ORDER BY versionNo DESC")
    fun observeVersions(id: Long): kotlinx.coroutines.flow.Flow<List<NoteVersionEntity>>
    @Query("SELECT * FROM note_versions WHERE noteId=:id ORDER BY versionNo DESC")
    suspend fun getVersions(id: Long): List<NoteVersionEntity>
    @Query("SELECT * FROM note_versions WHERE id=:id") suspend fun getVersion(id: Long): NoteVersionEntity?
    @Insert suspend fun insertVersion(version: NoteVersionEntity): Long
    @Update suspend fun updateVersion(version: NoteVersionEntity)
    @Query("SELECT * FROM note_versions") suspend fun getAllVersions(): List<NoteVersionEntity>
    @Query("SELECT COALESCE(MAX(versionNo),0) FROM note_versions WHERE noteId=:id")
    suspend fun maxVersion(id: Long): Int
    @Query("SELECT * FROM vault_items ORDER BY updatedAt DESC")
    fun observeVault(): kotlinx.coroutines.flow.Flow<List<VaultItemEntity>>
    @Insert suspend fun insertVault(item: VaultItemEntity): Long
    @Update suspend fun updateVault(item: VaultItemEntity)
    @Delete suspend fun deleteVault(item: VaultItemEntity)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertNotes(items: List<NoteEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertVersions(items: List<NoteVersionEntity>)
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertVaultItems(items: List<VaultItemEntity>)
    @Query("DELETE FROM notes") suspend fun clearNotes()
    @Query("DELETE FROM note_versions") suspend fun clearVersions()
    @Query("DELETE FROM vault_items") suspend fun clearVault()
    @Transaction
    suspend fun replaceAllData(notes: List<NoteEntity>, versions: List<NoteVersionEntity>, vault: List<VaultItemEntity>) {
        clearVersions()
        clearNotes()
        clearVault()
        if (notes.isNotEmpty()) insertNotes(notes)
        if (versions.isNotEmpty()) insertVersions(versions)
        if (vault.isNotEmpty()) insertVaultItems(vault)
    }
}

@Database(entities = [NoteEntity::class, NoteVersionEntity::class, VaultItemEntity::class], version = 3, exportSchema = false)
abstract class ShijiDatabase : RoomDatabase() {
    abstract fun dao(): NotesDao
    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN deletedAt INTEGER")
            }
        }
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE notes ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE notes ADD COLUMN isFavorite INTEGER NOT NULL DEFAULT 0")
            }
        }
        @Volatile private var INSTANCE: ShijiDatabase? = null
        fun get(context: android.content.Context): ShijiDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(context.applicationContext, ShijiDatabase::class.java, "shiji_local.db")
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build().also { INSTANCE = it }
            }
    }
}
