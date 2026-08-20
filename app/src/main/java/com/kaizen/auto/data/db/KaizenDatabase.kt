package com.kaizen.auto.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        PatternMemory::class,
        HealingEvent::class,
        ScreenObservation::class,
        RunLog::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class KaizenDatabase : RoomDatabase() {

    abstract fun patternMemoryDao(): PatternMemoryDao
    abstract fun healingEventDao(): HealingEventDao
    abstract fun screenObservationDao(): ScreenObservationDao
    abstract fun runLogDao(): RunLogDao

    companion object {
        @Volatile
        private var INSTANCE: KaizenDatabase? = null

        fun get(context: Context): KaizenDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    KaizenDatabase::class.java,
                    "kaizen.db",
                )
                    // O runtime Lua roda numa thread própria e precisa de acesso
                    // síncrono ao banco; as queries são pequenas e indexadas.
                    .allowMainThreadQueries()
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { INSTANCE = it }
            }
    }
}
