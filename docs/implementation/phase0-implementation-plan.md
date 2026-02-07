# Phase 0 — Implementation Plan (Android)

Implement Phase 0 for Project TALON (Android) with the **smallest possible surface**.

---

## Progress (actual)

**Status: Implemented.** All work items and exit criteria are satisfied.

| Item | Done |
|------|------|
| 1) App launch (Compose, MainActivity, manifest launcher) | ✅ |
| 2) Bottom nav, 3 tabs, `selectedTabIndex` in `TalonApp` | ✅ |
| 3) Mock data in `data/mock/MockData.kt` (dashboard, diagnostics, trips) | ✅ |
| 4) Screens wired to mock values (Dashboard, Diagnostics, Trips) | ✅ |

**Implemented structure:** `MainActivity` → `setContent { TalonApp() }`; `TalonApp` holds `selectedTabIndex`, `NavigationBar` with 3 items, `when(selectedTabIndex)` → `DashboardScreen` / `DiagnosticsScreen` / `TripsScreen`. Single mock object `MockData` with `DashboardMock`, `DiagnosticsMock`, and `List<TripSummaryMock>` (2 trips, UUID ids). No NavHost, no ViewModels, no trip detail; connection state static ("Mock").

---

## Constraints

- **No Compose Navigation / NavHost** — tab index only (e.g. `selectedTabIndex` state).
- **No ViewModels** in Phase 0.
- **No trip detail screen** — Trips tab shows list only.
- **Default Material 3 theme only** — no custom theme/colors.
- **Static connection state** — show "Mock" or "Disconnected" only; no toggle.

---

## Work Required

### 1) Make app launch

- Add Jetpack Compose dependencies and enable Compose in Gradle.
- Create `MainActivity` with `setContent { TalonApp() }`.
- Add launcher activity to `AndroidManifest.xml`.

### 2) Add bottom navigation with 3 tabs

- In `MainActivity`, hold `selectedTabIndex` state (e.g. `var selectedTabIndex by remember { mutableIntStateOf(0) }`).
- Tabs: **Dashboard**, **Diagnostics**, **Trips**.
- Each tab index renders a separate composable (no `NavHost`).

### 3) Add minimal mock data

Single module/file under `app/src/main/.../data/mock` (one file or one small package):

- **Dashboard mock:** `fuelPercent`, `speedMph`, `rpm`, `coolantF` (optional).
- **Diagnostics mock:** `milOn`, `dtcCount`, `engineRuntimeMin`, `dtcs` (list of 0–2 items: code + description).
- **Trips mock:** list of 0–2 `TripSummary` with: `id` (UUID string), `startTime`, `durationSec`, `distanceMi`, `fuelUsedPct`.

### 4) Wire screens to mock values

- Each screen displays the mock values (no placeholder text for data).
- No timers required; optional simple `LaunchedEffect` tick for a “live” feel on Dashboard.

---

## Exit Criteria

- [x] APK installs and launches on device.
- [x] No crashes.
- [x] Tabs switch reliably.
- [x] Each screen shows mock values (not placeholders).
