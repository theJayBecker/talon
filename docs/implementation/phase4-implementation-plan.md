# Phase 4 — Implementation Plan (Android)

**Objective:** Expand OBD diagnostics with a **fluid reactive** data pipeline: one source of OBD state, all PIDs we can get from the device, and UI that composes from that state. Add fuel trims, intake air temp, MAF, throttle, timing advance, readiness monitors, and freeze-frame data. Unsupported PIDs are handled gracefully (no crash; null values show "—" only). No performance regression (single in-flight command, tiered polling).

**Prerequisites:** Phase 1 complete ([phase1-implementation-plan.md](phase1-implementation-plan.md)) — live PIDs (speed, RPM, coolant, fuel), MIL, DTC read/clear, engine runtime. Phase 2/3 optional for app context; Phase 4 focuses on OBD layer and Diagnostics UI.

**Reference:** talon-ui [diagnostics-types.ts](../../talon-ui/src/app/lib/diagnostics-types.ts) defines engine, fuel, emissions, and trouble-code layout; Android implements real PIDs against that contract.

---

## Locked clarifications (pre-implementation)

Answers to pre-implementation questions; implement to these decisions only.

| # | Question | Decision |
|---|----------|----------|
| **1. State shape** | One object vs. keep two flows? | **Extend in place.** Keep two StateFlows: `LivePidValues`, `DiagnosticsData`. Add new fields to both; do **not** introduce a third flow or a single merged ObdState. Fold freeze frame, readiness, and pending DTCs into **DiagnosticsData**. |
| **2. Discovery scope** | 0100 only, or 0120 / 0140 / 0160? | **0100 and 0120 only.** Phase 4 = discover PIDs 01–20 (0100) and 21–40 (0120). No 0140 or 0160. |
| **3. "Supported" in UI** | Show "Not supported" vs "—" for null? | **UI shows "—" for all null.** Use the supported set only to skip requesting unsupported PIDs. Do not expose "supported" per PID to UI; no "Not supported" copy. |
| **4. Intake manifold pressure (010B)** | Add PID or defer? | **Defer.** Do not add 010B in Phase 4. Keep the UI slot (to match talon-ui layout) and show "—" until we add the PID later. |
| **5. Mode 09 readiness TIDs** | Which exact TID(s)? | **09 04 only.** Request Mode 09 PID 04; parse the response into a list of (monitor id/name, status: ready \| not ready \| not supported). Document the chosen TID in code. No 09 02 then per-monitor. |
| **6. Tier timing** | Concrete numbers? | **Tier 1:** 400 ms between Tier-1 commands (unchanged). **Tier 2:** run every **2nd** main loop cycle (i.e. every other time we finish Tier 1). **Tier 3:** run every **45 s** (single number). |
| **7. Freeze frame** | One frame or one per DTC? | **One frame only.** Request/display a single freeze frame (e.g. for the DTC returned by 0202, or first available). Not one per stored DTC. |
| **8. Control module voltage (0142) and VIN** | Phase 4 or defer? | **Defer both.** Do not request or poll 0142 in Phase 4; leave for Phase 5. Do not implement VIN (09 02) in Phase 4; no half-support. |
| **9. Backward compatibility** | Rename/remove existing fields? | **Additive only.** Keep all existing field names and types in `LivePidValues` and `DiagnosticsData` (speedMph, rpm, coolantF, fuelPercent, …; milOn, dtcCount, engineRuntimeSec, dtcCodes). Only add fields; no renames or removals. |

---

## Policies (from roadmap)

- **Single in-flight OBD command** — unchanged; no overlapping requests.
- **Unsupported PIDs handled gracefully** — request only PIDs in supported set (from 0100/0120); on NO DATA / timeout, store null; UI shows "—" for null, never crash.
- **No performance regression** — Dashboard and live feel must remain smooth; use tiered polling (high-frequency core PIDs, lower-frequency diagnostics) so total traffic does not spike.
- **Reactive display** — All OBD-derived UI (Dashboard, Diagnostics, any future screens) subscribes to one OBD state stream; no per-screen polling or duplicate requests.

---

## OBD data inventory (what we get from the device)

Single source of truth for "every piece of data" from the Bluetooth OBD adapter. All values are optional (null if unsupported or error).

### Mode 01 — Current powertrain data (live)

| PID | Name | Status | Unit / notes |
|-----|------|--------|---------------|
| 00 | Supported PIDs [01–20] | Add (discovery) | Bitmask; drive which PIDs we request |
| 01 | Monitor status (MIL, DTC count) | Have | 0101 |
| 04 | Calculated engine load | Add | % |
| 05 | Engine coolant temperature | Have | °C → °F |
| 06 | STFT Bank 1 | Add | % |
| 07 | LTFT Bank 1 | Add | % |
| 08 | STFT Bank 2 | Add | % |
| 09 | LTFT Bank 2 | Add | % |
| 0C | Engine speed | Have | rpm |
| 0D | Vehicle speed | Have | km/h → mph |
| 0E | Timing advance | Add | ° before TDC |
| 0F | Intake air temperature | Add | °C → °F |
| 10 | MAF air flow rate | Have | g/s |
| 11 | Throttle position | Add | % |
| 1F | Run time since engine start | Have | s |
| 0B | Intake manifold absolute pressure | Defer (Phase 4: UI slot, show "—") | kPa |
| 20 | Supported PIDs [21–40] | Add (discovery) | Bitmask; Phase 4 discovery = 0100 + 0120 only |
| 2F | Fuel tank level | Have | % |
| 42 | Control module voltage | Defer (Phase 5) | V |
| 5E | Engine fuel rate | Have | L/h |

Additional Mode 01 PIDs (O2 sensors, catalyst temp, barometric, etc.) can be added later; Phase 4 focuses on the table above plus discovery.

### Mode 02 — Freeze frame

| Item | Description |
|------|-------------|
| 02 | DTC that caused stored freeze frame |
| Same PIDs as Mode 01 (where applicable) | Snapshot at time of DTC. Request Mode 02 after PID 00/02 to get one frame. |

### Mode 03 — Stored DTCs

| Item | Status |
|------|--------|
| List of stored DTC codes | Have |

### Mode 04 — Clear DTCs

| Item | Status |
|------|--------|
| Clear command | Have; after clear, refresh 01/03/07 state |

### Mode 07 — Pending DTCs

| Item | Add |
|------|-----|
| DTCs detected this/last drive cycle, not yet confirmed | Same format as 03; separate list in state |

### Mode 09 — Readiness (Phase 4); VIN deferred

| TID | Name | Add / defer |
|-----|------|--------------|
| 04 | Readiness monitors (id + status) | Add; **09 04 only**; ready / not ready / not supported |
| 02 | VIN | **Defer** (not in Phase 4) |

---

## Locked decisions (Phase 4)

- **State:** Extend **LivePidValues** and **DiagnosticsData** in place; no third flow, no single merged ObdState. New diagnostics (freeze frame, readiness, pending DTCs) go into **DiagnosticsData**.
- **Support discovery:** **0100 and 0120 only.** Use bitmask to decide which PIDs to request; do not expose "supported" to UI — UI shows "—" for any null.
- **Tiered polling:** **Tier 1:** 010C, 010D, 0105, 012F (and 0104 if supported); **400 ms** between commands (unchanged). **Tier 2:** 0106–0109, 010E, 010F, 0110, 0111 (if supported); run every **2nd** main loop cycle. **Tier 3:** 0101, 011F, Mode 03, Mode 07, Mode 09 04; run once after connect and after clear, then every **45 s**. Single queue; one command in flight.
- **Parsers:** Add Parse methods for each new PID; return nullable. Freeze frame: **one** snapshot via Mode 02 (0202 + same PIDs as 01). Readiness: **Mode 09 PID 04 only**; parse to list of (id/name, ready | not ready | not supported).
- **Engine load (0104):** Add to LivePidValues. **Intake manifold pressure (010B):** Defer; UI slot shows "—". **Control module voltage (0142), VIN:** Defer.
- **Backward compatibility:** Additive only; keep all existing field names and types.
- **DTC descriptions:** Phase 4 remains codes-only (e.g. P0420).

---

## Work Required

### 1) Support discovery and expanded state model (4.1)

**Goal:** Know which PIDs the vehicle supports (0100 + 0120 only); extend existing **LivePidValues** and **DiagnosticsData** with new fields; no third flow.

- **Discovery:** After connect and init, request **0100** then **0120**. Parse 4-byte bitmasks for PIDs 01–20 and 21–40. Store supported set (e.g. `Set<String>`) for request logic only; do not expose to UI. No 0140/0160.
- **LivePidValues:** Add nullable fields: engineLoad (0104), stftB1, ltftB1, stftB2, ltftB2 (0106–0109), intakeAirTempF (010F), throttlePercent (0111), timingAdvanceDeg (010E). Keep existing speed, rpm, coolantF, fuelPercent, mafGps, fuel rate, etc. Do **not** add control module voltage (0142) or intake manifold pressure (010B) in Phase 4.
- **DiagnosticsData:** Add: pendingDtcCodes (Mode 07), freezeFrameSnapshot (one snapshot, nullable), readinessMonitors (list of monitor + status from 09 04). Keep existing milOn, dtcCount, engineRuntimeSec, dtcCodes, errorMessage.
- **Backward compatibility:** All existing field names and types unchanged; new fields additive only.

**Exit (4.1):** Discovery 0100 + 0120 runs after connect; LivePidValues and DiagnosticsData extended; supported set used only to skip unsupported PIDs.

---

### 2) Parsers and commands for new PIDs and modes (4.2)

**Goal:** Parse every new PID and mode; add ObdCommands; handle NO DATA without crash. No 010B, 0142, or VIN.

- **ObdCommands:** Add 0100, 0104, 0106–0109, 010E, 010F, 0111, 0120. Add Mode 02 (0202 + same PIDs as 01 for snapshot), Mode 07, Mode 09 **04** only for readiness. Do not add 010B, 0142, or 09 02 (VIN).
- **Parse (Mode 01):** Engine load (0104): 100*A/255 %. Fuel trims (0106–0109): (100/128)*A - 100 %. Timing advance (010E): A/2 - 64 °. Intake air temp (010F): A-40 °C → °F. Throttle (0111): 100*A/255 %. All return nullable.
- **Parse Mode 02:** Request 0202 (DTC that caused frame), then Mode 02 for same PIDs as 01; parse into **one** snapshot data class. Handle "no frame" (0202 returns 0000).
- **Parse Mode 07:** Same as Mode 03; list of DTC codes → `pendingDtcCodes`.
- **Parse Mode 09 04 (readiness):** Request **09 04** only. Parse response to list of (monitor id/name, status: ready | not ready | not supported). Document "Mode 09 PID 04" in code.
- **Graceful failure:** Every parser returns null or empty on NO DATA / ? / parse error; never throw. Log at debug.

**Exit (4.2):** Parsers for 0100, 0104, 0106–0109, 010E, 010F, 0111, 0120, Mode 02 (one frame), Mode 07, Mode 09 04; unsupported → null.

---

### 3) Reactive pipeline and tiered polling (4.3)

**Goal:** Single in-flight command; tiered loop with concrete timings; all results written into existing LivePidValues and DiagnosticsData flows.

- **State holder:** ObdStateHolder keeps existing **two** MutableStateFlows (liveValues, diagnosticsData). After each response, update the corresponding field; on failure, set null or leave previous.
- **Tiered loop (concrete):** (1) **Tier 1:** 010D, 010C, 0105, 012F (and 0104 if in supported set); **400 ms** delay between each command (unchanged). (2) **Tier 2:** 0106, 0107, 0108, 0109, 010E, 010F, 0110, 0111 — only PIDs in supported set; run Tier 2 **every 2nd** main loop (i.e. every other cycle after Tier 1 completes). (3) **Tier 3:** 0101, 011F, Mode 03, Mode 07, Mode 09 04; run once after connect and after clear, then every **45 s**. Freeze frame: one-shot Mode 02 only when user taps "Load freeze frame".
- **Support-aware:** Before requesting a Tier 2 PID, check supported set from 0100/0120; skip if not supported.
- **Single queue:** One sendCommand in flight; next after response or timeout. Tier 1 cadence unchanged so Dashboard does not regress.

**Exit (4.3):** Two flows, one loop with Tier 1 = 400 ms, Tier 2 = every 2nd cycle, Tier 3 = 45 s; no performance regression.

---

### 4) Diagnostics UI and freeze frame (4.4)

**Goal:** Diagnostics screen shows all new metrics; readiness list; one freeze frame on demand; **all null values show "—"** (no "Not supported" copy).

- **Diagnostics screen:** Consume extended LivePidValues and DiagnosticsData. Sections: Vehicle status (MIL, DTC count, runtime, connection). Engine & performance: RPM, speed, engine load, throttle, timing advance, **intake manifold pressure (slot only, show "—")**, IAT, MAF. Fuel system: fuel level, STFT/LTFT B1/B2. Readiness monitors (from 09 04): list with ready / not ready / not supported. Trouble codes: stored (03) + pending (07); Read codes, Clear codes (with confirm). **Freeze frame:** Button "Load freeze frame"; on tap request **one** Mode 02 snapshot; show in card or bottom sheet (labeled "At DTC").
- **Graceful display:** For every null value show **"—"** only. No "Not supported", no "NO DATA" raw string, no crash.
- **Reference:** Match talon-ui diagnostics-types.ts grouping where applicable; intake manifold pressure slot present, value "—" until 010B added later.

**Exit (4.4):** Diagnostics shows Phase 4 metrics, readiness, one freeze frame on demand; null = "—".

---

## Exit Criteria Summary

| Block | Criterion |
|-------|-----------|
| 4.1 | Support discovery (0100 + 0120 only); extend LivePidValues and DiagnosticsData (no third flow) |
| 4.2 | Parsers for 0104, 0106–0109, 010E, 010F, 0111, Mode 02 (one frame), 07, 09 04; null on unsupported |
| 4.3 | Tier 1 = 400 ms, Tier 2 = every 2nd cycle, Tier 3 = 45 s; two flows; no perf regression |
| 4.4 | Diagnostics UI: new metrics, readiness, one freeze frame; null = "—" only |
| **Phase 4** | Unsupported PIDs handled gracefully (null = "—"); no performance regression; fluid reactive data collection/display |

---

## Suggested structure (optional)

- **obd/ObdState.kt** — Extend **LivePidValues** (engineLoad, stftB1, ltftB1, stftB2, ltftB2, intakeAirTempF, throttlePercent, timingAdvanceDeg). Extend **DiagnosticsData** (pendingDtcCodes, freezeFrameSnapshot, readinessMonitors). No new type; no third flow.
- **obd/ObdCommands.kt** — Add 0100, 0104, 0106–0109, 010E, 010F, 0111, 0120; Mode 02 (0202 + PIDs), Mode 07, Mode 09 **04**.
- **obd/Parse.kt** — parseEngineLoad, parseFuelTrim, parseTimingAdvance, parseIntakeAirTemp, parseThrottlePosition; parseMode02FreezeFrame (one snapshot); parseMode07DtcList; parseMode09_04_Readiness (list of monitor + status).
- **obd/ObdStateHolder.kt** — Discovery 0100 + 0120 after connect; tiered loop (Tier 1 @ 400 ms, Tier 2 every 2nd cycle, Tier 3 @ 45 s); update liveValues and diagnosticsData; one-shot Mode 02 on user "Load freeze frame".
- **ui/screen/DiagnosticsScreen.kt** — Consume extended flows; sections for fuel trims, IAT, throttle, timing, engine load, intake manifold slot "—"; readiness list; freeze frame button + one snapshot.

---

## Progress (actual)

**Status:** Complete.

010B (MAP) was added for speed-density fuel estimation; the intake manifold pressure UI slot now shows live MAP when available (see fuel burn refactor in Phase 4 readiness plan).

| Item | Done |
|------|------|
| 1) Support discovery and expanded state model | Done |
| 2) Parsers and commands for new PIDs and modes | Done |
| 3) Reactive pipeline and tiered polling | Done |
| 4) Diagnostics UI and freeze frame | Done |
