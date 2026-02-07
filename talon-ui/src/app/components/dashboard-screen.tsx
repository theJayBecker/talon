import { useEffect, useState, useRef } from "react";
import { Gauge, Thermometer, Circle, RefreshCw, Settings } from "lucide-react";
import { MockObdDataSource, ObdMetrics, SessionStats } from "../lib/mock-obd-data-source";
import { MetricDetailsModal } from "./metric-details-modal";
import { CircularFuelGauge } from "./circular-fuel-gauge";
import { TripControls } from "./trip-controls";
import { StopTripModal } from "./stop-trip-modal";
import { ConnectScreen } from "./connect-screen";
import { TripRepository } from "../lib/trip-repository";
import { Trip } from "../lib/trip-types";

type ConnectionStatus = "disconnected" | "connecting" | "connected";
type UnitSystem = "us" | "metric";

interface DashboardScreenProps {
  isConnected: boolean;
  tripRepository: TripRepository;
  autoDetect: boolean;
  onToggleAutoDetect: () => void;
  connectionStatus: ConnectionStatus;
  deviceName: string;
  onConnect: () => void;
  onDisconnect: () => void;
}

interface SelectedMetric {
  name: string;
  pid: string;
  value: string;
  min: string;
  max: string;
}

export function DashboardScreen({ 
  isConnected, 
  tripRepository,
  autoDetect,
  onToggleAutoDetect,
  connectionStatus: externalConnectionStatus,
  deviceName,
  onConnect,
  onDisconnect,
}: DashboardScreenProps) {
  const [metrics, setMetrics] = useState<ObdMetrics>({
    fuelPercent: 72.5,
    speedMph: 45.2,
    rpm: 2150,
    coolantF: 195.0,
    engineLoadPercent: 32.8,
    timestamp: Date.now(),
  });

  const [sessionStats, setSessionStats] = useState<SessionStats>({
    speedMin: 0,
    speedMax: 68.5,
    rpmMin: 700,
    rpmMax: 4200,
    coolantMin: 185,
    coolantMax: 205,
    loadMin: 10,
    loadMax: 78,
    fuelMin: 68.2,
    fuelMax: 75.0,
  });

  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>("disconnected");
  const [lastUpdateTime, setLastUpdateTime] = useState<number>(Date.now());
  const [timeSinceUpdate, setTimeSinceUpdate] = useState<string>("0s ago");
  const [unitSystem, setUnitSystem] = useState<UnitSystem>("us");
  const [selectedMetric, setSelectedMetric] = useState<SelectedMetric | null>(null);
  const [showToast, setShowToast] = useState<string | null>(null);
  const [highLoadDuration, setHighLoadDuration] = useState<number>(0);
  const [activeTrip, setActiveTrip] = useState<Trip | null>(null);
  const [showStopTripModal, setShowStopTripModal] = useState(false);

  const dataSourceRef = useRef<MockObdDataSource | null>(null);
  const pressTimerRef = useRef<NodeJS.Timeout | null>(null);
  const statsInitializedRef = useRef(false);
  const tripUpdateIntervalRef = useRef<NodeJS.Timeout | null>(null);

  // Sync active trip from repository
  useEffect(() => {
    const unsubscribe = tripRepository.subscribe(() => {
      setActiveTrip(tripRepository.getActiveTrip());
    });
    setActiveTrip(tripRepository.getActiveTrip());
    return unsubscribe;
  }, [tripRepository]);

  // Update active trip with new metrics
  useEffect(() => {
    if (activeTrip && connectionStatus === "connected") {
      tripRepository.updateActiveTrip({
        timestamp: metrics.timestamp,
        speedMph: metrics.speedMph,
        rpm: metrics.rpm,
        coolantF: metrics.coolantF,
        engineLoadPercent: metrics.engineLoadPercent,
        fuelPercent: metrics.fuelPercent,
      });
    }
  }, [metrics, activeTrip, connectionStatus, tripRepository]);

  const handleStartTrip = () => {
    const recordingMode = autoDetect ? "auto" : "manual";
    tripRepository.startTrip(recordingMode, metrics.fuelPercent);
    setShowToast(`Trip recording started (${recordingMode} mode)`);
  };

  const handleStopTrip = () => {
    if (activeTrip) {
      setShowStopTripModal(true);
    }
  };

  const handleConfirmStopTrip = (status: "completed" | "partial") => {
    tripRepository.stopTrip(status);
    setShowStopTripModal(false);
    setShowToast(`Trip saved as ${status}`);
  };

  // Initialize data source
  useEffect(() => {
    if (!dataSourceRef.current) {
      dataSourceRef.current = new MockObdDataSource();
    }
    return () => {
      if (dataSourceRef.current) {
        dataSourceRef.current.stop();
      }
    };
  }, []);

  // Handle connection state changes
  useEffect(() => {
    if (isConnected && connectionStatus !== "connected") {
      setConnectionStatus("connecting");
      setShowToast("Connecting to OBD-II adapter...");
      setTimeout(() => {
        setConnectionStatus("connected");
        setShowToast("Connected successfully");
        if (dataSourceRef.current) {
          dataSourceRef.current.start();
        }
      }, 1500);
    } else if (!isConnected && connectionStatus !== "disconnected") {
      setConnectionStatus("disconnected");
      setShowToast("Disconnected from adapter");
      if (dataSourceRef.current) {
        dataSourceRef.current.stop();
      }
      statsInitializedRef.current = false;
    }
  }, [isConnected, connectionStatus]);

  // Subscribe to metrics updates
  useEffect(() => {
    if (!dataSourceRef.current) return;

    const unsubscribe = dataSourceRef.current.subscribe((newMetrics) => {
      setMetrics(newMetrics);
      setLastUpdateTime(newMetrics.timestamp);

      // Update session stats
      setSessionStats((prev) => {
        if (!statsInitializedRef.current && connectionStatus === "connected") {
          statsInitializedRef.current = true;
          return {
            speedMin: newMetrics.speedMph,
            speedMax: newMetrics.speedMph,
            rpmMin: newMetrics.rpm,
            rpmMax: newMetrics.rpm,
            coolantMin: newMetrics.coolantF,
            coolantMax: newMetrics.coolantF,
            loadMin: newMetrics.engineLoadPercent,
            loadMax: newMetrics.engineLoadPercent,
            fuelMin: newMetrics.fuelPercent,
            fuelMax: newMetrics.fuelPercent,
          };
        }

        return {
          speedMin: Math.min(prev.speedMin, newMetrics.speedMph),
          speedMax: Math.max(prev.speedMax, newMetrics.speedMph),
          rpmMin: Math.min(prev.rpmMin, newMetrics.rpm),
          rpmMax: Math.max(prev.rpmMax, newMetrics.rpm),
          coolantMin: Math.min(prev.coolantMin, newMetrics.coolantF),
          coolantMax: Math.max(prev.coolantMax, newMetrics.coolantF),
          loadMin: Math.min(prev.loadMin, newMetrics.engineLoadPercent),
          loadMax: Math.max(prev.loadMax, newMetrics.engineLoadPercent),
          fuelMin: Math.min(prev.fuelMin, newMetrics.fuelPercent),
          fuelMax: Math.max(prev.fuelMax, newMetrics.fuelPercent),
        };
      });
    });

    return unsubscribe;
  }, [connectionStatus]);

  // Update "time since last update" display
  useEffect(() => {
    const interval = setInterval(() => {
      const seconds = Math.floor((Date.now() - lastUpdateTime) / 1000);
      if (seconds < 60) {
        setTimeSinceUpdate(`${seconds}s ago`);
      } else {
        const minutes = Math.floor(seconds / 60);
        setTimeSinceUpdate(`${minutes}m ago`);
      }
    }, 1000);

    return () => clearInterval(interval);
  }, [lastUpdateTime]);

  // Track high load duration for warning
  useEffect(() => {
    if (metrics.engineLoadPercent > 85) {
      setHighLoadDuration((prev) => prev + 1);
    } else {
      setHighLoadDuration(0);
    }
  }, [metrics.engineLoadPercent]);

  // Auto-dismiss toast
  useEffect(() => {
    if (showToast) {
      const timer = setTimeout(() => setShowToast(null), 3000);
      return () => clearTimeout(timer);
    }
  }, [showToast]);

  const handleRefresh = () => {
    if (dataSourceRef.current) {
      dataSourceRef.current.forceUpdate();
      setShowToast("Dashboard refreshed");
    }
  };

  const toggleUnits = () => {
    setUnitSystem((prev) => (prev === "us" ? "metric" : "us"));
  };

  const handleMetricLongPress = (metric: SelectedMetric) => {
    setSelectedMetric(metric);
  };

  const handleMetricPressStart = (metric: SelectedMetric) => {
    pressTimerRef.current = setTimeout(() => {
      handleMetricLongPress(metric);
    }, 500);
  };

  const handleMetricPressEnd = () => {
    if (pressTimerRef.current) {
      clearTimeout(pressTimerRef.current);
      pressTimerRef.current = null;
    }
  };

  // Convert values based on unit system
  const convertSpeed = (mph: number) => {
    if (unitSystem === "metric") {
      return { value: (mph * 1.60934).toFixed(1), unit: "km/h" };
    }
    return { value: mph.toFixed(1), unit: "mph" };
  };

  const convertTemp = (fahrenheit: number) => {
    if (unitSystem === "metric") {
      return { value: ((fahrenheit - 32) * 5 / 9).toFixed(1), unit: "°C" };
    }
    return { value: fahrenheit.toFixed(1), unit: "°F" };
  };

  // Determine coolant status
  const getCoolantStatus = () => {
    if (metrics.coolantF > 235) return "danger";
    if (metrics.coolantF > 225) return "warning";
    return "normal";
  };

  const speed = convertSpeed(metrics.speedMph);
  const coolant = convertTemp(metrics.coolantF);
  const coolantStatus = getCoolantStatus();

  // Show connect screen when disconnected
  if (externalConnectionStatus === "disconnected") {
    return (
      <ConnectScreen
        connectionStatus={externalConnectionStatus}
        deviceName={deviceName}
        onConnect={onConnect}
        onDisconnect={onDisconnect}
      />
    );
  }

  return (
    <div className="flex flex-col h-full bg-background">
      {/* Status Bar */}
      <div className="bg-card border-b border-border px-4 py-3 flex items-center justify-between">
        <h2 className="text-lg font-semibold">Dashboard</h2>
        <div className="flex items-center gap-3">
          <button
            onClick={toggleUnits}
            className="p-2 hover:bg-muted rounded-lg transition-colors"
            aria-label="Toggle units"
          >
            <Settings className="w-4 h-4 text-muted-foreground" />
          </button>
          <button
            onClick={handleRefresh}
            className="p-2 hover:bg-muted rounded-lg transition-colors"
            aria-label="Refresh"
          >
            <RefreshCw className="w-4 h-4 text-primary" />
          </button>
        </div>
      </div>

      {/* Main Content */}
      <div className="flex-1 overflow-auto px-4 py-4 space-y-4">
        <>
          {/* Hero Card - Fuel */}
          <div className="bg-card border border-border rounded-lg p-6">
            <div className="flex flex-col items-center space-y-4">
              {/* Title */}
              <div className="text-sm text-muted-foreground uppercase tracking-wide">Fuel</div>
              
              {/* Circular Gauge */}
              <CircularFuelGauge fuelPercent={metrics.fuelPercent} size={180} strokeWidth={14} />
              
              {/* Stats */}
              <div className="w-full grid grid-cols-2 gap-4 pt-4 border-t border-border/50">
                <div className="text-center">
                  <div className="text-xs text-muted-foreground mb-1">Est. Range</div>
                  <div className="text-lg font-semibold text-foreground">— mi</div>
                </div>
                <div className="text-center">
                  <div className="text-xs text-muted-foreground mb-1">Δ Fuel (Trip)</div>
                  <div className="text-lg font-semibold text-foreground">
                    {(sessionStats.fuelMax - metrics.fuelPercent).toFixed(1)}%
                  </div>
                </div>
              </div>
            </div>
          </div>

          {/* 2x2 Metric Grid */}
          <div className="grid grid-cols-2 gap-3">
            {/* Speed */}
            <button
              onMouseDown={() =>
                handleMetricPressStart({
                  name: "Speed",
                  pid: "010D",
                  value: `${speed.value} ${speed.unit}`,
                  min: `${convertSpeed(sessionStats.speedMin).value} ${speed.unit}`,
                  max: `${convertSpeed(sessionStats.speedMax).value} ${speed.unit}`,
                })
              }
              onMouseUp={handleMetricPressEnd}
              onMouseLeave={handleMetricPressEnd}
              onTouchStart={() =>
                handleMetricPressStart({
                  name: "Speed",
                  pid: "010D",
                  value: `${speed.value} ${speed.unit}`,
                  min: `${convertSpeed(sessionStats.speedMin).value} ${speed.unit}`,
                  max: `${convertSpeed(sessionStats.speedMax).value} ${speed.unit}`,
                })
              }
              onTouchEnd={handleMetricPressEnd}
              className="bg-card border border-border rounded-lg p-4 text-left hover:border-primary/30 transition-colors active:scale-95"
            >
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground uppercase tracking-wide">Speed</span>
                <Gauge className="w-4 h-4 text-primary/40" />
              </div>
              <div className="flex items-baseline gap-1">
                <span className="text-3xl font-semibold text-foreground tabular-nums">
                  {speed.value}
                </span>
                <span className="text-sm text-muted-foreground">{speed.unit}</span>
              </div>
            </button>

            {/* RPM */}
            <button
              onMouseDown={() =>
                handleMetricPressStart({
                  name: "RPM",
                  pid: "010C",
                  value: `${metrics.rpm.toFixed(0)} rpm`,
                  min: `${sessionStats.rpmMin.toFixed(0)} rpm`,
                  max: `${sessionStats.rpmMax.toFixed(0)} rpm`,
                })
              }
              onMouseUp={handleMetricPressEnd}
              onMouseLeave={handleMetricPressEnd}
              onTouchStart={() =>
                handleMetricPressStart({
                  name: "RPM",
                  pid: "010C",
                  value: `${metrics.rpm.toFixed(0)} rpm`,
                  min: `${sessionStats.rpmMin.toFixed(0)} rpm`,
                  max: `${sessionStats.rpmMax.toFixed(0)} rpm`,
                })
              }
              onTouchEnd={handleMetricPressEnd}
              className="bg-card border border-border rounded-lg p-4 text-left hover:border-primary/30 transition-colors active:scale-95"
            >
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground uppercase tracking-wide">RPM</span>
                <Gauge className="w-4 h-4 text-primary/40" />
              </div>
              <div className="flex items-baseline gap-1">
                <span className="text-3xl font-semibold text-foreground tabular-nums">
                  {metrics.rpm.toFixed(0)}
                </span>
                <span className="text-sm text-muted-foreground">rpm</span>
              </div>
            </button>

            {/* Coolant Temp */}
            <button
              onMouseDown={() =>
                handleMetricPressStart({
                  name: "Coolant Temp",
                  pid: "0105",
                  value: `${coolant.value} ${coolant.unit}`,
                  min: `${convertTemp(sessionStats.coolantMin).value} ${coolant.unit}`,
                  max: `${convertTemp(sessionStats.coolantMax).value} ${coolant.unit}`,
                })
              }
              onMouseUp={handleMetricPressEnd}
              onMouseLeave={handleMetricPressEnd}
              onTouchStart={() =>
                handleMetricPressStart({
                  name: "Coolant Temp",
                  pid: "0105",
                  value: `${coolant.value} ${coolant.unit}`,
                  min: `${convertTemp(sessionStats.coolantMin).value} ${coolant.unit}`,
                  max: `${convertTemp(sessionStats.coolantMax).value} ${coolant.unit}`,
                })
              }
              onTouchEnd={handleMetricPressEnd}
              className={`bg-card border rounded-lg p-4 text-left hover:border-primary/30 transition-colors active:scale-95 ${
                coolantStatus === "danger"
                  ? "border-destructive"
                  : coolantStatus === "warning"
                  ? "border-secondary"
                  : "border-border"
              }`}
            >
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground uppercase tracking-wide">
                  Coolant Temp
                </span>
                <Thermometer
                  className={`w-4 h-4 ${
                    coolantStatus === "danger"
                      ? "text-destructive"
                      : coolantStatus === "warning"
                      ? "text-secondary"
                      : "text-primary/40"
                  }`}
                />
              </div>
              <div className="flex items-baseline gap-1">
                <span className="text-3xl font-semibold text-foreground tabular-nums">
                  {coolant.value}
                </span>
                <span className="text-sm text-muted-foreground">{coolant.unit}</span>
              </div>
            </button>

            {/* Engine Load */}
            <button
              onMouseDown={() =>
                handleMetricPressStart({
                  name: "Engine Load",
                  pid: "0104",
                  value: `${metrics.engineLoadPercent.toFixed(1)}%`,
                  min: `${sessionStats.loadMin.toFixed(1)}%`,
                  max: `${sessionStats.loadMax.toFixed(1)}%`,
                })
              }
              onMouseUp={handleMetricPressEnd}
              onMouseLeave={handleMetricPressEnd}
              onTouchStart={() =>
                handleMetricPressStart({
                  name: "Engine Load",
                  pid: "0104",
                  value: `${metrics.engineLoadPercent.toFixed(1)}%`,
                  min: `${sessionStats.loadMin.toFixed(1)}%`,
                  max: `${sessionStats.loadMax.toFixed(1)}%`,
                })
              }
              onTouchEnd={handleMetricPressEnd}
              className={`bg-card border rounded-lg p-4 text-left hover:border-primary/30 transition-colors active:scale-95 ${
                highLoadDuration > 5 ? "border-secondary" : "border-border"
              }`}
            >
              <div className="flex items-center justify-between mb-2">
                <span className="text-xs text-muted-foreground uppercase tracking-wide">
                  Engine Load
                </span>
                <Gauge
                  className={`w-4 h-4 ${
                    highLoadDuration > 5 ? "text-secondary" : "text-primary/40"
                  }`}
                />
              </div>
              <div className="flex items-baseline gap-1">
                <span className="text-3xl font-semibold text-foreground tabular-nums">
                  {metrics.engineLoadPercent.toFixed(1)}
                </span>
                <span className="text-sm text-muted-foreground">%</span>
              </div>
            </button>
          </div>

          {/* Trip Controls */}
          <TripControls
            activeTrip={activeTrip}
            autoDetect={autoDetect}
            onStartTrip={handleStartTrip}
            onStopTrip={handleStopTrip}
            onToggleAutoDetect={onToggleAutoDetect}
          />

          {/* Status Strip */}
          <div className="bg-card border border-border rounded-lg p-3 space-y-2">
            <div className="flex items-center justify-between text-xs">
              <div className="flex items-center gap-2">
                <Circle className={`w-2 h-2 ${
                  connectionStatus === "connected"
                    ? "text-green-500 fill-green-500"
                    : connectionStatus === "connecting"
                    ? "text-secondary fill-secondary animate-pulse"
                    : "text-muted-foreground fill-muted-foreground"
                }`} />
                <span className="text-muted-foreground">
                  {connectionStatus === "connected"
                    ? "Connected"
                    : connectionStatus === "connecting"
                    ? "Connecting..."
                    : "Disconnected"}
                </span>
              </div>
              <div className="text-muted-foreground">Updated: {timeSinceUpdate}</div>
            </div>
            <div className="flex items-center justify-between text-xs">
              <div className="bg-secondary/20 text-secondary px-2 py-0.5 rounded">
                Mock Data
              </div>
              <div className="text-muted-foreground">Logging: Off</div>
            </div>
          </div>
          
          {connectionStatus === "disconnected" && (
            <div className="bg-muted/30 border border-border/50 rounded-lg p-3 text-center">
              <p className="text-sm text-muted-foreground">
                Showing preview data. Connect from the Connect tab for live updates.
              </p>
            </div>
          )}
        </>
      </div>

      {/* Toast Notification */}
      {showToast && (
        <div className="fixed bottom-20 left-1/2 -translate-x-1/2 bg-card border border-border rounded-lg px-4 py-2 shadow-lg animate-in slide-in-from-bottom duration-200">
          <p className="text-sm text-foreground">{showToast}</p>
        </div>
      )}

      {/* Metric Details Modal */}
      <MetricDetailsModal
        isOpen={selectedMetric !== null}
        onClose={() => setSelectedMetric(null)}
        metricName={selectedMetric?.name || ""}
        pid={selectedMetric?.pid || ""}
        currentValue={selectedMetric?.value || ""}
        min={selectedMetric?.min || ""}
        max={selectedMetric?.max || ""}
        lastUpdated={timeSinceUpdate}
      />

      {/* Stop Trip Modal */}
      <StopTripModal
        isOpen={showStopTripModal}
        onClose={() => setShowStopTripModal(false)}
        onConfirm={handleConfirmStopTrip}
        duration={activeTrip?.stats.durationSec || 0}
        distance={activeTrip?.stats.distanceMi || 0}
      />
    </div>
  );
}