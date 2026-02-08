# Fuel burn refactor — review (Iris)

**Status:** Partially implemented. Core math and persistence exist; sampling cadence and a few spec details are wrong or missing. Below: what exists, what’s missing, what to change.

---

## A) Domain models (single source of truth)

| Spec | Status | Notes |
|------|--------|------|
| `ObdSampleRaw(tsMs, mapKpa, rpm, iatC, tpsPct, speedKph, fuelPct)` | **Missing** | No dedicated raw-sample type. `LivePidValues` holds map/rpm/iat/tps/fuel; no `tsMs` in a sample DTO. Trip sampling uses `TripSample` (timestampIso, tSec, speedMph, rpm, fuelPct, …, fuelRateGph, fuelBurnedGalTotal, fuelMethod). |
| `FuelEstimate(tsMs, gph, galPerSec)` | **Missing** | `SpeedDensity.computeFuelFlow()` returns `Pair<Double, Double>?` (galPerSec, gph). No `FuelEstimate` data class. |
| `TripAccumulator(lastTsMs?, totalGallons, emaGph?)` | **Partial** | Trip fuel state lives in `TripSession`: `gallonsBurnedTrip`, `lastSampleTimeMs` in TripStateHolder. No explicit `TripAccumulator` type; no stored `emaGph` (avg is computed at end as mean). |

**Verdict:** Logic is implemented without the exact data classes. Optional refactor: introduce ObdSampleRaw / FuelEstimate / TripAccumulator for clarity and tests; not required for correctness.

---

## B) Sampling: right sensors, order, cadence

| Spec | Status | Notes |
|------|--------|------|
| Request 010B, 010C, 010F each cycle | **Partial** | 010C and 012F are in Tier 1 every cycle. **010B (MAP) and 010F (IAT) are in Tier 2 only**, which runs every *other* cycle (`cycleIndex % 2 == 0`). So MAP/IAT update ~every 2 cycles. |
| Order per cycle: 010B → 010C → 010F → optional 0111 → optional 012F (slower) | **No** | Tier 1 order: 010D, 010C, 0105, 012F, 0104?, 015E/0110. Tier 2: STFT, LTFT, …, 010F, 010B, 0110, 0111. MAP/IAT not first; not every cycle. |
| 5–7 Hz (150–200 ms cycle) for MAP/RPM/IAT | **No** | `POLL_DELAY_MS = 400`. Tier 2 runs every other cycle; each cycle is multiple commands × 400 ms. Effective MAP/IAT rate is well below 1 Hz (roughly 0.2–0.4 Hz depending on Tier 2 run). |
| All reads via ObdCommandQueue; probe pauses polling | **Yes** | `ElmClient.sendObdCommand` → queue; probe cancels `pollingJob`, runs probe, then `startPolling()` again. |

**Verdict:** Must fix so MAP, RPM, IAT are read **every** cycle and, if possible, with a shorter delay for the fuel trio so cadence approaches 5–7 Hz. Option A: run 010B, 010C, 010F at the start of every cycle (when `supportsSpeedDensity`) with ~80 ms between commands, then continue Tier 1 as today. Option B: dedicated fuel job that only does 010B, 010C, 010F (and optional 0111) every 150–200 ms via the same queue (single in-flight preserved).

---

## C) FuelBurnCalculator (pure functions)

| Spec | Status | Notes |
|------|--------|------|
| `FuelBurnCalculator.kt` with constants (2.4 L, R_air, AFR, 745 g/L, L/gal) | **Done** | `SpeedDensity` object: same constants; engine displacement 2.4, R_AIR 287.05, AFR 14.7, FUEL_DENSITY_G_PER_L 745, L_PER_GAL. |
| `estimate(sample: ObdSampleRaw): FuelEstimate?` | **Different shape** | `SpeedDensity.computeFuelFlow(mapKpa, iatC, rpm, tpsPct)` takes primitives (Int/Double/Float?), returns `Pair(galPerSec, gph)?`. Same math; no ObdSampleRaw/FuelEstimate types. |
| Require mapKpa, rpm, iatC, rpm > 400; VE from TPS or MAP | **Done** | MIN_RPM_FOR_FLOW = 400; VE = TPS-based or MAP-based clamp 0.35–0.95. |
| Formula (rho, intakeEventsPerSec, airVolPerSec, fuelGalPerSec, gph) | **Done** | Matches spec. |

**Verdict:** Computation is correct and used in ObdStateHolder (live gph) and TripStateHolder (trip integration). Naming and types differ from spec; behavior matches.

---

## D) Integrate fuel over time (TripAccumulator)

| Spec | Status | Notes |
|------|--------|------|
| On valid FuelEstimate: dt = (tsMs - lastTsMs)/1000; integrate only if dt in (0, 2.0] | **Missing** | TripStateHolder uses `dtSec = (now - lastSampleTimeMs)/1000.0` and always integrates when `dtSec > 0` and speed-density returns non-null. No guard for pauses/jumps (e.g. dt > 2 s). |
| totalGallons += fuelGalPerSec * dt; emaGph = ema(…, 0.2); lastTsMs = tsMs | **Partial** | totalGallons updated; lastSampleTimeMs updated every sample (1 s). No EMA for gph in trip; avg is mean at end. |
| Invalid sample: do not integrate; policy documented | **Partial** | If map/iat/rpm missing or rpm ≤ 400, speed-density returns null and we don’t add. lastSampleTimeMs is still updated (we set it at start of sampling step). Spec says “do not update lastTsMs or update but skip integration; pick one and document”. |

**Verdict:** Add dt guard: integrate only when `dtSec in (0.0, 2.0]`. Optionally add trip-level EMA for avg_fuel_burn_gph; current mean is acceptable. Document policy: on invalid sample we skip integration but still advance lastSampleTimeMs (so next dt is from last *sample* time, not last *valid* time).

---

## E) Persist + export

| Spec | Status | Notes |
|------|--------|------|
| samples.csv: ts_ms, map_kpa, rpm, iat_c, tps_pct, fuel_pct, speed_kph, fuel_gph, fuel_gal_total | **Partial** | CSV has timestamp_iso, t_sec, speed_mph, rpm, fuel_pct, …, fuel_rate_gph, fuel_burned_gal_total, fuel_method. Missing: ts_ms, map_kpa, iat_c, tps_pct. speed_mph not speed_kph. |
| trip.json: fuel_used_gallons, avg_fuel_burn_gph, method | **Done** | Stored as fuelBurnedGal, avgFuelBurnGph, fuelMethod. |
| trip.md: fuel_used_gallons, avg_fuel_burn_gph, fuel_method; Fuel section | **Done** | Frontmatter has fuel_used_gallons (and fuel_burned_gal), avg_fuel_burn_gph, fuel_method; body has Fuel row. |

**Verdict:** trip.json and trip.md are sufficient. CSV can be extended with ts_ms, map_kpa, iat_c, tps_pct for debugging and consistency with spec (append at end to avoid breaking existing parsers).

---

## F) UI

| Spec | Status | Notes |
|------|--------|------|
| Dashboard: “Fuel burn (gph)” and “Trip fuel used (gal)” when recording | **Done** | FuelBurnedCard shows gph and gallons (trip vs session). |
| Diagnostics: MAP / IAT / TPS live | **Done** | DiagnosticsScreen shows MAP (kPa), IAT °F, Throttle %. |
| If fuel estimate unavailable: “Fuel burn: unavailable (needs MAP/RPM/IAT)” | **Missing** | When fuelRateGph is null we don’t show that message; we just omit the gph line. |

**Verdict:** Add explicit “Fuel burn: unavailable (needs MAP/RPM/IAT)” when speed-density is selected but gph is null.

---

## G) Tests

| Spec | Status | Notes |
|------|--------|------|
| Unit test: FuelBurnCalculator with MAP=39 kPa, RPM=966, IAT=16°C, TPS=12.5% → gph in 0.1–0.5 | **Missing** | No unit test for SpeedDensity / fuel burn. |

**Verdict:** Add one unit test (e.g. in `ExampleUnitTest` or new `SpeedDensityTest`) asserting gph in range for that input.

---

## Acceptance criteria

| Criterion | Status |
|-----------|--------|
| Polling collects MAP/RPM/IAT consistently without breaking the command queue | **Partial** — MAP/IAT not every cycle; queue and probe behavior are correct. |
| Fuel burn (gph) non-zero when engine running; near-zero when off | **Yes** — MIN_RPM_FOR_FLOW and formula handle this. |
| Trip recording accumulates total gallons smoothly | **Partial** — works but no dt cap; long pauses could over-count. |
| Exported markdown includes fuel_used_gallons in properties/frontmatter | **Yes** |
| No STOPPED responses; no concurrent sends | **Yes** — single queue; probe pauses polling. |

---

## Recommended changes (minimal, focused)

1. **Sampling**  
   - Run 010B, 010C, 010F (and optionally 0111) **every** cycle when `supportsSpeedDensity`, at the **start** of the loop, with a shorter delay (e.g. 80–100 ms) between these so MAP/RPM/IAT refresh every cycle.  
   - Optionally add a dedicated fuel-only job that does 010B, 010C, 010F every 150–200 ms via the same queue to reach 5–7 Hz (single in-flight preserved).

2. **Trip integration**  
   - In TripStateHolder, integrate fuel only when `dtSec in (0.0, 2.0]` (and optionally when `dtSec > 0` for first sample). Document: invalid sample → skip integration, still set lastSampleTimeMs.

3. **CSV**  
   - Append columns to samples.csv: ts_ms, map_kpa, iat_c, tps_pct (and optionally speed_kph = speed_mph * 1.60934). Update TripSample and parser accordingly.

4. **UI**  
   - When capabilities indicate speed-density but `fuelRateGph == null`, show: “Fuel burn: unavailable (needs MAP/RPM/IAT)”.

5. **Test**  
   - One unit test: MAP=39, RPM=966, IAT=16, TPS=12.5 → gph in [0.1, 0.5] (or similar range).

No extra features: no background service changes, no DB migration.

**Implementation status (same session):** Sampling fixed via `runFuelPidsFirst()` (010B/010C/010F every cycle, 80 ms delay). Trip integration guarded with `dt in (0, 2.0]`. CSV extended with ts_ms, map_kpa, iat_c, tps_pct. Dashboard shows "Fuel burn: unavailable (needs MAP/RPM/IAT)" when speed-density and gph null. Unit test `SpeedDensityTest.idleSample_producesGphInExpectedRange()` added.

— Iris, enemy of excess
