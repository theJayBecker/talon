import { Play, Square, Clock, MapPin } from "lucide-react";
import { Trip } from "../lib/trip-types";

interface TripControlsProps {
  activeTrip: Trip | null;
  autoDetect: boolean;
  onStartTrip: () => void;
  onStopTrip: () => void;
  onToggleAutoDetect: () => void;
}

export function TripControls({
  activeTrip,
  autoDetect,
  onStartTrip,
  onStopTrip,
  onToggleAutoDetect,
}: TripControlsProps) {
  const isRecording = activeTrip !== null;

  const formatDuration = (seconds: number): string => {
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;

    if (hours > 0) {
      return `${hours}:${minutes.toString().padStart(2, "0")}:${secs.toString().padStart(2, "0")}`;
    }
    return `${minutes}:${secs.toString().padStart(2, "0")}`;
  };

  return (
    <div className="bg-card border border-border rounded-lg p-4">
      <div className="flex items-center justify-between mb-3">
        <div className="flex items-center gap-2">
          <div
            className={`w-2 h-2 rounded-full ${
              isRecording ? "bg-destructive animate-pulse" : "bg-muted-foreground"
            }`}
          />
          <span className="text-sm font-semibold text-foreground">
            Trip {isRecording ? "Recording" : "Idle"}
          </span>
        </div>
        
        {/* Auto-detect toggle */}
        <button
          onClick={onToggleAutoDetect}
          className={`px-2 py-1 text-xs rounded transition-colors ${
            autoDetect
              ? "bg-primary/20 text-primary border border-primary/30"
              : "bg-muted/50 text-muted-foreground border border-border"
          }`}
        >
          Auto-detect {autoDetect ? "On" : "Off"}
        </button>
      </div>

      {isRecording && activeTrip && (
        <div className="grid grid-cols-2 gap-3 mb-3">
          <div className="flex items-center gap-2 text-xs">
            <Clock className="w-3.5 h-3.5 text-muted-foreground" />
            <div>
              <div className="text-muted-foreground">Duration</div>
              <div className="text-foreground font-semibold tabular-nums">
                {formatDuration(activeTrip.stats.durationSec)}
              </div>
            </div>
          </div>
          <div className="flex items-center gap-2 text-xs">
            <MapPin className="w-3.5 h-3.5 text-muted-foreground" />
            <div>
              <div className="text-muted-foreground">Distance</div>
              <div className="text-foreground font-semibold tabular-nums">
                {activeTrip.stats.distanceMi.toFixed(1)} mi
              </div>
            </div>
          </div>
        </div>
      )}

      <div className="flex gap-2">
        {!isRecording ? (
          <button
            onClick={onStartTrip}
            className="flex-1 flex items-center justify-center gap-2 bg-primary text-primary-foreground px-4 py-2 rounded-lg font-medium hover:bg-primary/90 transition-colors active:scale-95"
          >
            <Play className="w-4 h-4" />
            Start Trip
          </button>
        ) : (
          <button
            onClick={onStopTrip}
            className="flex-1 flex items-center justify-center gap-2 bg-destructive text-destructive-foreground px-4 py-2 rounded-lg font-medium hover:bg-destructive/90 transition-colors active:scale-95"
          >
            <Square className="w-4 h-4" />
            Stop Trip
          </button>
        )}
      </div>
    </div>
  );
}
