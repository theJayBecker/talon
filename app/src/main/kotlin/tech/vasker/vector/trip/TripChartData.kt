package tech.vasker.vector.trip

import java.util.Calendar

/** Start of the past-30-days window (30 days ago from now, 00:00:00.000) in the default timezone. */
fun past30DaysStartMillis(): Long {
    val cal = Calendar.getInstance()
    cal.add(Calendar.DAY_OF_YEAR, -30)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.timeInMillis
}

/** End of the past-30-days window (now). */
fun past30DaysEndMillis(): Long = System.currentTimeMillis()

/**
 * Trips that ended (or started if no end) in the past 30 days, sorted by end time ascending.
 */
fun tripsInLast30Days(trips: List<TripSummary>): List<TripSummary> {
    val start = past30DaysStartMillis()
    val end = past30DaysEndMillis()
    return trips
        .filter { trip ->
            val t = trip.endTime ?: trip.startTime
            t in start..end
        }
        .sortedBy { it.endTime ?: it.startTime }
}

/** Trip end timestamps (or start if no end) for the given list, in same order. Used for x-axis labels. */
fun tripEndDatesForAxis(trips: List<TripSummary>): List<Long> =
    trips.map { it.endTime ?: it.startTime }

/**
 * Chart series for the Fuel tab: one point per trip. X = index (0..n-1), Y = fuel gallons.
 * Trips with null [TripSummary.fuelBurnedGal] are treated as 0 so they still appear on the chart.
 */
fun fuelSeriesForChart(trips: List<TripSummary>): Pair<List<Float>, List<Float>> {
    val x = trips.indices.map { it.toFloat() }
    val y = trips.map { (it.fuelBurnedGal ?: 0.0).toFloat() }
    return x to y
}

/**
 * Chart series for the Miles tab: one point per trip. X = index (0..n-1), Y = distance miles.
 */
fun milesSeriesForChart(trips: List<TripSummary>): Pair<List<Float>, List<Float>> {
    val x = trips.indices.map { it.toFloat() }
    val y = trips.map { it.distanceMi.toFloat() }
    return x to y
}

/**
 * Chart series for the MPG tab: one point per trip. X = index (0..n-1), Y = miles per gallon.
 * Trips with null or zero [TripSummary.fuelBurnedGal] are treated as 0 MPG so they still appear.
 */
fun mpgSeriesForChart(trips: List<TripSummary>): Pair<List<Float>, List<Float>> {
    val x = trips.indices.map { it.toFloat() }
    val y = trips.map { trip ->
        val gal = trip.fuelBurnedGal ?: 0.0
        if (gal > 0) (trip.distanceMi / gal).toFloat() else 0f
    }
    return x to y
}

/** Total fuel burned (gal) in the given trip list. Null fuel counts as 0. */
fun totalFuelBurnedGal(trips: List<TripSummary>): Double =
    trips.sumOf { it.fuelBurnedGal ?: 0.0 }

/** Total distance (mi) in the given trip list. */
fun totalMiles(trips: List<TripSummary>): Double =
    trips.sumOf { it.distanceMi }

/** Average MPG over the period: total miles / total gallons. Null if no fuel burned. */
fun averageMpg(trips: List<TripSummary>): Double? {
    val fuel = totalFuelBurnedGal(trips)
    if (fuel <= 0) return null
    return totalMiles(trips) / fuel
}
