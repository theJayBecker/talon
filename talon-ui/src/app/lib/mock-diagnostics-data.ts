import { DiagnosticsState, DiagnosticMetric, MetricStatus } from "./diagnostics-types";

export class MockDiagnosticsDataSource {
  private state: DiagnosticsState;
  private startTime: number;
  private updateInterval: NodeJS.Timeout | null = null;
  private subscribers: Set<(state: DiagnosticsState) => void> = new Set();

  constructor() {
    this.startTime = Date.now();
    this.state = this.createInitialState();
  }

  private createInitialState(): DiagnosticsState {
    return {
      milOn: true,
      dtcCount: 2,
      engineRuntimeMin: 0,
      connectionMode: "mock",

      engineMetrics: {
        rpm: {
          label: "Engine RPM",
          value: 850,
          unit: "rpm",
          pid: "010C",
          status: "normal",
          supported: true,
        },
        speed: {
          label: "Vehicle Speed",
          value: 0,
          unit: "mph",
          pid: "010D",
          status: "normal",
          supported: true,
        },
        engineLoad: {
          label: "Engine Load",
          value: 28.5,
          unit: "%",
          pid: "0104",
          status: "normal",
          supported: true,
        },
        throttlePosition: {
          label: "Throttle Position",
          value: 15.2,
          unit: "%",
          pid: "0111",
          status: "normal",
          supported: true,
        },
        timingAdvance: {
          label: "Timing Advance",
          value: 12.5,
          unit: "°",
          pid: "010E",
          status: "normal",
          supported: true,
        },
        intakeManifoldPressure: {
          label: "Intake Manifold Pressure",
          value: 35.2,
          unit: "kPa",
          pid: "010B",
          status: "normal",
          supported: true,
        },
        intakeAirTemp: {
          label: "Intake Air Temperature",
          value: 72,
          unit: "°F",
          pid: "010F",
          status: "normal",
          supported: true,
        },
        massAirFlow: {
          label: "Mass Air Flow",
          value: 4.82,
          unit: "g/s",
          pid: "0110",
          status: "normal",
          supported: true,
        },
      },

      fuelMetrics: {
        fuelLevel: {
          label: "Fuel Level",
          value: 65.8,
          unit: "%",
          pid: "012F",
          status: "normal",
          supported: true,
        },
        fuelPressure: {
          label: "Fuel Pressure",
          value: 350,
          unit: "kPa",
          pid: "010A",
          status: "normal",
          supported: true,
        },
        shortTermFuelTrimBank1: {
          label: "Short Term Fuel Trim (Bank 1)",
          value: 2.3,
          unit: "%",
          pid: "0106",
          status: "normal",
          supported: true,
        },
        longTermFuelTrimBank1: {
          label: "Long Term Fuel Trim (Bank 1)",
          value: -1.5,
          unit: "%",
          pid: "0107",
          status: "normal",
          supported: true,
        },
        shortTermFuelTrimBank2: {
          label: "Short Term Fuel Trim (Bank 2)",
          value: "—",
          unit: "%",
          pid: "0108",
          status: "unknown",
          supported: false,
        },
        longTermFuelTrimBank2: {
          label: "Long Term Fuel Trim (Bank 2)",
          value: "—",
          unit: "%",
          pid: "0109",
          status: "unknown",
          supported: false,
        },
      },

      emissionsData: {
        oxygenSensors: [
          {
            bank: 1,
            sensor: 1,
            voltage: 0.45,
            supported: true,
          },
          {
            bank: 1,
            sensor: 2,
            voltage: 0.52,
            supported: true,
          },
          {
            bank: 2,
            sensor: 1,
            voltage: null,
            supported: false,
          },
          {
            bank: 2,
            sensor: 2,
            voltage: null,
            supported: false,
          },
        ],
        catalystTempBank1: {
          label: "Catalyst Temperature (Bank 1)",
          value: 1245,
          unit: "°F",
          pid: "013C",
          status: "normal",
          supported: true,
        },
        readinessMonitors: [
          { name: "Catalyst", status: "not-ready" },
          { name: "Heated Catalyst", status: "not-ready" },
          { name: "Evaporative System", status: "ready" },
          { name: "Secondary Air System", status: "not-supported" },
          { name: "A/C Refrigerant", status: "not-supported" },
          { name: "Oxygen Sensor", status: "ready" },
          { name: "Oxygen Sensor Heater", status: "ready" },
          { name: "EGR System", status: "ready" },
        ],
      },

      troubleCodes: [
        {
          code: "P0420",
          description: "Catalyst System Efficiency Below Threshold (Bank 1)",
          type: "generic",
        },
        {
          code: "P0171",
          description: "System Too Lean (Bank 1)",
          type: "generic",
        },
      ],
    };
  }

  start() {
    if (this.updateInterval) return;

    this.updateInterval = setInterval(() => {
      this.updateMetrics();
      this.notifySubscribers();
    }, 1000);
  }

  stop() {
    if (this.updateInterval) {
      clearInterval(this.updateInterval);
      this.updateInterval = null;
    }
  }

  private updateMetrics() {
    // Update engine runtime
    this.state.engineRuntimeMin = Math.floor((Date.now() - this.startTime) / 60000);

    // Simulate some varying metrics
    const rpm = this.state.engineMetrics.rpm;
    const speed = this.state.engineMetrics.speed;
    const engineLoad = this.state.engineMetrics.engineLoad;
    const throttle = this.state.engineMetrics.throttlePosition;
    const maf = this.state.engineMetrics.massAirFlow;

    // Add small random variations
    rpm.value = Math.max(700, Math.min(7000, (rpm.value as number) + (Math.random() - 0.5) * 50));
    speed.value = Math.max(0, Math.min(120, (speed.value as number) + (Math.random() - 0.5) * 2));
    engineLoad.value = Math.max(0, Math.min(100, (engineLoad.value as number) + (Math.random() - 0.5) * 5));
    throttle.value = Math.max(0, Math.min(100, (throttle.value as number) + (Math.random() - 0.5) * 3));
    maf.value = Math.max(0, Math.min(200, (maf.value as number) + (Math.random() - 0.5) * 0.5));

    // Update status based on values
    rpm.status = this.getRpmStatus(rpm.value as number);
    engineLoad.status = this.getEngineLoadStatus(engineLoad.value as number);

    // Update oxygen sensor voltages
    this.state.emissionsData.oxygenSensors.forEach((sensor) => {
      if (sensor.supported && sensor.voltage !== null) {
        sensor.voltage = Math.max(0, Math.min(1, sensor.voltage + (Math.random() - 0.5) * 0.02));
      }
    });
  }

  private getRpmStatus(rpm: number): MetricStatus {
    if (rpm > 6500) return "critical";
    if (rpm > 5000) return "warning";
    return "normal";
  }

  private getEngineLoadStatus(load: number): MetricStatus {
    if (load > 85) return "warning";
    if (load > 95) return "critical";
    return "normal";
  }

  subscribe(callback: (state: DiagnosticsState) => void): () => void {
    this.subscribers.add(callback);
    return () => this.subscribers.delete(callback);
  }

  private notifySubscribers() {
    this.subscribers.forEach((callback) => callback({ ...this.state }));
  }

  getState(): DiagnosticsState {
    return { ...this.state };
  }

  readTroubleCodes() {
    // Simulate reading codes - already populated in mock
    this.notifySubscribers();
  }

  clearTroubleCodes() {
    this.state.troubleCodes = [];
    this.state.dtcCount = 0;
    this.state.milOn = false;
    
    // Reset some readiness monitors after clearing
    this.state.emissionsData.readinessMonitors = this.state.emissionsData.readinessMonitors.map(
      (monitor) => {
        if (monitor.status === "ready") {
          return { ...monitor, status: "not-ready" as const };
        }
        return monitor;
      }
    );
    
    this.notifySubscribers();
  }

  setConnectionMode(mode: "connected" | "disconnected" | "mock") {
    this.state.connectionMode = mode;
    this.notifySubscribers();
  }
}
