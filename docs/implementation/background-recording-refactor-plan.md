# Background Recording Refactor — Implementation Plan

**Objective:** Move trip recording and OBD connection handling out of the Activity so that recording continues when the screen is off or another app is focused, and OBD disconnect still triggers automatic stop-and-save. This fulfills Phase 2 requirement 2.2: *"ensure no dependency on Activity lifecycle for writing samples."*

**Prerequisites:** Current codebase as of this plan (Phase 2 trip recording, foreground service, Room + TripStorage).

---

## Decisions / Clarifications (post–Iris review)

- **Single source for holders in UI:** Application is the single source. TalonApp uses `(applicationContext as TalonApplication).obdHolder` and `(applicationContext as TalonApplication).tripHolder`. TripManager is only used inside Application to create and hold the TripStateHolder; the UI and RecordingService never call `TripManager.get()`.
- **Notification-updater job:** One loop in TripStateHolder `init`, running for the process lifetime. Every 2s: if state is ACTIVE then `TripNotificationState.update(...)`, else `TripNotificationState.clear()`. No start/cancel when entering or leaving ACTIVE.
- **stopTrip async work:** Use the same `scope` (applicationScope) instead of `GlobalScope.launch`. All async work in TripStateHolder uses one scope.
- **applicationScope:** Do **not** cancel in `onTerminate()`. Process death does not invoke it reliably; when the process is killed, everything is gone. No code for “cleanup” on terminate.
- **Process death:** Do not add “clear TripManager / ObdStateHolder on process death.” Single process run is enough; no requirement to clear.
- **TalonApp context:** Use `LocalContext.current.applicationContext as TalonApplication`; then `app.obdHolder`, `app.tripHolder`. If recomposition needs a stable reference, use `remember(app) { app.obdHolder }` (and similarly for tripHolder); the instances are stable process singletons.

---

## 1. Current State Summary

### 1.1 Architecture (as-is)

| Component | Where created | Scope / lifecycle | Role |
|-----------|----------------|-------------------|------|
| **ObdStateHolder** | `TalonApp` (`remember { ObdStateHolder(scope, context) }`) | Composition scope (`rememberCoroutineScope()`) | Bluetooth OBD connection, polling, liveValues, connectionState, fuelBurnSession, diagnostics |
| **TripManager** | `TalonApp` (`remember { TripManager.init(scope, context, ...) }`) | Same composition scope | Singleton; creates/returns **TripStateHolder** with that scope |
| **TripStateHolder** | Via `TripManager.init()` with `obdHolder.liveValues`, `connectionState`, `capabilities` | Same composition scope | Sampling (1 Hz), location updates, monitoring, auto-start, stop trip, RecordingService.start/stop |
| **RecordingService** | Started by `TripStateHolder.startTrip()`; stopped by `stopTripInternal()` or notification Stop | Foreground service (process kept alive) | Notification only; calls `TripManager.get()?.stopTrip(true)` on Stop. Does **not** own recording. |
| **TripNotificationState** | Updated by `TalonApp` LaunchedEffect every 2s | Composition scope | Live distance/gallons for notification text |

**Critical flows:**

- **Start trip:** User or auto-start → `TripStateHolder.startTrip()` → `startLocationUpdates()`, `startSampling()`, `RecordingService.start(context)`.
- **Sampling:** `TripStateHolder.samplingJob` = `scope.launch(Dispatchers.IO) { while (ACTIVE) { ... } }` — reads `liveValues.value`, GPS listener, writes to `TripStorage`, updates `_tripState`.
- **OBD disconnect:** `ObdStateHolder.disconnect()` or polling exception → `onDisconnecting?.invoke(gallons)` → set in TalonApp to `tripHolder.stopTrip(userInitiated = false, gallonsBurnedSinceConnect = gallons)`.
- **Stop trip:** `stopTrip()` → `GlobalScope.launch { stopTripInternal(...) }` → finalize storage, `RecordingService.stop(context)`, `onTripSaved?.invoke()` (e.g. `obdHolder.resetSessionStats()`).

### 1.2 The gap

- **Recording** (sampling, location, monitoring, auto-start) and **OBD** (connection, polling, liveValues) all run on the **composition scope** of `TalonApp`, i.e. the Activity’s lifecycle.
- When the Activity is **destroyed** (e.g. user switches app, system reclaims memory), that scope is **cancelled**. Then:
  - `samplingJob`, `monitoringJob`, and all `scope.launch { ... collect { } }` in TripStateHolder and ObdStateHolder stop.
  - Recording stops; notification may freeze at last values.
- **RecordingService** keeps the **process** alive but does not run any recording logic, so it does not fix the dependency on the Activity.

---

## 2. Target Architecture

### 2.1 Principle

- **Process-scoped** components own recording and OBD: they use a **CoroutineScope** that lives for the **process** (cancelled only when the app process is killed), not the Activity.
- The **foreground service** continues to only show the notification and handle Stop; it does **not** need to “hold” the recorder — the **same** TripStateHolder/ObdStateHolder used by the UI will outlive the Activity because they are created with the process scope.
- The **UI (TalonApp)** only observes state and sends user actions (start/stop trip, connect/disconnect, export, etc.); it does **not** create or own the scope that drives recording or OBD.

### 2.2 Component ownership

| Component | Owner | Scope | Created |
|-----------|--------|-------|---------|
| **Application** (new) | Process | — | Android |
| **applicationScope** | Application | `SupervisorJob() + Dispatchers.Default`; **do not** cancel in onTerminate() | Application `onCreate()` |
| **ObdStateHolder** | Application | `applicationScope` | Application `onCreate()` |
| **TripStateHolder** | Application (via TripManager.init) | `applicationScope` | TripManager.init() from Application `onCreate()`; Application exposes as `tripHolder` |
| **RecordingService** | System | — | When trip goes ACTIVE (unchanged); on Stop, gets `(applicationContext as TalonApplication).tripHolder` and calls `stopTrip(true)` |
| **TalonApp** | MainActivity | Composition | Gets `obdHolder` and `tripHolder` from Application only; does **not** create them or use TripManager |

### 2.3 Data flow (target)

- **OBD:** Single `ObdStateHolder(applicationScope, applicationContext)` in Application. Expose via `(application as TalonApplication).obdHolder`. Bluetooth and polling run on `applicationScope` → survive Activity destroy.
- **Trip recording:** Single `TripStateHolder(applicationScope, ...)` created in Application via `TripManager.init(applicationScope, applicationContext, obdHolder.liveValues, ...)`. Application exposes it as `tripHolder`. Sampling, location, monitoring, auto-start run on `applicationScope` → survive Activity destroy.
- **Callbacks:** Set once in Application after creating both holders:
  - `obdHolder.onDisconnecting = { gallons -> tripHolder.stopTrip(userInitiated = false, gallonsBurnedSinceConnect = gallons) }`
  - `tripHolder.gallonsProvider = { obdHolder.fuelBurnSession.value.gallonsBurned }`
  - `tripHolder.onTripSaved = { obdHolder.resetSessionStats() }`
  So OBD disconnect → stop-and-save works without the Activity.
- **Notification:** One loop in TripStateHolder `init`: “every 2s update TripNotificationState” `scope.launch { while (true) { delay(2000); if (state == ACTIVE) TripNotificationState.update(...) else TripNotificationState.clear() } }`. Same 2s as RecordingService notification refresh. No start/cancel when entering/leaving ACTIVE.
- **UI:** TalonApp gets `obdHolder` and `tripHolder` only from Application (`app.obdHolder`, `app.tripHolder`). No `remember { ObdStateHolder(...) }` or `TripManager.init(...)`. All existing `collectAsState` and callbacks stay the same; only the source of the instances changes.

### 2.4 Context usage

- **TripStateHolder, TripStorage, RoomTripRepository, ObdStateHolder:** Use **application context** (from Application). This is safe for getSystemService, getExternalFilesDir, database, SharedPreferences.
- **Export/share:** `tripHolder.exportTrip(context, tripId)` continues to receive **caller context**; TalonApp keeps passing `LocalContext.current` (Activity) so the share chooser has an Activity to use. No change.

---

## 3. Implementation Phases

### Phase A: Application and process scope

**Goal:** Introduce `TalonApplication` and `applicationScope`; no behavior change yet.

1. **Add `TalonApplication`**
   - New class `tech.vasker.vector.TalonApplication : Application()`.
   - In `onCreate()`: create `applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)` (store in a property). Do **not** cancel in `onTerminate()` (not guaranteed on Android; process death needs no explicit cleanup).
   - Register in `AndroidManifest.xml`: `<application android:name=".TalonApplication" ...>`.

2. **No other code changes in Phase A.** Verify app still launches and works as before.

**Exit:** App runs; Application class exists and has a process-long scope.

---

### Phase B: Move ObdStateHolder to Application

**Goal:** Single ObdStateHolder created in Application with applicationScope; UI obtains it from Application.

1. **Application**
   - In `onCreate()`, after creating `applicationScope`:  
     `obdHolder = ObdStateHolder(applicationScope, applicationContext)`  
   - Expose: `val obdHolder: ObdStateHolder` (lateinit or create in onCreate).

2. **TalonApp**
   - Remove: `val obdHolder = remember { ObdStateHolder(scope, context) }`.
   - Add: `val app = LocalContext.current.applicationContext as TalonApplication` and `val obdHolder = app.obdHolder`. If recomposition needs a stable reference, use `remember(app) { app.obdHolder }`.

3. **All other TalonApp usages of `obdHolder`** remain unchanged (connectionState, liveValues, connect, tryAutoConnect, onDisconnecting, etc.).

4. **Verify:** Connect OBD, open Diagnostics, start/stop trip from Dashboard. Ensure no duplicate ObdStateHolder (only one created in Application).

**Exit:** OBD connection and polling are process-scoped; UI still works and shows live data.

---

### Phase C: Move TripStateHolder to Application scope

**Goal:** TripManager.init() called from Application with applicationScope; TripStateHolder uses no Activity scope.

1. **Application**
   - In `onCreate()`, after creating `obdHolder`: call `TripManager.init(applicationScope, applicationContext, obdHolder.liveValues, obdHolder.connectionState, obdHolder.capabilities)` with same storage/repository as today (TripStorage, TalonDatabase.get(context), Phase2ToRoomMigration, RoomTripRepository). Pass `applicationContext`.
   - Store and expose the holder: `tripHolder = TripManager.get()!!` (or have TripManager.init return it and assign to `tripHolder`). Expose as `val tripHolder: TripStateHolder` so the UI and RecordingService use Application as the single source.

2. **TripManager**
   - No API change. Init is only called from Application. TalonApp and RecordingService do not call TripManager.get(); they use `app.tripHolder`.

3. **TalonApp**
   - Remove: `val tripHolder = remember { TripManager.init(scope, context, obdHolder.liveValues, ...) }`.
   - Use: `val tripHolder = app.tripHolder` (same `app` as for obdHolder).

4. **RecordingService**
   - On Stop action: get tripHolder from Application instead of TripManager. Use `(applicationContext as TalonApplication).tripHolder.stopTrip(userInitiated = true)` (and then `stopSelf()` as today).

5. **Wire callbacks in Application**
   - After both `obdHolder` and `tripHolder` exist:  
     - `tripHolder.gallonsProvider = { obdHolder.fuelBurnSession.value.gallonsBurned }`  
     - `tripHolder.onTripSaved = { obdHolder.resetSessionStats() }`  
     - `obdHolder.onDisconnecting = { gallons -> tripHolder.stopTrip(userInitiated = false, gallonsBurnedSinceConnect = gallons) }`  
   - Remove the same wiring from TalonApp’s LaunchedEffect(obdHolder, tripHolder).

6. **Verify:** Start trip, switch to another app or lock screen for 30+ seconds, return — trip should still be recording (check notification and/or trip file/samples). Stop trip; verify OBD disconnect still stops and saves trip.

**Exit:** Recording and OBD disconnect handling run on applicationScope; they survive Activity destroy.

---

### Phase D: Notification updates from TripStateHolder

**Goal:** Notification (distance, gallons) updates even when the UI is not composed; remove dependency on TalonApp’s LaunchedEffect for TripNotificationState.

1. **TripStateHolder**
   - Add one long-lived job in `init`: `scope.launch { while (true) { delay(2000); if (_tripState.value.state == TripState.ACTIVE) TripNotificationState.update(_tripState.value.distanceMi, gallonsProvider?.invoke()) else TripNotificationState.clear() } }`. One loop for the process lifetime; no start/cancel when entering or leaving ACTIVE. Uses the same `scope` (applicationScope).

2. **TalonApp**
   - Remove the LaunchedEffect that does `while (true) { delay(2000); ... TripNotificationState.update(...) }`.

3. **Verify:** Start trip, send app to background or lock screen; notification text (distance, gallons) should still update every ~2s.

**Exit:** Notification live stats are driven by TripStateHolder’s process-scoped job, not the UI.

---

### Phase E: Cleanup and edge cases

**Goal:** No Activity-scoped leftovers; handle process death and export cleanly.

1. **TripStateHolder**
   - Ensure every use of `scope` is the one passed from TripManager.init (applicationScope).
   - **stopTrip():** Replace `GlobalScope.launch { ... stopTripInternal(...) }` with `scope.launch { ... }` so all async work in TripStateHolder uses the same scope (no GlobalScope).

2. **Export**
   - Keep `exportTrip(context: Context, tripId: String)`; callers (TalonApp, TripDetailScreen, TripsScreen) continue to pass Activity context for the share chooser. No change.

3. **Process death**
   - Phase 2 already defines: on next launch, finalize open trips (e.g. `storage.finalizeOpenTrips()`). TripStateHolder.init already has `scope.launch(Dispatchers.IO) { storage.finalizeOpenTrips(System.currentTimeMillis()) }`. With applicationScope, this runs once per process; ensure it still runs when Application starts (it will, since TripStateHolder is created in Application onCreate). No change unless you moved finalize elsewhere.

4. **RecordingService**
   - Already updated in Phase C: on Stop, use `(applicationContext as TalonApplication).tripHolder.stopTrip(userInitiated = true)` (no TripManager.get()).

5. **Process death**
   - Do not add “clear TripManager / ObdStateHolder on process death.” Single process run is enough.

**Exit:** No remaining dependency on Activity lifecycle for recording or OBD; export and process-death behavior unchanged.

---

## 4. File Change Summary

| File | Changes |
|------|---------|
| **New: `TalonApplication.kt`** | Application subclass; `applicationScope` (no onTerminate cancel); create `ObdStateHolder` and `TripManager.init(...)`; expose `obdHolder` and `tripHolder`; wire `gallonsProvider`, `onTripSaved`, `onDisconnecting`. |
| **`AndroidManifest.xml`** | Add `android:name=".TalonApplication"` to `<application>`. |
| **`TalonApp.kt`** | Get `app = applicationContext as TalonApplication`; use `app.obdHolder` and `app.tripHolder`; remove ObdStateHolder and TripManager.init creation; remove LaunchedEffect (callbacks); remove LaunchedEffect (TripNotificationState). |
| **`TripStateHolder.kt`** | Add one notification-updater loop in init (scope.launch { while(true) { delay(2000); if ACTIVE update else clear } }); in `stopTrip()` use `scope.launch` instead of `GlobalScope.launch`. |
| **`TripManager.kt`** | No API change; init only called from Application. |
| **`RecordingService.kt`** | On Stop: get `(applicationContext as TalonApplication).tripHolder` and call `stopTrip(userInitiated = true)` (no TripManager.get()). |
| **`ObdStateHolder.kt`** | No change (still takes scope + context; scope will now be applicationScope). |
| **All screens (Dashboard, Diagnostics, Trips, TripDetail)** | No change (they receive state and callbacks from TalonApp; TalonApp’s source of obdHolder/tripHolder is the only change). |

---

## 5. Testing Checklist

- [ ] **Cold start:** Launch app; connect OBD; start trip. Verify recording (samples in storage or notification).
- [ ] **Background:** With trip active, press Home or switch to another app. Wait 1–2 minutes. Return to Talon. Verify trip still recording (notification updates, or new samples in file).
- [ ] **Screen off:** With trip active, turn screen off. Wait 1–2 minutes. Unlock and open Talon. Verify trip still recording.
- [ ] **OBD disconnect (UI visible):** Start trip; disconnect OBD (or turn adapter off). Verify trip stops and saves with status partial and gallons captured.
- [ ] **OBD disconnect (background):** Start trip; send app to background; disconnect OBD. Verify trip stops and saves (e.g. check trip list or storage for new completed/partial trip).
- [ ] **Stop from notification:** Start trip; send app to background; tap Stop in notification. Verify trip stops and notification disappears.
- [ ] **Export trip:** From Trips tab, export a trip. Verify share sheet and export file.
- [ ] **Diagnostics / Dashboard:** All existing UI (connect, reconnect, live values, diagnostics, probe, clear DTC, etc.) still works.
- [ ] **No duplicate connections:** Only one ObdStateHolder; only one TripStateHolder; UI and RecordingService get holders from Application only.
- [ ] **Activity destroyed while recording:** Enable “Don’t keep activities” (Developer options). Start trip, press Home so Activity is destroyed. Wait 1–2 minutes. Reopen app. Verify trip was still recording (notification kept updating, or new samples in storage) and stop works.

---

## 6. Risks and Mitigations

| Risk | Mitigation |
|------|-------------|
| Application.onCreate() runs before Activity; if TripManager.init() needs something from UI | TripManager.init() only needs context, scope, and StateFlows from ObdStateHolder. No UI. |
| Memory: holding ObdStateHolder + TripStateHolder for process lifetime | Acceptable; they are the core of the app. No extra large objects. |
| Multiple processes | Single process assumed; no change to process model. |
| Export with applicationContext | Export must use caller context (Activity) for share; plan keeps that. |
| Cancellation order on process death | We do not cancel applicationScope in onTerminate(). If process is killed, everything stops anyway. |

---

## 7. Rollback

If issues appear after deployment:

- Revert TalonApplication and manifest change; revert TalonApp to create ObdStateHolder and TripManager.init(scope, context, ...) with `rememberCoroutineScope()` again; revert TripStateHolder notification-updater addition; restore TalonApp LaunchedEffects for callbacks and TripNotificationState. That returns to “recording only while Activity is alive” behavior.

---

## 8. References

- Phase 2 plan §2.2: Foreground service and “no dependency on Activity lifecycle for writing samples” — [phase2-implementation-plan.md](phase2-implementation-plan.md).
- Vale investigation: recording and OBD were tied to Activity/composition scope; RecordingService only provides notification and Stop — see conversation that led to this plan.
- **Revision (Iris review):** Plan updated to adopt single source for holders (Application exposes `tripHolder`; UI and RecordingService use `app.tripHolder`), one notification loop in TripStateHolder init (no start/cancel on ACTIVE), stopTrip using `scope.launch` instead of GlobalScope, no applicationScope cancel in onTerminate(), RecordingService gets tripHolder from Application on Stop, and "Don't keep activities" test added.