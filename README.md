<p align="center">
  <img src="docs/talonlogo.png" alt="TALON logo" width="220"/>
</p>

# TALON

Android app for vehicle telemetry using a Bluetooth OBD-II adapter. Live dashboard (speed, RPM, coolant, fuel), diagnostics (read/clear trouble codes), and trip recording with file export.

---

## Supported vehicles

**Tested and supported**

- Hyundai Sonata 2013 GLS

Other OBD-II vehicles with ELM327-compatible adapters may work; only the Sonata 2013 GLS has been verified so far. If you run TALON on another car, consider opening an issue with model and results.

---

## How to use

**Requirements**

- Android phone with Bluetooth
- ELM327-compatible OBD-II Bluetooth adapter (Classic Bluetooth, not BLE)
- Vehicle with OBD-II port

**Setup**

1. Pair the OBD adapter with your phone in **Settings → Bluetooth**.
2. Plug the adapter into the vehicle’s OBD-II port (usually under the dashboard).
3. Install and open TALON.

**In the app**

- **Dashboard** — Tap **Connect**, select your paired adapter. Once connected, you’ll see live speed, RPM, coolant temperature, and fuel level. Use **Disconnect** when done.
- **Diagnostics** — View check-engine status, DTC count, engine runtime. Tap **Read Codes** to fetch stored trouble codes; **Clear All Codes** (with confirmation) to clear them.
- **Trips** — Start and stop trips from the Dashboard when connected. Trips are saved locally; you can export a trip as Markdown from the trip detail screen.

Trips keep recording in the background (notification) until you stop them. If the adapter disconnects mid-trip, the app continues using GPS for speed and saves the trip when you stop.

---

## Building and running (developers)

- Open the project in Android Studio and run on a device or emulator.
- Use a physical device and a real OBD adapter for full functionality.
- For logs: `adb logcat` with tags `ElmTransport`, `ElmClient`, `ObdStateHolder`, `Parse`.

---

## Contributing

Contributions are welcome.

1. Clone the repo and get the app running (see above).
2. Open an issue to discuss larger changes or new features before a big PR.
3. For bug fixes or small improvements, a direct PR is fine.

---

## License

License TBD.
