import {
  ArrowLeft,
  Clock,
  MapPin,
  Droplet,
  Gauge,
  Thermometer,
  Download,
  Trash2,
} from "lucide-react";
import { Trip } from "../lib/trip-types";
import { useState } from "react";
import { ExportBottomSheet } from "./export-bottom-sheet";

interface TripDetailScreenProps {
  trip: Trip;
  onBack: () => void;
  onDelete: (id: string) => void;
}

export function TripDetailScreen({ trip, onBack, onDelete }: TripDetailScreenProps) {
  const [showExport, setShowExport] = useState(false);
  const [showDeleteConfirm, setShowDeleteConfirm] = useState(false);

  const formatDate = (timestamp: number): string => {
    return new Date(timestamp).toLocaleDateString(undefined, {
      weekday: "short",
      month: "short",
      day: "numeric",
      year: "numeric",
    });
  };

  const formatTime = (timestamp: number): string => {
    return new Date(timestamp).toLocaleTimeString(undefined, {
      hour: "2-digit",
      minute: "2-digit",
    });
  };

  const formatDuration = (seconds: number): string => {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    if (hours > 0) return `${hours}h ${minutes}m ${secs}s`;
    if (minutes > 0) return `${minutes}m ${secs}s`;
    return `${secs}s`;
  };

  const handleDelete = () => {
    onDelete(trip.metadata.id);
    onBack();
  };

  return (
    <div className="flex flex-col h-full bg-background">
      {/* Header */}
      <div className="bg-card border-b border-border px-4 py-3">
        <div className="flex items-center gap-3">
          <button
            onClick={onBack}
            className="p-2 -ml-2 hover:bg-muted rounded-lg transition-colors"
          >
            <ArrowLeft className="w-5 h-5 text-foreground" />
          </button>
          <div className="flex-1">
            <h2 className="text-lg font-semibold">Trip Details</h2>
            <p className="text-xs text-muted-foreground">
              {formatDate(trip.metadata.startTime)} at {formatTime(trip.metadata.startTime)}
            </p>
          </div>
        </div>
      </div>

      {/* Content */}
      <div className="flex-1 overflow-auto px-4 py-4 space-y-4">
        {/* Status Badge */}
        {trip.metadata.status !== "completed" && (
          <div className="flex items-center gap-2">
            <div
              className={`px-3 py-1 rounded-lg text-sm font-medium ${
                trip.metadata.status === "recording"
                  ? "bg-destructive/20 text-destructive"
                  : "bg-secondary/20 text-secondary"
              }`}
            >
              {trip.metadata.status === "recording" ? "Recording" : "Partial Trip"}
            </div>
          </div>
        )}

        {/* Summary Cards Grid */}
        <div className="grid grid-cols-2 gap-3">
          {/* Duration */}
          <div className="bg-card border border-border rounded-lg p-3">
            <div className="flex items-center gap-2 mb-2">
              <Clock className="w-4 h-4 text-primary/40" />
              <span className="text-xs text-muted-foreground uppercase tracking-wide">
                Duration
              </span>
            </div>
            <div className="text-xl font-semibold text-foreground tabular-nums">
              {formatDuration(trip.stats.durationSec)}
            </div>
          </div>

          {/* Distance */}
          <div className="bg-card border border-border rounded-lg p-3">
            <div className="flex items-center gap-2 mb-2">
              <MapPin className="w-4 h-4 text-primary/40" />
              <span className="text-xs text-muted-foreground uppercase tracking-wide">
                Distance
              </span>
            </div>
            <div className="text-xl font-semibold text-foreground tabular-nums">
              {trip.stats.distanceMi.toFixed(1)}
              <span className="text-sm text-muted-foreground ml-1">mi</span>
            </div>
          </div>

          {/* Fuel Used */}
          <div className="bg-card border border-border rounded-lg p-3">
            <div className="flex items-center gap-2 mb-2">
              <Droplet className="w-4 h-4 text-primary/40" />
              <span className="text-xs text-muted-foreground uppercase tracking-wide">
                Fuel Used
              </span>
            </div>
            <div className="text-xl font-semibold text-foreground tabular-nums">
              {trip.stats.fuelUsedPct.toFixed(1)}
              <span className="text-sm text-muted-foreground ml-1">%</span>
            </div>
            <div className="text-xs text-muted-foreground mt-1">
              {trip.stats.fuelStartPct.toFixed(1)}% → {trip.stats.fuelEndPct.toFixed(1)}%
            </div>
          </div>

          {/* Idle Time */}
          <div className="bg-card border border-border rounded-lg p-3">
            <div className="flex items-center gap-2 mb-2">
              <Clock className="w-4 h-4 text-primary/40" />
              <span className="text-xs text-muted-foreground uppercase tracking-wide">
                Idle Time
              </span>
            </div>
            <div className="text-xl font-semibold text-foreground tabular-nums">
              {formatDuration(trip.stats.idleTimeSec)}
            </div>
          </div>
        </div>

        {/* Speed Stats */}
        <div className="bg-card border border-border rounded-lg p-4">
          <div className="flex items-center gap-2 mb-3">
            <Gauge className="w-4 h-4 text-primary" />
            <h3 className="text-sm font-semibold text-foreground">Speed</h3>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <div className="text-xs text-muted-foreground mb-1">Average</div>
              <div className="text-lg font-semibold text-foreground tabular-nums">
                {trip.stats.avgSpeedMph.toFixed(1)}
                <span className="text-sm text-muted-foreground ml-1">mph</span>
              </div>
            </div>
            <div>
              <div className="text-xs text-muted-foreground mb-1">Maximum</div>
              <div className="text-lg font-semibold text-foreground tabular-nums">
                {trip.stats.maxSpeedMph.toFixed(1)}
                <span className="text-sm text-muted-foreground ml-1">mph</span>
              </div>
            </div>
          </div>
        </div>

        {/* RPM Stats */}
        <div className="bg-card border border-border rounded-lg p-4">
          <div className="flex items-center gap-2 mb-3">
            <Gauge className="w-4 h-4 text-primary" />
            <h3 className="text-sm font-semibold text-foreground">RPM</h3>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <div className="text-xs text-muted-foreground mb-1">Average</div>
              <div className="text-lg font-semibold text-foreground tabular-nums">
                {Math.round(trip.stats.avgRpm)}
                <span className="text-sm text-muted-foreground ml-1">rpm</span>
              </div>
            </div>
            <div>
              <div className="text-xs text-muted-foreground mb-1">Maximum</div>
              <div className="text-lg font-semibold text-foreground tabular-nums">
                {Math.round(trip.stats.maxRpm)}
                <span className="text-sm text-muted-foreground ml-1">rpm</span>
              </div>
            </div>
          </div>
        </div>

        {/* Coolant Stats */}
        <div className="bg-card border border-border rounded-lg p-4">
          <div className="flex items-center gap-2 mb-3">
            <Thermometer className="w-4 h-4 text-primary" />
            <h3 className="text-sm font-semibold text-foreground">Coolant Temperature</h3>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <div className="text-xs text-muted-foreground mb-1">Average</div>
              <div className="text-lg font-semibold text-foreground tabular-nums">
                {trip.stats.avgCoolantF.toFixed(1)}
                <span className="text-sm text-muted-foreground ml-1">°F</span>
              </div>
            </div>
            <div>
              <div className="text-xs text-muted-foreground mb-1">Maximum</div>
              <div className="text-lg font-semibold text-foreground tabular-nums">
                {trip.stats.maxCoolantF.toFixed(1)}
                <span className="text-sm text-muted-foreground ml-1">°F</span>
              </div>
            </div>
          </div>
        </div>

        {/* Engine Load */}
        <div className="bg-card border border-border rounded-lg p-4">
          <div className="flex items-center gap-2 mb-3">
            <Gauge className="w-4 h-4 text-primary" />
            <h3 className="text-sm font-semibold text-foreground">Engine Load</h3>
          </div>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <div className="text-xs text-muted-foreground mb-1">Maximum</div>
              <div className="text-lg font-semibold text-foreground tabular-nums">
                {trip.stats.maxLoadPct.toFixed(1)}
                <span className="text-sm text-muted-foreground ml-1">%</span>
              </div>
            </div>
          </div>
        </div>

        {/* Notes */}
        {trip.notes && (
          <div className="bg-card border border-border rounded-lg p-4">
            <h3 className="text-sm font-semibold text-foreground mb-2">Notes</h3>
            <p className="text-sm text-muted-foreground">{trip.notes}</p>
          </div>
        )}

        {/* Metadata */}
        <div className="bg-muted/30 border border-border/50 rounded-lg p-3 text-xs space-y-1">
          <div className="flex justify-between">
            <span className="text-muted-foreground">Recording Mode:</span>
            <span className="text-foreground capitalize">{trip.metadata.recordingMode}</span>
          </div>
          <div className="flex justify-between">
            <span className="text-muted-foreground">Samples:</span>
            <span className="text-foreground">{trip.samples.length}</span>
          </div>
          {trip.metadata.vehicle && (
            <div className="flex justify-between">
              <span className="text-muted-foreground">Vehicle:</span>
              <span className="text-foreground">{trip.metadata.vehicle}</span>
            </div>
          )}
        </div>
      </div>

      {/* Sticky Actions */}
      <div className="bg-card border-t border-border p-4 space-y-2">
        <button
          onClick={() => setShowExport(true)}
          className="w-full flex items-center justify-center gap-2 bg-primary text-primary-foreground px-4 py-3 rounded-lg font-medium hover:bg-primary/90 transition-colors"
        >
          <Download className="w-4 h-4" />
          Export Markdown
        </button>
        <button
          onClick={() => setShowDeleteConfirm(true)}
          className="w-full flex items-center justify-center gap-2 bg-destructive/10 text-destructive px-4 py-2 rounded-lg font-medium hover:bg-destructive/20 transition-colors"
        >
          <Trash2 className="w-4 h-4" />
          Delete Trip
        </button>
      </div>

      {/* Export Bottom Sheet */}
      <ExportBottomSheet
        isOpen={showExport}
        onClose={() => setShowExport(false)}
        trip={trip}
      />

      {/* Delete Confirmation */}
      {showDeleteConfirm && (
        <div className="fixed inset-0 z-50 flex items-end sm:items-center justify-center">
          <div className="absolute inset-0 bg-black/60" onClick={() => setShowDeleteConfirm(false)} />
          <div className="relative bg-card border border-border rounded-t-2xl sm:rounded-2xl w-full sm:max-w-md animate-in slide-in-from-bottom duration-200">
            <div className="p-4 space-y-4">
              <h3 className="text-lg font-semibold text-foreground">Delete Trip?</h3>
              <p className="text-sm text-muted-foreground">
                This action cannot be undone. The trip and all its data will be permanently deleted.
              </p>
              <div className="flex gap-2">
                <button
                  onClick={() => setShowDeleteConfirm(false)}
                  className="flex-1 px-4 py-2 bg-muted text-foreground rounded-lg font-medium hover:bg-muted/80 transition-colors"
                >
                  Cancel
                </button>
                <button
                  onClick={handleDelete}
                  className="flex-1 px-4 py-2 bg-destructive text-destructive-foreground rounded-lg font-medium hover:bg-destructive/90 transition-colors"
                >
                  Delete
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
