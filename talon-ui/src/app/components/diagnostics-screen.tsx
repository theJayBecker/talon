import { AlertCircle, Trash2, CheckCircle, XCircle, HelpCircle, RefreshCw } from "lucide-react";
import { useState, useEffect, useRef } from "react";
import { CollapsibleSection } from "./collapsible-section";
import { DiagnosticMetricRow } from "./diagnostic-metric-row";
import { MockDiagnosticsDataSource } from "../lib/mock-diagnostics-data";
import { DiagnosticsState, ReadinessStatus } from "../lib/diagnostics-types";

interface DiagnosticsScreenProps {
  isConnected: boolean;
  troubleCodes?: Array<{ code: string; description: string }>;
  onReadCodes?: () => void;
  onClearCodes?: () => void;
}

export function DiagnosticsScreen({
  isConnected,
}: DiagnosticsScreenProps) {
  const [diagnosticsState, setDiagnosticsState] = useState<DiagnosticsState | null>(null);
  const [isClearing, setIsClearing] = useState(false);
  const [holdProgress, setHoldProgress] = useState(0);
  const dataSourceRef = useRef<MockDiagnosticsDataSource | null>(null);
  const clearIntervalRef = useRef<NodeJS.Timeout | null>(null);

  // Initialize data source
  useEffect(() => {
    if (!dataSourceRef.current) {
      dataSourceRef.current = new MockDiagnosticsDataSource();
    }

    const dataSource = dataSourceRef.current;
    const unsubscribe = dataSource.subscribe(setDiagnosticsState);
    
    // Set initial state
    setDiagnosticsState(dataSource.getState());

    // Start updates when connected
    if (isConnected) {
      dataSource.setConnectionMode("connected");
      dataSource.start();
    } else {
      dataSource.setConnectionMode("disconnected");
      dataSource.stop();
    }

    return () => {
      unsubscribe();
      dataSource.stop();
    };
  }, [isConnected]);

  const handleReadCodes = () => {
    dataSourceRef.current?.readTroubleCodes();
  };

  const handleClearPress = () => {
    if (!isConnected || !diagnosticsState || diagnosticsState.troubleCodes.length === 0) return;
    
    setIsClearing(true);
    let progress = 0;
    
    clearIntervalRef.current = setInterval(() => {
      progress += 5;
      setHoldProgress(progress);
      if (progress >= 100) {
        if (clearIntervalRef.current) {
          clearInterval(clearIntervalRef.current);
        }
        dataSourceRef.current?.clearTroubleCodes();
        setIsClearing(false);
        setHoldProgress(0);
      }
    }, 50); // 2 seconds total (100 / 5 * 50ms)
  };

  const handleClearRelease = () => {
    if (clearIntervalRef.current) {
      clearInterval(clearIntervalRef.current);
      clearIntervalRef.current = null;
    }
    setIsClearing(false);
    setHoldProgress(0);
  };

  const getReadinessIcon = (status: ReadinessStatus) => {
    switch (status) {
      case "ready":
        return <CheckCircle className="w-4 h-4 text-green-500" />;
      case "not-ready":
        return <XCircle className="w-4 h-4 text-secondary" />;
      case "not-supported":
        return <HelpCircle className="w-4 h-4 text-muted-foreground/50" />;
    }
  };

  const getReadinessText = (status: ReadinessStatus) => {
    switch (status) {
      case "ready":
        return "Ready";
      case "not-ready":
        return "Not Ready";
      case "not-supported":
        return "Not Supported";
    }
  };

  if (!diagnosticsState) {
    return (
      <div className="flex items-center justify-center h-full">
        <div className="text-center">
          <RefreshCw className="w-8 h-8 text-muted-foreground animate-spin mx-auto mb-2" />
          <p className="text-sm text-muted-foreground">Loading diagnostics...</p>
        </div>
      </div>
    );
  }

  return (
    <div className="flex flex-col h-full bg-background">
      {/* Header */}
      <div className="bg-card border-b border-border px-4 py-3">
        <h2 className="text-lg font-semibold">Diagnostics</h2>
        <p className="text-xs text-muted-foreground mt-1">
          2013 Hyundai Sonata GLS - {diagnosticsState.connectionMode === "mock" ? "Mock Data" : isConnected ? "Connected" : "Disconnected"}
        </p>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-auto px-4 py-4 space-y-3">
        {/* Vehicle Status - Always Visible */}
        <div className="bg-card border border-border rounded-lg p-4 space-y-3">
          <h3 className="text-sm font-semibold text-foreground">Vehicle Status</h3>
          
          <div className="grid grid-cols-2 gap-3">
            {/* MIL Status */}
            <div className="space-y-1">
              <div className="text-xs text-muted-foreground uppercase tracking-wide">
                Check Engine Light
              </div>
              <div className="flex items-center gap-2">
                <div
                  className={`w-2.5 h-2.5 rounded-full ${
                    diagnosticsState.milOn ? "bg-secondary animate-pulse" : "bg-muted-foreground/30"
                  }`}
                />
                <span className={`text-sm font-semibold ${diagnosticsState.milOn ? "text-secondary" : "text-foreground"}`}>
                  {diagnosticsState.milOn ? "ON" : "OFF"}
                </span>
              </div>
            </div>

            {/* DTC Count */}
            <div className="space-y-1">
              <div className="text-xs text-muted-foreground uppercase tracking-wide">
                Stored DTCs
              </div>
              <div className="text-sm font-semibold text-foreground tabular-nums">
                {diagnosticsState.dtcCount}
              </div>
            </div>

            {/* Engine Runtime */}
            <div className="space-y-1">
              <div className="text-xs text-muted-foreground uppercase tracking-wide">
                Runtime Since Start
              </div>
              <div className="text-sm font-semibold text-foreground tabular-nums">
                {diagnosticsState.engineRuntimeMin} min
              </div>
            </div>

            {/* Connection Mode */}
            <div className="space-y-1">
              <div className="text-xs text-muted-foreground uppercase tracking-wide">
                Connection
              </div>
              <div className="text-sm font-semibold text-foreground capitalize">
                {diagnosticsState.connectionMode}
              </div>
            </div>
          </div>
        </div>

        {/* Engine & Performance */}
        <CollapsibleSection title="Engine & Performance" defaultOpen={true}>
          <div className="divide-y divide-border">
            <DiagnosticMetricRow metric={diagnosticsState.engineMetrics.rpm} />
            <DiagnosticMetricRow metric={diagnosticsState.engineMetrics.speed} />
            <DiagnosticMetricRow metric={diagnosticsState.engineMetrics.engineLoad} />
            <DiagnosticMetricRow metric={diagnosticsState.engineMetrics.throttlePosition} />
            <DiagnosticMetricRow metric={diagnosticsState.engineMetrics.timingAdvance} />
            <DiagnosticMetricRow metric={diagnosticsState.engineMetrics.intakeManifoldPressure} />
            <DiagnosticMetricRow metric={diagnosticsState.engineMetrics.intakeAirTemp} />
            <DiagnosticMetricRow metric={diagnosticsState.engineMetrics.massAirFlow} />
          </div>
        </CollapsibleSection>

        {/* Fuel System */}
        <CollapsibleSection title="Fuel System" defaultOpen={false}>
          <div className="divide-y divide-border">
            <DiagnosticMetricRow metric={diagnosticsState.fuelMetrics.fuelLevel} />
            <DiagnosticMetricRow metric={diagnosticsState.fuelMetrics.fuelPressure} />
            <DiagnosticMetricRow metric={diagnosticsState.fuelMetrics.shortTermFuelTrimBank1} />
            <DiagnosticMetricRow metric={diagnosticsState.fuelMetrics.longTermFuelTrimBank1} />
            <DiagnosticMetricRow metric={diagnosticsState.fuelMetrics.shortTermFuelTrimBank2} />
            <DiagnosticMetricRow metric={diagnosticsState.fuelMetrics.longTermFuelTrimBank2} />
          </div>
        </CollapsibleSection>

        {/* Emissions & Sensors */}
        <CollapsibleSection title="Emissions & Sensors" defaultOpen={false}>
          <div className="p-4 space-y-4">
            {/* Oxygen Sensors */}
            <div>
              <h4 className="text-xs text-muted-foreground uppercase tracking-wide mb-2">
                Oxygen Sensors
              </h4>
              <div className="space-y-2">
                {diagnosticsState.emissionsData.oxygenSensors.map((sensor, index) => (
                  <div
                    key={index}
                    className="flex items-center justify-between py-2 px-3 bg-muted/20 rounded"
                  >
                    <span className="text-sm text-foreground">
                      Bank {sensor.bank} Sensor {sensor.sensor}
                    </span>
                    <span className={`text-sm font-semibold tabular-nums ${sensor.supported ? "text-foreground" : "text-muted-foreground/50"}`}>
                      {sensor.supported && sensor.voltage !== null
                        ? `${sensor.voltage.toFixed(2)} V`
                        : "—"}
                    </span>
                  </div>
                ))}
              </div>
            </div>

            {/* Catalyst Temperature */}
            <div className="pt-2 border-t border-border">
              <DiagnosticMetricRow metric={diagnosticsState.emissionsData.catalystTempBank1} />
            </div>

            {/* Readiness Monitors */}
            <div className="pt-2 border-t border-border">
              <h4 className="text-xs text-muted-foreground uppercase tracking-wide mb-2">
                Readiness Monitors
              </h4>
              <div className="space-y-1">
                {diagnosticsState.emissionsData.readinessMonitors.map((monitor, index) => (
                  <div
                    key={index}
                    className="flex items-center justify-between py-2 px-3 hover:bg-muted/20 rounded transition-colors"
                  >
                    <span className="text-sm text-foreground">{monitor.name}</span>
                    <div className="flex items-center gap-2">
                      {getReadinessIcon(monitor.status)}
                      <span className={`text-xs ${
                        monitor.status === "ready" ? "text-green-500" :
                        monitor.status === "not-ready" ? "text-secondary" :
                        "text-muted-foreground/70"
                      }`}>
                        {getReadinessText(monitor.status)}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          </div>
        </CollapsibleSection>

        {/* Trouble Codes */}
        <CollapsibleSection 
          title="Trouble Codes" 
          defaultOpen={true}
          badge={diagnosticsState.dtcCount > 0 ? diagnosticsState.dtcCount : undefined}
        >
          <div className="p-4 space-y-4">
            {/* Read Codes Button */}
            <button
              onClick={handleReadCodes}
              disabled={!isConnected}
              className="w-full bg-primary text-primary-foreground px-4 py-2.5 rounded-lg text-sm font-medium hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-all flex items-center justify-center gap-2"
            >
              <RefreshCw className="w-4 h-4" />
              Read Codes
            </button>

            {/* Codes List */}
            {diagnosticsState.troubleCodes.length === 0 ? (
              <div className="bg-muted/30 border border-border rounded-lg p-6 text-center space-y-2">
                <CheckCircle className="w-8 h-8 text-green-500 mx-auto" />
                <p className="text-sm text-foreground font-medium">No trouble codes detected</p>
                <p className="text-xs text-muted-foreground">Vehicle systems operating normally</p>
              </div>
            ) : (
              <div className="space-y-2">
                {diagnosticsState.troubleCodes.map((code, index) => (
                  <div
                    key={index}
                    className="bg-secondary/10 border border-secondary/30 rounded-lg p-3 space-y-1"
                  >
                    <div className="flex items-center gap-2">
                      <AlertCircle className="w-4 h-4 text-secondary" />
                      <span className="font-mono text-base text-secondary font-semibold">
                        {code.code}
                      </span>
                      <span className="text-xs text-muted-foreground px-2 py-0.5 bg-muted/50 rounded">
                        {code.type}
                      </span>
                    </div>
                    <p className="text-sm text-foreground pl-6">{code.description}</p>
                  </div>
                ))}
              </div>
            )}

            {/* Clear Codes Button */}
            {diagnosticsState.troubleCodes.length > 0 && (
              <div className="pt-2 space-y-2">
                <button
                  onMouseDown={handleClearPress}
                  onMouseUp={handleClearRelease}
                  onMouseLeave={handleClearRelease}
                  onTouchStart={handleClearPress}
                  onTouchEnd={handleClearRelease}
                  disabled={!isConnected}
                  className="relative w-full bg-destructive/10 text-destructive border border-destructive/30 py-3 px-4 rounded-lg hover:bg-destructive/20 disabled:opacity-50 disabled:cursor-not-allowed transition-all overflow-hidden"
                >
                  {/* Progress indicator */}
                  {isClearing && (
                    <div
                      className="absolute inset-0 bg-destructive/30 transition-all"
                      style={{ width: `${holdProgress}%` }}
                    />
                  )}
                  <div className="relative flex items-center justify-center gap-2">
                    <Trash2 className="w-4 h-4" />
                    <span className="font-medium">
                      {isClearing ? "Hold to confirm..." : "Clear All Codes"}
                    </span>
                  </div>
                </button>
                <p className="text-xs text-muted-foreground text-center">
                  ⚠️ Press and hold for 2 seconds. Clearing codes may reset readiness monitors.
                </p>
              </div>
            )}
          </div>
        </CollapsibleSection>
      </div>
    </div>
  );
}
