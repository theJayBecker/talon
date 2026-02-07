# Phase 3 — Implementation Plan (Android)

**Objective:** Introduce structured persistence with Room without breaking existing trips. Phase 2 file format is ingested and migrated into Room; Room becomes the active query layer. Files remain export artifacts (generated from Room + samples when needed).

**Prerequisites:** Phase 2 complete ([phase2-implementation-plan.md](phase2-implementation-plan.md)) — trips recorded to file-backed directories (`trip.json`, `samples.csv`, `trip.md`).

---

## Policies (from roadmap)

- **Migration:** Phase 3 **ingests and migrates Phase 2 file format** into Room. Existing trip directories are read and inserted into the database; no duplicate source of truth for the list.
- **Single source of truth:** Trip list and all trip queries come from Room. Files (trip.json, trip.md, samples.csv) are either migration input or export output, not the authority for “what trips exist.”
- **Trip shape:** Metadata, stats, and sample reference aligned with talon-ui `trip-types.ts` for consistent export and future sync.
- **Raw samples:** Remain file-based (no row-per-sample in Room) to avoid row explosion. Aggregated summary data lives in the Trip row.

---

## Locked decisions (Phase 3)

- **Trips table:** One row per trip. Columns cover metadata (id, startTime, endTime, status, recordingMode, vehicle, etc.) and aggregated stats (durationSec, distanceMi, fuelStartPct, fuelEndPct, fuelUsedPct, avgSpeedMph, maxSpeedMph, avgRpm, maxRpm, avgCoolantF, maxCoolantF, maxLoadPct, idleTimeSec). Optional: notes, samplesPath (path to samples file for this trip).
- **Samples storage:** Raw samples stay in a file per trip (e.g. `trips/<trip_id>/samples.csv`). Room stores either the path to this file or the convention is fixed (trips/<id>/samples.csv). No `samples` table with one row per sample.
- **Migration strategy:** One-time migration on first app launch after upgrade (or on demand): scan Phase 2 trip directories, read each `trip.json`, insert into Room; record samples path. Optionally mark directories as migrated (e.g. rename or flag file) so we don’t re-ingest. If a directory has no `trip.json` (corrupt or partial), skip or log and continue.
- **New trips (post–Phase 3):** When a trip ends, write aggregated data (metadata + stats) to Room; write raw samples to the same file path convention (`trips/<trip_id>/samples.csv`). Optionally still write `trip.json` for backup/debug or stop writing it and generate from Room on export only.
- **Export:** “Export” (e.g. share trip as markdown or bundle) generates `trip.md` (and optionally `trip.json`) from Room row + samples file. No need to keep trip.json on disk for every trip if we can regenerate from Room + samples.
- **Trip list:** TripsScreen and any “list trips” call use Room exclusively (DAO or repository that reads from Room). No scanning trip directories for the list.

---

## Work Required

### 1) Room schema and DAOs (3.1)

**Goal:** Define Room database, one Trip entity (metadata + stats in one row), and DAOs for insert/update/query. No samples table.

- **Database:** Single Room database (e.g. `TalonDatabase`) with one table for trips. Version 1. No migrations yet (Phase 2 has no DB).
- **Trip entity:** Map to Trip row: id (UUID, primary key), startTime, endTime (nullable), status, recordingMode, vehicle (nullable), notes (nullable), durationSec, distanceMi, fuelStartPct, fuelEndPct, fuelUsedPct, avgSpeedMph, maxSpeedMph, avgRpm, maxRpm, avgCoolantF, maxCoolantF, maxLoadPct, idleTimeSec. Optional: samplesPath (nullable) or rely on convention `trips/<id>/samples.csv`.
- **DAO:** Insert trip, update trip (e.g. notes), get by id, get all ordered by startTime descending. Delete trip if needed for Phase 3 (optional).
- **Type conversions:** Store startTime/endTime as Long (millis). Status/recordingMode as String or enum.

**Exit (3.1):** Room DB and Trip entity and DAO in place; can insert and query trips.

---

### 2) Migrate Phase 2 trips into Room (3.2)

**Goal:** Existing trip directories (Phase 2 format) are ingested into Room so the list shows them and there is no dual source of truth.

- **Migration step:** Run once (e.g. on app startup or first launch after install that has Room). Scan the Phase 2 trips root (e.g. `getExternalFilesDir(null)/trips/` or same path used in Phase 2). For each subdirectory that looks like a trip (contains `trip.json`):
  - Read and parse `trip.json`.
  - Map metadata + stats to Room Trip entity. Set samples path if needed (e.g. `trips/<id>/samples.csv`).
  - Insert into Room (ignore if already present, e.g. by id).
- **Idempotency:** Use trip `id` as primary key; if row already exists, skip or replace. So re-running migration is safe.
- **Orphaned / corrupt dirs:** If `trip.json` is missing or unreadable, skip that directory and log; do not crash. Optionally leave directory as-is for manual inspection.
- **After migration:** Trip list from Room includes all migrated trips. No code path should build the list by scanning directories anymore (except inside the migration step itself).

**Exit (3.2):** All existing Phase 2 trips appear in Room; migration is idempotent and safe.

---

### 3) Single source of truth — repository and write path (3.3)

**Goal:** One place that “owns” trip data: Room for list and summary; samples file for raw samples. New trips write to Room + samples file; export generates files from Room + samples.

- **TripRepository (or equivalent):** Exposes Flow or LiveData of trips from Room (all trips, ordered). Provides getTrip(id) from Room; when full trip with samples is needed (e.g. export), load row from Room and read samples from file (trips/<id>/samples.csv).
- **New trip save (from Phase 2 recorder):** When a trip ends, Phase 2 already writes the trip directory. In Phase 3, after that (or instead of writing trip.json), insert or update the Trip row in Room from the same metadata + stats. Keep writing samples to `trips/<id>/samples.csv` so we have raw data for export. Decide whether to keep writing `trip.json` on save (redundant with Room) or drop it and generate on export only.
- **List trips:** TripsScreen (and any other UI) calls repository “get all trips”; repository returns data from Room only. No file scanning.
- **Export:** When user exports a trip, build Trip (metadata + stats + samples) from Room row + samples file; generate `trip.md` (and optionally `trip.json`) using same contract as Phase 2 / talon-ui. Write to export destination (e.g. share intent, or copy to app’s export folder). Do not rely on existing trip.json on disk for export unless migration left it and we prefer to use it.

**Exit (3.3):** No dual source of truth; trip list loads exclusively from Room; new trips persist to Room and samples file; export uses Room + samples.

---

### 4) Wire UI and remove file-based list (3.4)

**Goal:** TripsScreen and any trip list UI use Room-backed repository only. Remove or refactor any Phase 2 code that builds the list by scanning trip directories.

- **TripsScreen:** Consume trips from repository (Room-backed Flow or list). Same UI as Phase 2 (list of summaries); data source is Room.
- **Trip detail (if any):** If Phase 3 adds a simple detail screen, load trip by id from Room and samples from file when needed.
- **Recording flow:** When recording ends, ensure the save path writes to Room (and samples file) so the new trip appears in the list on next load.
- **Cleanup:** Remove any “list trips by scanning directories” logic from app code; migration is the only place that reads Phase 2 directories for ingestion.

**Exit (3.4):** Trip list is from Room only; UI and recording flow use the new persistence layer.

---

## Exit Criteria Summary

| Block | Criterion |
|-------|-----------|
| 3.1 | Room database, Trip entity, DAO; insert and query trips |
| 3.2 | Existing Phase 2 trips migrate cleanly into Room; idempotent migration |
| 3.3 | No dual source of truth; list from Room; new trips write to Room + samples file; export from Room + samples |
| 3.4 | Trip list loads exclusively from Room; UI and recorder use repository |
| **Phase 3** | Existing trips migrate cleanly; no dual source of truth; trip list loads exclusively from Room |

---

## Suggested structure (optional)

- **`db/` or `data/db/`:** Room database class, Trip entity, TripDao. Optional: TypeConverters for enums or dates.
- **`data/` or `trip/`:** TripRepository (or TripRepositoryImpl) that uses TripDao and reads/writes samples file under `trips/<id>/samples.csv`. Exposes Flow<List<TripSummary>> and getTrip(id) for detail/export.
- **Migration:** One-off migration routine (e.g. in Application onCreate or first time a repository is used) that scans Phase 2 dirs, parses trip.json, inserts into Room.
- **Recording:** Phase 2 recorder (or TripRecorder) on trip end: write samples file as today; then call repository.insertTrip(metadata, stats, samplesPath) so Room is updated.
- **UI:** TripsScreen observes repository.tripsFlow() (from Room); export flow builds Trip from repository.getTrip(id) + samples file and generates trip.md.

---

## Progress (actual)

**Status:** Not started.

| Item | Done |
|------|------|
| 1) Room schema and DAOs (Trip entity, one row per trip; no samples table) | ☐ |
| 2) Migrate Phase 2 trips into Room (scan dirs, read trip.json, insert; idempotent) | ☐ |
| 3) Single source of truth (repository, list from Room, new trips to Room + samples; export from Room + samples) | ☐ |
| 4) Wire UI and remove file-based list (TripsScreen from Room only) | ☐ |
