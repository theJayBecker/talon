export type MetricStatus = "normal" | "warning" | "critical" | "unknown";
export type ReadinessStatus = "ready" | "not-ready" | "not-supported";

export interface DiagnosticMetric {
  label: string;
  value: string | number;
  unit: string;
  pid: string;
  status?: MetricStatus;
  supported: boolean;
}

export interface OxygenSensor {
  bank: number;
  sensor: number;
  voltage: number | null;
  supported: boolean;
}

export interface ReadinessMonitor {
  name: string;
  status: ReadinessStatus;
}

export interface DiagnosticsState {
  // Vehicle Status
  milOn: boolean;
  dtcCount: number;
  engineRuntimeMin: number;
  connectionMode: "connected" | "disconnected" | "mock";

  // Engine & Performance
  engineMetrics: {
    rpm: DiagnosticMetric;
    speed: DiagnosticMetric;
    engineLoad: DiagnosticMetric;
    throttlePosition: DiagnosticMetric;
    timingAdvance: DiagnosticMetric;
    intakeManifoldPressure: DiagnosticMetric;
    intakeAirTemp: DiagnosticMetric;
    massAirFlow: DiagnosticMetric;
  };

  // Fuel System
  fuelMetrics: {
    fuelLevel: DiagnosticMetric;
    fuelPressure: DiagnosticMetric;
    shortTermFuelTrimBank1: DiagnosticMetric;
    longTermFuelTrimBank1: DiagnosticMetric;
    shortTermFuelTrimBank2: DiagnosticMetric;
    longTermFuelTrimBank2: DiagnosticMetric;
  };

  // Emissions & Sensors
  emissionsData: {
    oxygenSensors: OxygenSensor[];
    catalystTempBank1: DiagnosticMetric;
    readinessMonitors: ReadinessMonitor[];
  };

  // Trouble Codes
  troubleCodes: Array<{
    code: string;
    description: string;
    type: "generic" | "manufacturer";
  }>;
}
