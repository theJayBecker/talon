# Phase 2 — Implementation Plan (Android)

**Objective:** Record trips reliably using hybrid GPS + OBD logic. Each trip is stored as a file-backed directory (`trip.json`, `samples.csv`, `trip.md`). Recording runs in a foreground service so it survives background and lock screen.

**Prerequisites:** Phase 1 complete ([phase1-implementation-plan.md](phase1-implementation-plan.md)) — app connects to OBD, shows live PIDs and diagnostics.

---

## Policies (from roadmap)

- **Trip identity:** UUID generated at trip start. Authoritative key: `(trip_id, start_timestamp)`.
- **Speed source:** Primary = GPS speed; fallback = OBD speed when GPS unavailable or lost.
- **Failure (no silent failure):**
  - OBD disconnect mid‑trip: show state in UI; continue trip using GPS if available; persist partial data on stop.
  - GPS loss: fall back to OBD speed for trip logic.
  - Total signal loss: pause trip state; resume if signal returns within a defined window; otherwise close trip as `partial`.
- **Storage:** Each trip is a directory. Files inside are the authoritative representation in Phase 2 (no database yet).

---

## Locked decisions (Phase 2)

- **Signal-loss window (explicit):**
  - **GPS unavailable:** No valid location/speed update for 5 seconds OR reported accuracy worse than ±50m (if available).
  - **OBD unavailable:** No successful PID response for 5 seconds (e.g. 5 consecutive 1s attempts fail) OR socket disconnect.
  - **Window start:** When both GPS and OBD speed are unavailable at the same time.
  - **Auto-close:** If both missing for 30 seconds, close trip as `partial`.
- **Trip directory name:** Use `trip_id` (UUID string) so directory is unique and matches roadmap key.
- **Recording modes:** Support **manual** start/stop only. Auto start/stop deferred.
- **trip.md format:** Match talon-ui contract (YAML frontmatter, Summary table, optional Samples table, Notes, footer). Reference: `talon-ui/src/app/lib/trip-markdown.ts`. Samples table is **off by default**.
- **Engine load (0104):** Include column in `samples.csv` now for schema stability; leave blank if not available.
- **CSV schema (stable):** `timestamp_iso, t_sec, speed_mph, rpm, fuel_pct, coolant_f, engine_load_pct, source_speed, flags`
- **Storage location:** Use `getExternalFilesDir(null)` under `trips/` (app-scoped external; no permissions).
- **Process death:** No resume in Phase 2. On next launch, finalize any active trip as `partial` with `end_reason: process_death`.
- **Trip detail:** No trip detail screen; Trips list only.
- **Statuses:** Use only `completed` and `partial`.

---

## Work Required

### 1) Trip state machine and speed source (2.1)

**Goal:** A single trip state machine with states IDLE → ACTIVE → ENDING → SAVED. Speed for distance/recording comes from GPS first, OBD as fallback.

- **States:** `IDLE` (no trip); `ACTIVE` (recording); `ENDING` (flushing/writing files); `SAVED` (trip directory written, state returns to IDLE for next trip). No trip detail screen in Phase 2 (list only).
- **Start trip:** User starts recording (manual). Generate UUID, set start timestamp, capture initial fuel from current OBD value (or last known). Transition to ACTIVE. Start foreground service and begin sampling.
- **Speed source:** Prefer GPS speed (from `LocationManager` or FusedLocationProvider). When GPS is unavailable or not yet available, use OBD speed from existing Phase 1 live values. Use this combined “effective speed” for distance and for writing samples.
- **Stop trip:** User stops or trip is closed due to signal loss. Transition to ENDING: stop sampling, compute final stats, write trip directory (see §3), then SAVED and back to IDLE.
- **Total signal loss:** When both GPS and OBD are unavailable, keep ACTIVE and continue sampling with `speed_mph = null` and loss flags. If either returns within 30 seconds, continue; if window expires, close trip as `partial` (ENDING → SAVED).
- **OBD disconnect mid‑trip:** Per Failure Policy: keep trip ACTIVE; switch to GPS-only for speed; show “OBD disconnected” in UI; on stop, persist trip with status per rules below.
- **Status semantics:** User stop → `completed` unless both signals are lost at stop time. Auto-close due to loss → `partial`.
- **Sampling cadence:** 1 Hz fixed. Continue sampling during loss with null fields and flags.

**Exit (2.1):** Trips start and stop correctly; speed for recording is GPS when available, OBD fallback; state machine and signal-loss window behave per policy.

---

### 2) Foreground service (2.2)

**Goal:** Recording continues when app is in background or device is locked; user sees a persistent notification.

- **Service type:** Android foreground service with a persistent notification (e.g. “Talon — Recording trip” with optional stop action). Start service when trip moves to ACTIVE; stop when trip reaches SAVED (or when app is destroyed and no active trip — decide whether to keep last trip “active” across process death or not; Phase 2 can keep it simple: stop service on stop trip only).
- **Permissions:** Request `FOREGROUND_SERVICE_LOCATION` (and `ACCESS_FINE_LOCATION` / `ACCESS_COARSE_LOCATION` for GPS). Declare foreground service type in manifest. Android 14+ may require `POST_NOTIFICATIONS` for the notification channel.
- **Lifecycle:** Service holds reference to trip state / recorder; receives location updates and OBD samples (from existing ObdStateHolder or a shared flow). When trip ends, service stops itself after writing files.
- **Survives background/lock:** No special extra steps beyond foreground service + notification; ensure no dependency on Activity lifecycle for writing samples (samples are appended or buffered in the service / recorder).

**Exit (2.2):** Foreground service runs during recording; persistent notification visible; recording continues in background and when screen is locked.

---

### 3) Local storage — trip directory and files (2.3)

**Goal:** Each trip is stored as a directory containing `trip.json`, `samples.csv`, and `trip.md`. Directory name = `trip_id` (UUID). Files are the source of truth for Phase 2.

- **Location:** App-specific external storage via `getExternalFilesDir(null)` under `trips/`. Use a single root and one subdirectory per trip: `trips/<trip_id>/`.
- **trip.json:** Canonical summary. Structure aligned with talon-ui / roadmap:
  - `metadata`: `id`, `startTime`, `endTime`, `status` (completed | partial), `recordingMode` (manual), optional `vehicle`, optional `endReason` (e.g. `process_death`).
  - `stats`: `durationSec`, `distanceMi`, `fuelStartPct`, `fuelEndPct`, `fuelUsedPct`, `avgSpeedMph`, `maxSpeedMph`, `avgRpm`, `maxRpm`, `avgCoolantF`, `maxCoolantF`, `maxLoadPct`, `idleTimeSec`.
  - Optional `notes`.
  - No `samples` array in JSON (samples live in `samples.csv`).
- **samples.csv:** Raw samples, one row per sample. Columns (stable):
  - `timestamp_iso, t_sec, speed_mph, rpm, fuel_pct, coolant_f, engine_load_pct, source_speed, flags`
  - Use `null`/empty values when a field is not available (e.g. during signal loss).
  - Append-only during recording; write or flush on stop.
- **trip.md:** Human-readable export. **Contract:** Match talon-ui format:
  - YAML frontmatter: `type: obd_trip`, `start`, `end`, `duration_sec`, `distance_mi`, `fuel_*`, `avg_*`, `max_*`, `idle_time_sec`, `recording_mode`, `status`, optional `vehicle`.
  - Title: `# Trip - <date> <time>`.
  - Summary table (Duration, Distance, Fuel Used, Avg/Max Speed, Avg/Max RPM, Coolant, Max Load, Idle Time, Recording Mode, Status).
  - Optional Notes section.
  - Optional Samples table (Time, Speed, RPM, Coolant, Load, Fuel). **Off by default.**
  - Footer: `*Generated by Talon on <date>*`.
- **Writing order:** On trip end (ENDING → SAVED), write `trip.json`, then `samples.csv`, then `trip.md` (so JSON and CSV are authoritative; trip.md is derived for export).

**Exit (2.3):** Trip directory created with `trip_id`; `trip.json` and `samples.csv` and `trip.md` present; `trip.md` matches talon-ui contract for interchange.

---

### 4) Wire Dashboard and Trips UI (2.4)

**Goal:** User can start/stop a trip from the Dashboard; Trips tab shows the list of saved trips from disk; recording state is visible.

- **Dashboard:** When connected (and optionally when disconnected for manual start with stale data):
  - Show “Start trip” (manual). If auto is implemented, show “Auto” toggle or equivalent.
  - When a trip is ACTIVE, show “Recording” indicator and “Stop trip” (and optionally live duration/distance/fuel delta). Stop trip → confirm or direct stop → state machine moves to ENDING then SAVED.
  - Show connection state and OBD disconnect message so user knows when trip is GPS-only (Failure Policy).
- **Trips screen:** Replace mock list with list loaded from trip storage. Scan trip directories (e.g. `trips/*/trip.json`), parse each `trip.json` for summary fields (id, startTime, endTime, status, durationSec, distanceMi, fuelUsedPct). Sort by start time (newest first). Tapping a trip does nothing in Phase 2 (no detail screen).
- **Persistence across restarts:** Trip list is read from files on each load (no in-memory-only list). So “trips persist across app restarts” is satisfied by reading from the same trip directories.
- **Process death finalize:** On app launch, scan for any trip with missing `endTime` and finalize as `partial` with `endReason: process_death`.

**Exit (2.4):** Start/stop from Dashboard; Trips list from disk; recording state visible; trips persist across app restarts.

---

## Exit Criteria Summary

| Block | Criterion |
|-------|-----------|
| 2.1 | Trip state machine (IDLE → ACTIVE → ENDING → SAVED); speed = GPS primary, OBD fallback; manual start/stop only |
| 2.2 | Foreground service; persistent notification; recording in background and when locked |
| 2.3 | Per-trip directory with `trip.json`, `samples.csv`, `trip.md`; `trip.md` matches talon-ui contract |
| 2.4 | Start/stop from Dashboard; Trips list from storage; trips persist across restarts |
| **Phase 2** | Trips start/stop correctly (manual); persist across app restarts; background recording via foreground service works |

---

## Suggested structure (optional)

- **`trip/` or `recording/` package:** Trip state (IDLE/ACTIVE/ENDING/SAVED), trip ID and timestamps, effective speed (GPS + OBD), sample buffer. State machine and signal-loss window logic.
- **`TripRecorder` or similar:** Holds current trip state; subscribes to location (GPS) and OBD live values; produces samples; on stop, computes stats and calls storage to write directory.
- **`TripStorage` or `TripRepository`:** Writes and reads trip directories: `listTrips()` (scan `trips/`, read each `trip.json` for summary), `writeTrip(tripId, metadata, stats, samples)` → writes `trip.json`, `samples.csv`, `trip.md` (generate markdown per talon-ui contract).
- **Foreground service:** `RecordingService` (or similar). Started when trip goes ACTIVE; shows notification; holds or receives trip recorder; stops when trip is SAVED.
- **UI:** Dashboard gets “Start trip” / “Stop trip” and recording state from trip state holder (or ViewModel). TripsScreen gets list from TripStorage.listTrips() (or equivalent).

---

## Progress (actual)

**Status:** Not started.

| Item | Done |
|------|------|
| 1) Trip state machine + speed source (GPS primary, OBD fallback; signal-loss window) | ☐ |
| 2) Foreground service (notification; background + lock screen) | ☐ |
| 3) Local storage (trip dir, trip.json, samples.csv, trip.md) | ☐ |
| 4) Wire Dashboard + Trips UI (start/stop, list from disk, persist across restarts) | ☐ |
