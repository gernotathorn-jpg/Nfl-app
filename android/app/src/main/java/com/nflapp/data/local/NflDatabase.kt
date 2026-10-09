package com.nflapp.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [
        GameEntity::class,
        TeamEntity::class,
        TeamSeasonEntity::class,
        EloPointEntity::class,
        HeadToHeadEntity::class,
        SyncMetaEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class NflDatabase : RoomDatabase() {
    abstract fun games(): GameDao
    abstract fun teams(): TeamDao
    abstract fun syncMeta(): SyncMetaDao

    companion object {
        fun create(context: Context): NflDatabase =
            Room.databaseBuilder(context, NflDatabase::class.java, "nfl.db")
                // Everything in here is a cache of the remote JSON and can be re-downloaded.
                .fallbackToDestructiveMigration()
                .build()
    }
}
