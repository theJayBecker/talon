# Phase 1 — Implementation Plan (Android)

**Objective:** Ship a real APK that connects to an OBD2 Bluetooth adapter and shows live vehicle data. Phase 1 is the critical path; no trip recording yet.

**Prerequisites:** Phase 0 complete ([phase0-implementation-plan.md](phase0-implementation-plan.md)) — app launches, three tabs (Dashboard, Diagnostics, Trips), mock data displayed.

---

## Policies (from roadmap)

- **Transport:** Bluetooth Classic (SPP / RFCOMM) only. BLE out of scope.
- **Polling:** Single in‑flight OBD command, conservative polling rate. Revisit only if throughput is required later.
- **Failure:** No silent failure. Connection state and errors must be visible in UI; graceful handling (e.g. show "Disconnected" or error message, don’t crash or hide).

---

## Locked decisions (Phase 1 spec)

- **Paired-device list only** — no in-app discovery or pairing. UI: "Select from paired devices."
- **Connect:** Connect button → bottom sheet or dialog with paired devices (no dedicated Connect screen).
- **PIDs:** 010D, 010C, 0105, 012F only. Omit 0104 (engine load) in Phase 1.
- **DTCs:** Codes only (e.g. P0420). No code-to-description map in Phase 1.
- **ELM327 init:** 2 seconds per command, no retries. Timeout → fail connect and close socket.
- **ATI:** Liveness check only; do not display adapter identity in UI (optionally log to Logcat).
- **Errors:** Short user-facing message in UI; full exception/stacktrace in logs.
- **No mock data in Phase 1 runtime** when disconnected (stale values + "Stale" or "—" only).
- **One `obd/` package** for connection and protocol (no separate `bluetooth/` package).
- **State:** Single state holder in TalonApp; pass down to Dashboard and Diagnostics. Add one ViewModel only if prop-drilling becomes noisy.

---

## Work Required

### 1) Bluetooth + OBD connection (1.1)

**Goal:** User can select a paired Bluetooth device, app opens an SPP socket, runs ELM327 init, and shows connection state in the UI.

- **Permissions:** Request `BLUETOOTH_CONNECT` (and any other required Bluetooth permissions for your `minSdk`). Handle permission flow before showing device list.
- **Device selection:** Paired devices only. No in-app scan or pairing. Show list of paired devices (filter by SPP UUID if desired). Manual selection only (no auto-connect in Phase 1). Store selected device address/name for the session. UI copy: “Select from paired devices.”
- **SPP connection:** Open `BluetoothSocket` (RFCOMM) to selected device. Run on a background thread or coroutine; do not block the main thread.
- **ELM327 init sequence:** After socket connects, send in order: `ATZ`, `ATE0`, `ATL0`, `ATS0`, `ATH0`, `ATSP0`. Read responses; tolerate noise (e.g. strip `>`, trim, ignore empty). **Timeout: 2 seconds per command; no retries.** If any critical command fails or times out, treat as connection failure and close socket.
- **ATI check:** Send `ATI` and read response to confirm liveness. Required for “Exit: Responds to ATI”. Do not display adapter identity in UI; optionally log response to Logcat for debugging.
- **Connection state in UI:** Expose state: `Disconnected` | `Connecting` | `Connected` | `Error` (with short message). Dashboard shows current state. When disconnected, user taps “Connect” → bottom sheet or dialog with paired devices → select device to connect.
- **Disconnect:** Provide a way to disconnect (close socket, clear state). On disconnect, set state to `Disconnected` and surface in UI.

**Exit (1.1):** Connects to adapter; responds to `ATI`; connection state visible in UI.

---

### 2) Live PID polling (1.2)

**Goal:** One command in flight at a time; poll PIDs at a conservative rate; parse and expose values for Dashboard.

- **Single command in flight:** Implement a queue or state machine so that only one OBD request is outstanding. Send next command only after the previous response is received (or timed out). No overlapping requests.
- **Conservative polling:** Choose a fixed interval (e.g. 300–500 ms per PID cycle). Cycle through the four PIDs in a defined order: Speed (010D), RPM (010C), Coolant Temp (0105), Fuel Level (012F). Each response updates the corresponding value; then proceed to next PID.
- **Request/response format:** Send OBD II mode 01 requests (e.g. `010D` for speed). ELM327 returns hex bytes; parse according to SAE spec (e.g. 010D: bytes A B → speed = (256*A+B)/4 for km/h; convert to mph if needed). Handle `NO DATA`, `?`, timeouts, and malformed lines without crashing — treat as “no update” or show a sentinel (e.g. “—” or last value with “stale” indicator).
- **Expose live values:** Hold current `speedMph`, `rpm`, `coolantF`, `fuelPercent` only. Phase 1 PIDs: 010D, 010C, 0105, 012F; omit 0104 (engine load). These feed the Dashboard UI. When disconnected, stop polling; keep last values only for “stale” display (see §4); show connection state.
- **Graceful failure:** On socket error or disconnect during polling, stop the loop, set connection state to `Disconnected` (or `Error`), and show state in UI (Failure Policy).

**Exit (1.2):** Real values displayed on Dashboard; stable while driving (no crashes, continuous updates at conservative rate).

---

### 3) Minimal diagnostics (1.3)

**Goal:** Read DTCs (Mode 03), clear DTCs (Mode 04) with user confirmation, show MIL status and engine runtime.

- **MIL status:** Query MIL (e.g. Mode 01 PID 01 or appropriate service). Parse to get MIL on/off and DTC count. Expose for Diagnostics screen.
- **Engine runtime:** Query Mode 01 PID 1F (engine run time). Parse and expose in seconds or minutes for Diagnostics screen.
- **Read DTCs (Mode 03):** Send Mode 03 request; parse response into list of DTC codes (e.g. P0301, P0420). **Codes only in Phase 1** — no code-to-description map or descriptions. Show code list on Diagnostics screen.
- **Clear DTCs (Mode 04):** Send Mode 04 to clear DTCs. Require explicit user confirmation (e.g. “Clear codes? This may reset readiness.” + Confirm/Cancel). After clear, re-read MIL/DTC count and Mode 03 so UI reflects new state.
- **Error handling:** If Mode 03/04 fails (timeout, NO DATA, etc.), show error in UI; do not crash. Unsupported or “no codes” is a valid outcome.

**Exit (1.3):** Diagnostics screen shows MIL status, engine runtime, DTC list; user can read and clear codes with confirmation.

---

### 4) Wire UI and failure handling

**Goal:** Dashboard and Diagnostics use real OBD data when connected; when disconnected or in error, state is visible and app does not crash.

- **Dashboard:** When connected, display live `fuelPercent`, `speedMph`, `rpm`, `coolantF` from polling layer. When disconnected or error: show connection state prominently (“Disconnected” or “Error: …”); show **stale last-known values** only if we have them, labeled “Stale”, otherwise show “—”. **Do not show Phase 0 mock or placeholder data in Phase 1 runtime.**
- **Diagnostics:** When connected, show MIL, DTC count, engine runtime, and DTC list from OBD. “Read codes” triggers Mode 03 (or refresh). “Clear codes” shows confirmation dialog then Mode 04. When disconnected, show “Disconnected” and disable or message for read/clear.
- **Connect flow:** No dedicated Connect screen. Dashboard shows “Connect” button when disconnected; tap opens **bottom sheet or dialog** listing paired devices. Select device → run init and ATI; on success set `Connected` and start polling; on failure set `Error` with **short user-facing message** (e.g. “Connection failed: timeout”) and log full exception/stacktrace to Logcat.
- **Lifecycle:** On app background/foreground, decide whether to keep connection open or disconnect (Phase 1 can keep it simple: e.g. stay connected until user disconnects or socket error). On socket error, always surface state and stop polling.

**Exit (Phase 1 overall):** APK installs; connects to real vehicle; shows live fuel/speed/RPM/coolant; reads and clears codes; survives short real drive without crashing.

---

## Exit Criteria Summary

| Block | Criterion | Done |
|-------|-----------|------|
| 1.1 | Connects to adapter; responds to `ATI`; connection state visible in UI | ✅ |
| 1.2 | Real values displayed; stable while driving | ✅ |
| 1.3 | Read DTCs (Mode 03); clear DTCs (Mode 04, confirmed); MIL status; engine runtime | ✅ |
| **Phase 1** | APK installs; connects to real vehicle; shows live fuel/speed/RPM/coolant; reads & clears codes; survives short real drive without crashing | ✅ |

---

## Suggested structure (optional)

**One `obd/` package** (no separate `bluetooth/` package):

- `obd/ElmTransport` — Bluetooth socket connect/read/write (paired devices only).
- `obd/ElmClient` — AT init sequence, sendCommand, 2s timeout, no retries; ATI liveness check.
- `obd/ObdCommands` — Constants: ATZ/ATI/PIDs (010D, 010C, 0105, 012F), Mode 01/03/04.
- `obd/Parse` — Minimal PID and Mode 03/04 response parsing.

**State:** Single state holder in `TalonApp` (or MainActivity); pass connection state and live metrics down to Dashboard and Diagnostics. Introduce one ViewModel only if prop-drilling becomes noisy—not before.

**Screens:** DashboardScreen and DiagnosticsScreen consume connection state and live data; Dashboard shows Connect button → bottom sheet/dialog when disconnected; show short error message when state is Error.

---

## Progress (actual)

**Status: Implemented.** All work items and Phase 1 exit criteria are satisfied.

| Item | Done |
|------|------|
| 1) Bluetooth + OBD connection (permissions, device list, SPP, ELM327 init, ATI, state in UI) | ✅ |
| 2) Live PID polling (single in-flight, conservative rate, 010D/010C/0105/012F, feed Dashboard) | ✅ |
| 3) Minimal diagnostics (Mode 03, Mode 04 with confirm, MIL, engine runtime) | ✅ |
| 4) Wire UI + failure handling (real data when connected, state when disconnected/error) | ✅ |

**Implemented structure:** `obd/` — `ElmTransport` (SPP socket, sendCommand with `>` read loop), `ElmClient` (init sequence, ATI, sendObdCommand), `ObdCommands`, `Parse` (speed, rpm, coolant, fuel, MIL/DTC count, engine runtime, Mode 03 DTC list), `ObdState` (ConnectionState, LivePidValues, DiagnosticsData), `ObdStateHolder` (connect/disconnect on IO, 400 ms polling cycle, refreshDiagnostics, clearDtc). `TalonApp`: permission launcher, ModalBottomSheet for paired devices, `ObdStateHolder`; passes connectionState, liveValues, diagnosticsData to Dashboard and Diagnostics. Dashboard: ConnectView when disconnected (Connect → sheet), live fuel/speed/rpm/coolant when connected, Disconnect, stale indicator. Diagnostics: VehicleStatusCard (MIL, DTC count, runtime), TroubleCodesCard (Read Codes, DTC list, Clear with AlertDialog confirm).
