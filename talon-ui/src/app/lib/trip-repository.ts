import { Trip, TripSummary, TripSample, TripMetadata, TripStats } from "./trip-types";

export class TripRepository {
  private trips: Trip[] = [];
  private activeTrip: Trip | null = null;
  private subscribers: Set<() => void> = new Set();

  constructor() {
    this.initializeMockTrips();
  }

  private initializeMockTrips() {
    const now = Date.now();
    const oneDay = 24 * 60 * 60 * 1000;

    // Mock completed trip 1 (yesterday morning)
    this.trips.push({
      metadata: {
        id: "trip-001",
        startTime: now - oneDay - 2 * 60 * 60 * 1000,
        endTime: now - oneDay - 1.5 * 60 * 60 * 1000,
        status: "completed",
        recordingMode: "manual",
        vehicle: "Hyundai Sonata 2020",
      },
      stats: {
        durationSec: 1800,
        distanceMi: 24.5,
        fuelStartPct: 85.0,
        fuelEndPct: 78.2,
        fuelUsedPct: 6.8,
        avgSpeedMph: 48.7,
        maxSpeedMph: 72.0,
        avgRpm: 2150,
        maxRpm: 4200,
        avgCoolantF: 198.0,
        maxCoolantF: 205.0,
        maxLoadPct: 82.5,
        idleTimeSec: 240,
      },
      samples: this.generateMockSamples(1800, 85.0, 78.2),
      notes: "Morning commute to work",
    });

    // Mock completed trip 2 (3 days ago)
    this.trips.push({
      metadata: {
        id: "trip-002",
        startTime: now - 3 * oneDay - 3 * 60 * 60 * 1000,
        endTime: now - 3 * oneDay - 1 * 60 * 60 * 1000,
        status: "completed",
        recordingMode: "auto",
        vehicle: "Hyundai Sonata 2020",
      },
      stats: {
        durationSec: 7200,
        distanceMi: 68.3,
        fuelStartPct: 92.5,
        fuelEndPct: 72.1,
        fuelUsedPct: 20.4,
        avgSpeedMph: 34.2,
        maxSpeedMph: 68.5,
        avgRpm: 1950,
        maxRpm: 3800,
        avgCoolantF: 195.0,
        maxCoolantF: 208.0,
        maxLoadPct: 76.0,
        idleTimeSec: 840,
      },
      samples: this.generateMockSamples(7200, 92.5, 72.1),
    });

    // Mock partial trip 3 (last week)
    this.trips.push({
      metadata: {
        id: "trip-003",
        startTime: now - 7 * oneDay,
        endTime: now - 7 * oneDay + 600 * 1000,
        status: "partial",
        recordingMode: "manual",
        vehicle: "Hyundai Sonata 2020",
      },
      stats: {
        durationSec: 600,
        distanceMi: 8.2,
        fuelStartPct: 68.0,
        fuelEndPct: 66.5,
        fuelUsedPct: 1.5,
        avgSpeedMph: 49.2,
        maxSpeedMph: 65.0,
        avgRpm: 2300,
        maxRpm: 3500,
        avgCoolantF: 192.0,
        maxCoolantF: 199.0,
        maxLoadPct: 68.0,
        idleTimeSec: 60,
      },
      samples: this.generateMockSamples(600, 68.0, 66.5),
      notes: "Connection lost during trip",
    });

    // Sort trips by start time (newest first)
    this.trips.sort((a, b) => b.metadata.startTime - a.metadata.startTime);
  }

  private generateMockSamples(durationSec: number, fuelStart: number, fuelEnd: number): TripSample[] {
    const samples: TripSample[] = [];
    const numSamples = Math.min(Math.floor(durationSec / 5), 100); // Sample every 5 seconds, max 100 samples
    const fuelDelta = fuelEnd - fuelStart;

    for (let i = 0; i < numSamples; i++) {
      const progress = i / numSamples;
      samples.push({
        timestamp: Date.now() - durationSec * 1000 + progress * durationSec * 1000,
        speedMph: 20 + Math.random() * 50,
        rpm: 1500 + Math.random() * 2000,
        coolantF: 190 + Math.random() * 15,
        engineLoadPercent: 20 + Math.random() * 60,
        fuelPercent: fuelStart + fuelDelta * progress,
      });
    }

    return samples;
  }

  subscribe(callback: () => void): () => void {
    this.subscribers.add(callback);
    return () => this.subscribers.delete(callback);
  }

  private notify() {
    this.subscribers.forEach((callback) => callback());
  }

  getAllTrips(): TripSummary[] {
    return this.trips.map((trip) => ({
      id: trip.metadata.id,
      startTime: trip.metadata.startTime,
      endTime: trip.metadata.endTime,
      status: trip.metadata.status,
      durationSec: trip.stats.durationSec,
      distanceMi: trip.stats.distanceMi,
      fuelUsedPct: trip.stats.fuelUsedPct,
    }));
  }

  getTrip(id: string): Trip | null {
    return this.trips.find((trip) => trip.metadata.id === id) || null;
  }

  getActiveTrip(): Trip | null {
    return this.activeTrip;
  }

  startTrip(recordingMode: "auto" | "manual", initialFuel: number): string {
    const id = `trip-${Date.now()}`;
    this.activeTrip = {
      metadata: {
        id,
        startTime: Date.now(),
        endTime: null,
        status: "recording",
        recordingMode,
        vehicle: "Hyundai Sonata 2020",
      },
      stats: {
        durationSec: 0,
        distanceMi: 0,
        fuelStartPct: initialFuel,
        fuelEndPct: initialFuel,
        fuelUsedPct: 0,
        avgSpeedMph: 0,
        maxSpeedMph: 0,
        avgRpm: 0,
        maxRpm: 0,
        avgCoolantF: 0,
        maxCoolantF: 0,
        maxLoadPct: 0,
        idleTimeSec: 0,
      },
      samples: [],
    };
    this.notify();
    return id;
  }

  stopTrip(status: "completed" | "partial" = "completed"): Trip | null {
    if (!this.activeTrip) return null;

    this.activeTrip.metadata.endTime = Date.now();
    this.activeTrip.metadata.status = status;
    this.activeTrip.stats.durationSec = Math.floor(
      (this.activeTrip.metadata.endTime - this.activeTrip.metadata.startTime) / 1000
    );

    this.trips.unshift(this.activeTrip);
    const completed = this.activeTrip;
    this.activeTrip = null;
    this.notify();
    return completed;
  }

  updateActiveTrip(sample: TripSample) {
    if (!this.activeTrip) return;

    this.activeTrip.samples.push(sample);
    
    // Update stats
    const samples = this.activeTrip.samples;
    const stats = this.activeTrip.stats;

    stats.durationSec = Math.floor((Date.now() - this.activeTrip.metadata.startTime) / 1000);
    stats.fuelEndPct = sample.fuelPercent;
    stats.fuelUsedPct = stats.fuelStartPct - sample.fuelPercent;
    
    stats.maxSpeedMph = Math.max(stats.maxSpeedMph, sample.speedMph);
    stats.maxRpm = Math.max(stats.maxRpm, sample.rpm);
    stats.maxCoolantF = Math.max(stats.maxCoolantF, sample.coolantF);
    stats.maxLoadPct = Math.max(stats.maxLoadPct, sample.engineLoadPercent);

    // Calculate averages
    stats.avgSpeedMph = samples.reduce((sum, s) => sum + s.speedMph, 0) / samples.length;
    stats.avgRpm = samples.reduce((sum, s) => sum + s.rpm, 0) / samples.length;
    stats.avgCoolantF = samples.reduce((sum, s) => sum + s.coolantF, 0) / samples.length;

    // Estimate distance (simple: avg speed * time)
    stats.distanceMi = (stats.avgSpeedMph * stats.durationSec) / 3600;

    // Count idle time (speed < 5 mph and RPM < 1000)
    stats.idleTimeSec = samples.filter((s) => s.speedMph < 5 && s.rpm < 1000).length * 5;

    this.notify();
  }

  deleteTrip(id: string): boolean {
    const index = this.trips.findIndex((trip) => trip.metadata.id === id);
    if (index >= 0) {
      this.trips.splice(index, 1);
      this.notify();
      return true;
    }
    return false;
  }

  updateTripNotes(id: string, notes: string): boolean {
    const trip = this.trips.find((t) => t.metadata.id === id);
    if (trip) {
      trip.notes = notes;
      this.notify();
      return true;
    }
    return false;
  }
}
