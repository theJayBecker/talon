package tech.vasker.vector.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TripDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(trip: TripEntity)

    @Query("SELECT * FROM trips WHERE id = :id")
    suspend fun getById(id: String): TripEntity?

    @Query("SELECT * FROM trips ORDER BY startTime DESC")
    fun getAllOrderByStartTimeDesc(): Flow<List<TripEntity>>

    @Update
    suspend fun update(trip: TripEntity)

    @Query("UPDATE trips SET notes = :notes WHERE id = :id")
    suspend fun updateNotes(id: String, notes: String?)
}
