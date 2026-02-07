import { Calendar, Clock, MapPin, Droplet, ChevronRight } from "lucide-react";
import { TripSummary } from "../lib/trip-types";

interface TripsScreenProps {
  trips: TripSummary[];
  onSelectTrip: (id: string) => void;
}

export function TripsScreen({ trips, onSelectTrip }: TripsScreenProps) {
  const formatDate = (timestamp: number): string => {
    const date = new Date(timestamp);
    const today = new Date();
    const yesterday = new Date(today);
    yesterday.setDate(yesterday.getDate() - 1);

    if (date.toDateString() === today.toDateString()) {
      return "Today";
    } else if (date.toDateString() === yesterday.toDateString()) {
      return "Yesterday";
    } else {
      return date.toLocaleDateString(undefined, { month: "short", day: "numeric" });
    }
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
    if (hours > 0) return `${hours}h ${minutes}m`;
    return `${minutes}m`;
  };

  const getStatusBadge = (status: "recording" | "completed" | "partial") => {
    switch (status) {
      case "recording":
        return (
          <div className="px-2 py-0.5 bg-destructive/20 text-destructive text-xs rounded animate-pulse">
            Recording
          </div>
        );
      case "partial":
        return (
          <div className="px-2 py-0.5 bg-secondary/20 text-secondary text-xs rounded">
            Partial
          </div>
        );
      default:
        return null;
    }
  };

  return (
    <div className="flex flex-col h-full bg-background">
      {/* Header */}
      <div className="bg-card border-b border-border px-4 py-3">
        <h2 className="text-lg font-semibold">Trips</h2>
        <p className="text-xs text-muted-foreground mt-1">
          {trips.length} trip{trips.length !== 1 ? "s" : ""} recorded
        </p>
      </div>

      {/* Trips List */}
      <div className="flex-1 overflow-auto">
        {trips.length === 0 ? (
          <div className="flex flex-col items-center justify-center h-full px-4 text-center">
            <Calendar className="w-12 h-12 text-muted-foreground/40 mb-3" />
            <p className="text-sm text-muted-foreground">No trips recorded yet</p>
            <p className="text-xs text-muted-foreground/70 mt-1">
              Start a trip from the Dashboard to begin tracking
            </p>
          </div>
        ) : (
          <div className="divide-y divide-border">
            {trips.map((trip) => (
              <button
                key={trip.id}
                onClick={() => onSelectTrip(trip.id)}
                className="w-full px-4 py-4 hover:bg-muted/50 transition-colors text-left active:scale-[0.99]"
              >
                <div className="flex items-start justify-between gap-3">
                  <div className="flex-1 space-y-2">
                    {/* Date and Time */}
                    <div className="flex items-center gap-2">
                      <span className="text-sm font-semibold text-foreground">
                        {formatDate(trip.startTime)}
                      </span>
                      <span className="text-xs text-muted-foreground">
                        {formatTime(trip.startTime)}
                      </span>
                      {getStatusBadge(trip.status)}
                    </div>

                    {/* Stats */}
                    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs">
                      <div className="flex items-center gap-1 text-muted-foreground">
                        <Clock className="w-3.5 h-3.5" />
                        <span>{formatDuration(trip.durationSec)}</span>
                      </div>
                      <div className="flex items-center gap-1 text-muted-foreground">
                        <MapPin className="w-3.5 h-3.5" />
                        <span>{trip.distanceMi.toFixed(1)} mi</span>
                      </div>
                      <div className="flex items-center gap-1 text-muted-foreground">
                        <Droplet className="w-3.5 h-3.5" />
                        <span>-{trip.fuelUsedPct.toFixed(1)}%</span>
                      </div>
                    </div>
                  </div>

                  {/* Chevron */}
                  <ChevronRight className="w-5 h-5 text-muted-foreground flex-shrink-0 mt-1" />
                </div>
              </button>
            ))}
          </div>
        )}
      </div>
    </div>
  );
}
