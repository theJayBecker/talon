package tech.vasker.vector.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(
    entities = [TripEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(TripConverters::class)
abstract class TalonDatabase : RoomDatabase() {
    abstract fun tripDao(): TripDao

    companion object {
        @Volatile
        private var INSTANCE: TalonDatabase? = null

        fun get(context: Context): TalonDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    TalonDatabase::class.java,
                    "talon_db",
                ).build().also { INSTANCE = it }
            }
        }
    }
}
