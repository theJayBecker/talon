import { X } from "lucide-react";

interface MetricDetailsModalProps {
  isOpen: boolean;
  onClose: () => void;
  metricName: string;
  pid: string;
  currentValue: string;
  min: string;
  max: string;
  lastUpdated: string;
}

export function MetricDetailsModal({
  isOpen,
  onClose,
  metricName,
  pid,
  currentValue,
  min,
  max,
  lastUpdated,
}: MetricDetailsModalProps) {
  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center">
      {/* Backdrop */}
      <div
        className="absolute inset-0 bg-black/60"
        onClick={onClose}
      />

      {/* Modal Sheet */}
      <div className="relative w-full max-w-md bg-card border-t border-border rounded-t-2xl p-6 space-y-4 animate-in slide-in-from-bottom duration-200">
        {/* Header */}
        <div className="flex items-center justify-between">
          <h3 className="text-lg font-semibold">{metricName}</h3>
          <button
            onClick={onClose}
            className="p-1 hover:bg-muted rounded-lg transition-colors"
          >
            <X className="w-5 h-5" />
          </button>
        </div>

        {/* Content */}
        <div className="space-y-4">
          {/* PID */}
          <div className="bg-muted/50 rounded-lg p-3">
            <div className="text-xs text-muted-foreground uppercase">PID</div>
            <div className="text-sm font-mono text-primary mt-1">{pid}</div>
          </div>

          {/* Current Value */}
          <div>
            <div className="text-xs text-muted-foreground uppercase mb-1">Current Value</div>
            <div className="text-3xl font-semibold text-foreground">{currentValue}</div>
          </div>

          {/* Session Stats */}
          <div className="grid grid-cols-2 gap-3">
            <div className="bg-muted/30 rounded-lg p-3">
              <div className="text-xs text-muted-foreground uppercase">Session Min</div>
              <div className="text-lg font-semibold text-foreground mt-1">{min}</div>
            </div>
            <div className="bg-muted/30 rounded-lg p-3">
              <div className="text-xs text-muted-foreground uppercase">Session Max</div>
              <div className="text-lg font-semibold text-foreground mt-1">{max}</div>
            </div>
          </div>

          {/* Last Updated */}
          <div className="text-xs text-muted-foreground text-center pt-2">
            Last updated: {lastUpdated}
          </div>
        </div>
      </div>
    </div>
  );
}
