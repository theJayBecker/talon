# 🦅 Project TALON — Roadmap (Revised for Coherence & Optionality)

## North Star
**Reliable, fuel‑centric vehicle telemetry, end‑to‑end on real hardware.**

This doctrine bounds scope and defines success across all phases.

---

## Artifacts & UI Surface

- **Android app** (`app/`) — Jetpack Compose. Primary delivery: installable APK, real OBD + GPS, foreground trip recording. All phases target this surface.
- **talon-ui** (`talon-ui/`) — React (Vite) web app. Reference UI and design prototype: parallel screens (Dashboard, Diagnostics, Trips), mock OBD/diagnostics data, in-memory trip repository. Defines and validates **trip schema** and **markdown export format** (YAML frontmatter, summary, optional samples). Use as design reference and contract for Android; trip ID in talon-ui is currently time-based — align to UUID when Android persistence is implemented.

---

## Global Policies (Explicit)

### Transport Scope
- **Phase 1–2:** Bluetooth Classic (SPP / RFCOMM) only.
- **BLE:** Explicitly out of scope until a future phase.

### Trip Source of Truth
- **Trip identity:** UUID generated at trip start.
- **Authoritative key:** `(trip_id, start_timestamp)`.

### Failure Policy (Doctrine)
- No silent failure.
- On OBD disconnect mid‑trip:
  - Show state in UI.
  - Continue trip via GPS if available.
  - Persist partial data on stop.
- On GPS loss:
  - Fall back to OBD speed.
- On total signal loss:
  - Pause trip state, resume if signal returns within window.
  - Otherwise close trip as `partial`.

### Polling Policy
- **Phase 1:** Single in‑flight OBD command, conservative polling.
- This is a deliberate tradeoff, revisited only if throughput is required.

---

## PHASE 0 — Foundations
**Status:** Complete. Android skeleton implemented per [Phase 0 implementation plan](implementation/phase0-implementation-plan.md); talon-ui was already complete.

- Android project created
- Jetpack Compose UI skeleton generated — **done** (MainActivity, TalonApp, 3 screens, bottom nav via tab index)
- Navigation wired (Dashboard / Diagnostics / Trips) — **done** (tab index only, no NavHost)
- Mock data flowing — **done** (`data/mock/MockData.kt`: dashboard, diagnostics, 2 trip summaries)
- **talon-ui:** Web reference UI with Dashboard, Diagnostics, Trips, Connect flow, trip start/stop, trip detail, and markdown export (clipboard). Mock OBD and mock diagnostics; in-memory trip repository. Trip types and export schema align with Phase 2 contract.

**Exit Criteria**
- App installs and launches on physical device
- No crashes
- UI fully navigable

---

## PHASE 1 — Core End‑to‑End Workflow (CRITICAL)

**Status:** Complete. Implemented per [Phase 1 implementation plan](implementation/phase1-implementation-plan.md).

**Implementation plan:** [Phase 1 implementation plan](implementation/phase1-implementation-plan.md)

### Objective
Ship a real APK that connects to an OBD2 adapter and shows live vehicle data.

### 1.1 Bluetooth + OBD Connection — **done**
- Bluetooth Classic (SPP / RFCOMM)
- Manual selection from paired devices (bottom sheet, paired list)
- ELM327 init sequence (ATZ, ATE0, ATL0, ATS0, ATH0, ATSP0)
- Robust read loop (noise‑tolerant)

**Exit**
- Connects to adapter ✅
- Responds to `ATI` ✅
- Connection state visible in UI ✅

---

### 1.2 Live PID Polling — **done**
**PIDs**
- Speed (010D)
- RPM (010C)
- Coolant Temp (0105)
- Fuel Level (012F)

**Rules**
- One command in flight
- Conservative polling (400 ms)
- Graceful error surfacing per Failure Policy

**Exit**
- Real values displayed ✅
- Stable while driving ✅

---

### 1.3 Minimal Diagnostics — **done**
- Read DTCs (Mode 03)
- Clear DTCs (Mode 04, confirmed)
- MIL status
- Engine runtime

**Phase 1 Success Criteria**
- APK installs ✅
- Connects to real vehicle ✅
- Shows live fuel/speed/RPM/coolant ✅
- Reads & clears codes ✅
- Survives short real drive without crashing ✅

---

## PHASE 2 — Trip Recording (File‑Backed)

### Objective
Record trips reliably using hybrid GPS + OBD logic.

### Trip Logic
- **Primary speed:** GPS
- **Fallback:** OBD speed

### Storage (Source of Truth)
Each trip stored as a directory:
- `trip.json` — canonical summary
- `samples.csv` — raw samples
- `trip.md` — human‑readable export (YAML frontmatter, summary table, optional samples). **Contract:** Format implemented in talon-ui (`trip-markdown.ts`); Android export must match for interchange and Phase 6 export bundles.

These files are the **authoritative representation** of a trip in Phase 2.

**Exit Criteria**
- Trips auto‑start/stop correctly
- Trips persist across app restarts
- Background recording via foreground service works

---

## PHASE 3 — Persistence & Migration

### Objective
Introduce structured persistence without breaking existing trips.

### Policy
- Phase 3 **ingests and migrates Phase 2 file format** into Room.
- Files remain export artifacts; Room becomes the active query layer.

### Data Model
- Trips table (one row per trip)
- Samples stored as:
  - Aggregated summaries in DB
  - Raw samples remain file‑based (no row explosion)
- Trip shape (metadata, stats, samples) aligned with talon-ui `trip-types.ts` for consistent export and future sync.

**Exit Criteria**
- Existing trips migrate cleanly
- No dual source of truth
- Trip list loads exclusively from Room

---

## PHASE 4 — Diagnostics Expansion
Add advanced standard OBD‑II diagnostics:
- Fuel trims (STFT/LTFT)
- Intake air temperature
- MAF
- Throttle position
- Timing advance
- Readiness monitors
- Freeze‑frame data

**Reference:** talon-ui Diagnostics screen and `diagnostics-types.ts` define the target metrics and layout (engine, fuel, emissions, trouble codes); Android implements real PIDs against this contract.

**Exit Criteria**
- Unsupported PIDs handled gracefully
- No performance regression

---

## PHASE 5 — Power & Voltage (Eagle Wings Bridge)

### Objective
Extend TALON into power and electrical telemetry.

- Battery voltage
- Charging system voltage
- Voltage trend analysis
- Alert thresholds

**Note**
Phase 4 and Phase 5 are **logically independent** but ordered for interpretive clarity.

**Exit Criteria**
- Voltage visible and stable
- Alerts fire deterministically

---

## PHASE 6 — Polish & Hardening

- Charts (talon-ui has chart UI primitives; Android adds trip/time-series charts)
- Trip annotations
- Export bundles (markdown + optional files; format aligned with talon-ui export)
- Schema stabilization (trip types in talon-ui as reference)
- Test coverage (unit + on‑device integration)

**Exit Criteria**
- Refactors do not break North Star
- App behavior is predictable under failure

---

## Summary
Phase 1 remains non‑negotiable.

If TALON:
- connects reliably,
- shows fuel accurately,
- records trips without dying,

then every later phase compounds value rather than rescuing mistakes.