package com.srini.wheresthatphoto.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        PhotoEntity::class,
        CaptionEntity::class,
        ClipEmbeddingEntity::class,
        CaptionEmbeddingEntity::class,
        IdentityEntity::class,
        DetectedFaceEntity::class
    ],
    version = 2,
    exportSchema = false
)
@TypeConverters(RoomConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun identityDao(): IdentityDao

    companion object {
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE photos ADD COLUMN facesIndexedAt INTEGER")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS identities (
                        id TEXT NOT NULL PRIMARY KEY,
                        kind TEXT NOT NULL,
                        displayName TEXT,
                        representativeFaceId TEXT,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS detected_faces (
                        id TEXT NOT NULL PRIMARY KEY,
                        photoId TEXT NOT NULL,
                        identityId TEXT NOT NULL,
                        kind TEXT NOT NULL,
                        `left` REAL NOT NULL,
                        top REAL NOT NULL,
                        `right` REAL NOT NULL,
                        bottom REAL NOT NULL,
                        embedding TEXT NOT NULL,
                        thumbnailPath TEXT NOT NULL,
                        detectedAt INTEGER NOT NULL,
                        FOREIGN KEY(photoId) REFERENCES photos(id) ON DELETE CASCADE,
                        FOREIGN KEY(identityId) REFERENCES identities(id) ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_detected_faces_photoId ON detected_faces(photoId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_detected_faces_identityId ON detected_faces(identityId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_detected_faces_kind ON detected_faces(kind)")
            }
        }

        fun create(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "wheres_that_photo.db"
            )
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}
