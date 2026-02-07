# Phase 3 — Implementation Plan (Android)

**Objective:** Introduce structured persistence with Room without breaking existing trips. Phase 2 file format is ingested and migrated into Room; Room becomes the active query layer. Files remain export artifacts (generated from Room + samples when needed).

**Prerequisites:** Phase 2 complete ([phase2-implementation-plan.md](phase2-implementation-plan.md)) — trips recorded to file-backed directories (`trip.json`, `samples.csv`, `trip.md`).

---

## Policies (from roadmap)

- **Migration:** Phase 3 **ingests and migrates Phase 2 file format** into Room. Existing trip directories are read and inserted into the database; no duplicate source of truth for the list.
- **Single source of truth:** Trip list and all trip queries come from Room. Files (trip.json, trip.md, samples.csv) are either migration input or export output, not the authority for "what trips exist."
- **Trip shape:** Metadata, stats, and sample reference aligned with talon-ui `trip-types.ts` for consistent export and future sync.
- **Raw samples:** Remain file-based (no row-per-sample in Room) to avoid row explosion. Aggregated summary data lives in the Trip row.

---

## Locked decisions (Phase 3)

- **Trips table:** One row per trip. Columns: id, startTime, endTime, status, recordingMode, vehicle (nullable), endReason (nullable), notes (nullable), aggregated stats (durationSec, distanceMi, fuelStartPct, fuelEndPct, fuelUsedPct, avgSpeedMph, maxSpeedMph, avgRpm, maxRpm, avgCoolantF, maxCoolantF, maxLoadPct, idleTimeSec), and **samples_path** (explicit path to samples file). Storing the path explicitly avoids fragile conventions when adding compression, rotation, or alternate formats later.
- **Samples storage:** Raw samples stay in a file per trip (e.g. `trips/<trip_id>/samples.csv`). Room stores **samples_path** in the Trip row. No `samples` table with one row per sample.
- **Migration strategy:** Run migration **automatically on app startup**. **Per-trip idempotent:** scan Phase 2 trip folders; for each trip, if Room already has that `trip_id`, skip; otherwise import. No global "migration done" flag (trips may be added later from older app or restored folders). Best-effort, never crash: malformed `trip.json` → skip and log; missing fields → insert what we can, set `status = partial`, `end_reason = migration_incomplete`; missing `samples.csv` → insert metadata only, mark partial.
- **New trips (post–Phase 3):** When a trip ends, write aggregated data to Room; write raw samples to `trips/<trip_id>/samples.csv`; **keep writing `trip.json`** for every new trip (durable artifact, escape hatch for debugging/recovery, prevents catastrophic loss if Room corrupts). Room is the query layer; trip.json remains the file-based backup.
- **Export:** Generate exports to **temp files** and share via intent. Markdown (and optional `trip.json`) built from Room row + samples file. No dedicated export folder in Phase 3 (can add "Save copy" in Phase 6).
- **Trip list:** Order by **startTime DESC**, **full list**, **no filters** (do not hide partial trips; they are valuable for debugging). TripsScreen and all "list trips" use Room exclusively via repository; no directory scanning for the list.
- **Repository design:** Single **TripRepository** interface; two implementations: **FileTripRepository** (Phase 2, file-based) and **RoomTripRepository** (Phase 3, Room-backed). After migration, switch app usage to **RoomTripRepository**; keep FileTripRepository only for migration/import/export support, not as primary. Avoids parallel caller logic and dual source of truth.

---

## Work Required

### 1) Room schema and DAOs (3.1)

**Goal:** Define Room database, one Trip entity (metadata + stats in one row), and DAOs for insert/update/query. No samples table.

- **Database:** Single Room database (e.g. `TalonDatabase`) with one table for trips. Version 1. No migrations yet (Phase 2 has no DB).
- **Trip entity:** Map to Trip row: id (UUID, primary key), startTime, endTime (nullable), status, recordingMode, vehicle (nullable), endReason (nullable), notes (nullable), durationSec, distanceMi, fuelStartPct, fuelEndPct, fuelUsedPct, avgSpeedMph, maxSpeedMph, avgRpm, maxRpm, avgCoolantF, maxCoolantF, maxLoadPct, idleTimeSec, **samplesPath** (nullable String — explicit path to samples file, e.g. `trips/<id>/samples.csv`).
- **DAO:** Insert trip, update trip (e.g. notes), get by id, get all ordered by startTime descending. Delete trip if needed for Phase 3 (optional).
- **Type conversions:** Store startTime/endTime as Long (millis). Status/recordingMode as String or enum.

**Exit (3.1):** Room DB and Trip entity and DAO in place; can insert and query trips.

---

### 2) Migrate Phase 2 trips into Room (3.2)

**Goal:** Existing trip directories (Phase 2 format) are ingested into Room so the list shows them and there is no dual source of truth.

- **Migration trigger:** Run **automatically on app startup**. No global "migration done" flag — migration is **per-trip idempotent** so it is safe and future-proof (e.g. older app writes more trips later, or user restores folders).
- **Migration step:** Scan the Phase 2 trips root (e.g. `getExternalFilesDir(null)/trips/` or same path used in Phase 2). For each subdirectory that looks like a trip (contains `trip.json`):
  - If Room already has a row with this trip `id`, **skip** (no re-ingest).
  - Otherwise read and parse `trip.json`, map metadata + stats to Room Trip entity, set **samples_path** (e.g. `trips/<id>/samples.csv` if file exists), insert into Room.
- **Best-effort, never crash:**
  - **Malformed `trip.json`:** Skip that trip, log error; do not crash. Leave directory as-is for manual inspection.
  - **Parse OK but missing fields:** Insert what we can; set `status = partial`, `end_reason = migration_incomplete` (or similar); set samples_path if `samples.csv` exists.
  - **Missing `samples.csv`:** Insert trip metadata/stats only; set `samples_path` to null or omit; mark `status = partial`.
- **After migration:** Trip list from Room includes all migrated trips. No code path should build the list by scanning directories (except inside the migration step itself).

**Exit (3.2):** All existing Phase 2 trips appear in Room; migration is per-trip idempotent and resilient (best-effort, no crash).

---

### 3) Single source of truth — repository and write path (3.3)

**Goal:** One place that "owns" trip data: Room for list and summary; samples file for raw samples. Single TripRepository interface; app uses Room implementation after migration. New trips write to Room + samples file + trip.json; export generates files from Room + samples to temp and shares via intent.

- **TripRepository interface:** Define a single interface (e.g. list trips, get trip by id, insert/update trip, export trip). Two implementations:
  - **FileTripRepository:** Phase 2 style (scan directories, read trip.json). Used only for migration/import/export support, not as primary list source.
  - **RoomTripRepository:** Reads/writes Room; exposes Flow of trips (all trips, ordered by startTime DESC); getTrip(id) from Room; when full trip with samples is needed, load row and read samples from file using **samples_path**.
- **Switch after migration:** Run migration on startup; then app uses **RoomTripRepository** for all list and get operations. No parallel caller logic; no dual source of truth.
- **New trip save (from recorder):** When a trip ends: write samples to `trips/<id>/samples.csv`; **keep writing `trip.json`** (durable artifact, escape hatch); insert or update the Trip row in Room (metadata + stats + samples_path). Room is query layer; trip.json remains file-based backup.
- **List trips:** Full list, ordered by startTime DESC, no filters (include partial trips). Repository returns data from Room only; no directory scanning for the list.
- **Export:** Build trip from Room row + samples file (using samples_path); generate `trip.md` (and optionally `trip.json`) per Phase 2 / talon-ui contract. Write to **temp files** (e.g. cache dir), then **share via intent**. No dedicated export folder in Phase 3.

**Exit (3.3):** No dual source of truth; single TripRepository interface with Room implementation as primary; trip list from Room only; new trips persist to Room + samples file + trip.json; export is temp + share intent.

---

### 4) Wire UI and remove file-based list (3.4)

**Goal:** TripsScreen and any trip list UI use the TripRepository abstraction backed by Room (RoomTripRepository). Remove or refactor Phase 2 code that builds the list by scanning trip directories.

- **TripsScreen:** Consume trips from repository (e.g. `repository.tripsFlow()` — Room-backed). Same UI as Phase 2 (list of summaries): full list, startTime DESC, no filters (partial trips shown). Data source is Room via repository.
- **Trip detail (if any):** If Phase 3 adds a simple detail screen, load trip by id from repository (Room + samples file when needed).
- **Recording flow:** When recording ends, save path writes trip.json, samples file, and Room row so the new trip appears in the list on next load.
- **Cleanup:** Remove any "list trips by scanning directories" logic from app code; migration is the only place that reads Phase 2 directories for ingestion. Callers use TripRepository only; implementation is RoomTripRepository.

**Exit (3.4):** Trip list is from Room only via TripRepository; UI and recording flow use the new persistence layer.

---

## Exit Criteria Summary

| Block | Criterion |
|-------|-----------|
| 3.1 | Room database, Trip entity, DAO; insert and query trips |
| 3.2 | Existing Phase 2 trips migrate cleanly into Room; per-trip idempotent migration; best-effort, no crash |
| 3.3 | No dual source of truth; single TripRepository (Room impl); list from Room; new trips write to Room + samples + trip.json; export temp + share intent |
| 3.4 | Trip list loads exclusively from Room via TripRepository; UI and recorder use new persistence layer |
| **Phase 3** | Existing trips migrate cleanly; no dual source of truth; trip list loads exclusively from Room |

---

## Suggested structure (optional)

- **`db/` or `data/db/`:** Room database class, Trip entity (with **samples_path** column), TripDao. Optional: TypeConverters for enums or dates.
- **`trip/` (or `data/`):** **TripRepository** interface. **RoomTripRepository** (primary): uses TripDao, reads samples via samples_path; exposes Flow<List<TripSummary>> (startTime DESC), getTrip(id), insert/update. **FileTripRepository**: scan dirs, read trip.json; used for migration and export support only.
- **Migration:** Run on app startup (e.g. Application onCreate or first use of repository). Scan Phase 2 dirs; per-trip idempotent (skip if Room has trip_id); best-effort insert (malformed → skip; missing fields → partial + end_reason migration_incomplete). Insert into Room via DAO.
- **Recording:** On trip end: write samples to `trips/<id>/samples.csv`, write trip.json, call repository.insertTrip(metadata, stats, samples_path) so Room is updated.
- **UI:** TripsScreen observes repository.tripsFlow(); export builds Trip from repository.getTrip(id) + samples file, generates trip.md (and optional trip.json) to temp file, shares via intent.

---

## Progress (actual)

**Status:** Not started.

| Item | Done |
|------|------|
| 1) Room schema and DAOs (Trip entity, one row per trip; no samples table) | ☐ |
| 2) Migrate Phase 2 trips into Room (scan dirs, read trip.json, insert; per-trip idempotent, best-effort) | ☐ |
| 3) Single source of truth (TripRepository interface, Room impl, list from Room, new trips to Room + samples + trip.json; export temp + share) | ☐ |
| 4) Wire UI and remove file-based list (TripsScreen from Room via TripRepository) | ☐ |
