export interface TripSample {
  timestamp: number;
  speedMph: number;
  rpm: number;
  coolantF: number;
  engineLoadPercent: number;
  fuelPercent: number;
}

export interface TripMetadata {
  id: string;
  startTime: number;
  endTime: number | null;
  status: "recording" | "completed" | "partial";
  recordingMode: "auto" | "manual";
  vehicle?: string;
}

export interface TripStats {
  durationSec: number;
  distanceMi: number;
  fuelStartPct: number;
  fuelEndPct: number;
  fuelUsedPct: number;
  avgSpeedMph: number;
  maxSpeedMph: number;
  avgRpm: number;
  maxRpm: number;
  avgCoolantF: number;
  maxCoolantF: number;
  maxLoadPct: number;
  idleTimeSec: number;
}

export interface Trip {
  metadata: TripMetadata;
  stats: TripStats;
  samples: TripSample[];
  notes?: string;
}

export interface TripSummary {
  id: string;
  startTime: number;
  endTime: number | null;
  status: "recording" | "completed" | "partial";
  durationSec: number;
  distanceMi: number;
  fuelUsedPct: number;
}
