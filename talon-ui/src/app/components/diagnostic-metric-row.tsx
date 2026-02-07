import { DiagnosticMetric } from "../lib/diagnostics-types";

interface DiagnosticMetricRowProps {
  metric: DiagnosticMetric;
  showPid?: boolean;
}

export function DiagnosticMetricRow({ metric, showPid = false }: DiagnosticMetricRowProps) {
  const getStatusColor = () => {
    if (!metric.supported) return "text-muted-foreground/50";
    
    switch (metric.status) {
      case "critical":
        return "text-destructive";
      case "warning":
        return "text-secondary";
      case "normal":
        return "text-foreground";
      default:
        return "text-muted-foreground";
    }
  };

  const getStatusIndicator = () => {
    if (!metric.supported) return null;
    
    switch (metric.status) {
      case "critical":
        return <div className="w-2 h-2 rounded-full bg-destructive animate-pulse" />;
      case "warning":
        return <div className="w-2 h-2 rounded-full bg-secondary" />;
      default:
        return null;
    }
  };

  return (
    <div className="flex items-center justify-between py-3 px-4 hover:bg-muted/20 transition-colors">
      <div className="flex items-center gap-2 flex-1">
        {getStatusIndicator()}
        <div>
          <div className="text-sm text-foreground">{metric.label}</div>
          {showPid && (
            <div className="text-xs text-muted-foreground font-mono">{metric.pid}</div>
          )}
        </div>
      </div>
      <div className={`text-right tabular-nums ${getStatusColor()}`}>
        <span className="text-base font-semibold">
          {metric.supported ? metric.value : "—"}
        </span>
        {metric.supported && (
          <span className="text-sm text-muted-foreground ml-1">{metric.unit}</span>
        )}
      </div>
    </div>
  );
}
