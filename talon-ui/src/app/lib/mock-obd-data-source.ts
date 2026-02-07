export interface ObdMetrics {
  fuelPercent: number;
  speedMph: number;
  rpm: number;
  coolantF: number;
  engineLoadPercent: number;
  timestamp: number;
}

export interface SessionStats {
  speedMin: number;
  speedMax: number;
  rpmMin: number;
  rpmMax: number;
  coolantMin: number;
  coolantMax: number;
  loadMin: number;
  loadMax: number;
  fuelMin: number;
  fuelMax: number;
}

type MetricsCallback = (metrics: ObdMetrics) => void;

export class MockObdDataSource {
  private callbacks: Set<MetricsCallback> = new Set();
  private intervalIds: NodeJS.Timeout[] = [];
  private isRunning = false;

  // Current state
  private currentMetrics: ObdMetrics = {
    fuelPercent: 75,
    speedMph: 0,
    rpm: 0,
    coolantF: 75,
    engineLoadPercent: 0,
    timestamp: Date.now(),
  };

  // Simulation targets for smooth transitions
  private targetSpeed = 0;
  private targetRpm = 0;
  private targetLoad = 0;
  private coolantWarmedUp = false;
  private fuelDrainAccumulator = 0;

  subscribe(callback: MetricsCallback): () => void {
    this.callbacks.add(callback);
    // Immediately send current state
    callback({ ...this.currentMetrics });
    
    return () => {
      this.callbacks.delete(callback);
    };
  }

  start(): void {
    if (this.isRunning) return;
    this.isRunning = true;

    // High frequency updates (2 Hz) - Speed & RPM
    const fastInterval = setInterval(() => {
      this.updateSpeedAndRpm();
      this.emitMetrics();
    }, 500);

    // Medium frequency updates (1 Hz) - Coolant & Engine Load
    const mediumInterval = setInterval(() => {
      this.updateCoolantAndLoad();
      this.emitMetrics();
    }, 1000);

    // Slow updates (every 7 seconds) - Fuel
    const slowInterval = setInterval(() => {
      this.updateFuel();
      this.emitMetrics();
    }, 7000);

    this.intervalIds.push(fastInterval, mediumInterval, slowInterval);
  }

  stop(): void {
    this.isRunning = false;
    this.intervalIds.forEach(clearInterval);
    this.intervalIds = [];
    
    // Reset to idle state
    this.currentMetrics.speedMph = 0;
    this.currentMetrics.rpm = 0;
    this.currentMetrics.engineLoadPercent = 0;
    this.emitMetrics();
  }

  private updateSpeedAndRpm(): void {
    // Simulate realistic driving patterns
    const rand = Math.random();
    
    // Occasionally change target speed
    if (rand < 0.05) {
      this.targetSpeed = Math.random() * 75;
      
      // RPM correlates with speed but with variation
      const baseRpm = this.targetSpeed > 0 ? 1500 + (this.targetSpeed * 30) : 700;
      const variance = Math.random() * 800 - 400;
      this.targetRpm = Math.max(700, Math.min(4500, baseRpm + variance));
      
      // Occasional spike
      if (Math.random() < 0.1) {
        this.targetRpm = Math.min(5000, this.targetRpm + 1000);
      }
    }

    // Smooth transitions
    this.currentMetrics.speedMph += (this.targetSpeed - this.currentMetrics.speedMph) * 0.15;
    this.currentMetrics.rpm += (this.targetRpm - this.currentMetrics.rpm) * 0.2;

    // Add small noise
    this.currentMetrics.speedMph += (Math.random() - 0.5) * 1.5;
    this.currentMetrics.rpm += (Math.random() - 0.5) * 50;

    // Clamp values
    this.currentMetrics.speedMph = Math.max(0, Math.min(85, this.currentMetrics.speedMph));
    this.currentMetrics.rpm = Math.max(0, Math.min(6000, this.currentMetrics.rpm));

    // When idling, set rpm to ~700
    if (this.currentMetrics.speedMph < 2) {
      this.currentMetrics.rpm = 650 + Math.random() * 100;
    }
  }

  private updateCoolantAndLoad(): void {
    // Coolant warms up over time
    if (!this.coolantWarmedUp) {
      this.currentMetrics.coolantF += (190 - this.currentMetrics.coolantF) * 0.05;
      if (this.currentMetrics.coolantF > 185) {
        this.coolantWarmedUp = true;
      }
    } else {
      // Normal operating temp with small fluctuations
      const targetTemp = 195 + Math.random() * 10;
      this.currentMetrics.coolantF += (targetTemp - this.currentMetrics.coolantF) * 0.1;
      
      // Occasional spike
      if (Math.random() < 0.02) {
        this.currentMetrics.coolantF = Math.min(235, this.currentMetrics.coolantF + 15);
      }
    }

    // Engine load correlates with speed/rpm
    const speedFactor = this.currentMetrics.speedMph / 75;
    const rpmFactor = Math.max(0, (this.currentMetrics.rpm - 700) / 4000);
    this.targetLoad = (speedFactor * 0.4 + rpmFactor * 0.6) * 70 + 10;
    
    // Occasional spike
    if (Math.random() < 0.05) {
      this.targetLoad = Math.min(95, this.targetLoad + 20);
    }

    this.currentMetrics.engineLoadPercent += (this.targetLoad - this.currentMetrics.engineLoadPercent) * 0.2;
    this.currentMetrics.engineLoadPercent = Math.max(0, Math.min(100, this.currentMetrics.engineLoadPercent));
  }

  private updateFuel(): void {
    // Fuel decreases slowly based on engine load and speed
    const drainRate = (this.currentMetrics.engineLoadPercent / 100) * 0.3 + 
                     (this.currentMetrics.speedMph / 75) * 0.2;
    
    this.fuelDrainAccumulator += drainRate;
    
    // Only update fuel when accumulated enough
    if (this.fuelDrainAccumulator > 0.5) {
      this.currentMetrics.fuelPercent -= this.fuelDrainAccumulator;
      this.fuelDrainAccumulator = 0;
    }

    // Add tiny noise for realism
    this.currentMetrics.fuelPercent += (Math.random() - 0.5) * 0.3;
    
    // Clamp
    this.currentMetrics.fuelPercent = Math.max(5, Math.min(100, this.currentMetrics.fuelPercent));
  }

  private emitMetrics(): void {
    this.currentMetrics.timestamp = Date.now();
    const snapshot = { ...this.currentMetrics };
    this.callbacks.forEach(cb => cb(snapshot));
  }

  // For manual refresh
  forceUpdate(): void {
    this.emitMetrics();
  }

  // Reset to fresh state
  reset(): void {
    this.currentMetrics = {
      fuelPercent: 75,
      speedMph: 0,
      rpm: 0,
      coolantF: 75,
      engineLoadPercent: 0,
      timestamp: Date.now(),
    };
    this.targetSpeed = 0;
    this.targetRpm = 0;
    this.targetLoad = 0;
    this.coolantWarmedUp = false;
    this.fuelDrainAccumulator = 0;
    this.emitMetrics();
  }
}
