package tech.vasker.vector.data.db

import androidx.room.TypeConverter
import tech.vasker.vector.trip.RecordingMode
import tech.vasker.vector.trip.TripStatus

class TripConverters {
    @TypeConverter
    fun fromTripStatus(value: TripStatus): String = value.name

    @TypeConverter
    fun toTripStatus(value: String): TripStatus = TripStatus.valueOf(value)

    @TypeConverter
    fun fromRecordingMode(value: RecordingMode): String = value.name

    @TypeConverter
    fun toRecordingMode(value: String): RecordingMode = RecordingMode.valueOf(value)
}
