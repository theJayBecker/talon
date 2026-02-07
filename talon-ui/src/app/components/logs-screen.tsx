import { Play, Square, FileText, Share2 } from "lucide-react";

interface LogFile {
  filename: string;
  date: string;
  size: string;
}

interface LogsScreenProps {
  isConnected: boolean;
  isLogging: boolean;
  logFiles: LogFile[];
  onStartLogging: () => void;
  onStopLogging: () => void;
  onExportLog: (filename: string) => void;
}

export function LogsScreen({
  isConnected,
  isLogging,
  logFiles,
  onStartLogging,
  onStopLogging,
  onExportLog,
}: LogsScreenProps) {
  return (
    <div className="flex flex-col h-full">
      {/* Header */}
      <div className="bg-card border-b border-border px-6 py-3">
        <h2 className="text-lg font-semibold">Data Logs</h2>
      </div>

      {/* Content */}
      <div className="flex-1 px-6 py-6 overflow-auto space-y-6">
        {/* Trip Logging Section */}
        <div className="space-y-4">
          <h3 className="text-base font-semibold">Trip Logging</h3>
          
          {/* Logging Control */}
          <div className="bg-card border border-border rounded-lg p-4 space-y-4">
            <div className="flex items-center justify-between">
              <div className="space-y-1">
                <p className="text-sm font-medium">
                  {isLogging ? "Recording..." : "Start Recording"}
                </p>
                <p className="text-xs text-muted-foreground">
                  {isLogging
                    ? "Data is being logged to CSV"
                    : "Click to begin logging trip data"}
                </p>
              </div>
              <button
                onClick={isLogging ? onStopLogging : onStartLogging}
                disabled={!isConnected}
                className={`p-3 rounded-lg transition-all ${
                  isLogging
                    ? "bg-destructive/20 text-destructive hover:bg-destructive/30"
                    : "bg-primary text-primary-foreground hover:bg-primary/90"
                } disabled:opacity-50 disabled:cursor-not-allowed`}
              >
                {isLogging ? <Square className="w-5 h-5" /> : <Play className="w-5 h-5" />}
              </button>
            </div>

            {/* Info */}
            {!isConnected && (
              <div className="text-xs text-muted-foreground bg-muted/50 rounded p-2">
                Connect to OBD-II adapter to start logging
              </div>
            )}
            
            {isLogging && (
              <div className="text-xs text-muted-foreground bg-primary/10 border border-primary/20 rounded p-2">
                <div className="flex items-center gap-2">
                  <div className="w-2 h-2 bg-primary rounded-full animate-pulse" />
                  <span>Logging: Timestamp, Speed, RPM, Fuel %, Coolant Temp</span>
                </div>
              </div>
            )}
          </div>

          {/* Data Info */}
          <div className="bg-muted/30 rounded-lg p-3 text-xs text-muted-foreground space-y-1">
            <p className="font-medium text-foreground">Logged Data Fields:</p>
            <p>• Timestamp • Speed (km/h) • RPM</p>
            <p>• Fuel Level (%) • Coolant Temperature (°C)</p>
            <p className="pt-1 text-xs">Files saved locally as CSV format</p>
          </div>
        </div>

        {/* Past Logs Section */}
        <div className="space-y-4">
          <h3 className="text-base font-semibold">Saved Logs</h3>
          
          {logFiles.length === 0 ? (
            <div className="bg-card border border-border rounded-lg p-6 text-center">
              <FileText className="w-8 h-8 text-muted-foreground mx-auto mb-2" />
              <p className="text-muted-foreground">No saved logs yet</p>
              <p className="text-sm text-muted-foreground mt-1">Start logging to create files</p>
            </div>
          ) : (
            <div className="space-y-2">
              {logFiles.map((log, index) => (
                <div
                  key={index}
                  className="bg-card border border-border rounded-lg p-4 flex items-center justify-between hover:border-primary/30 transition-colors"
                >
                  <div className="flex items-center gap-3">
                    <FileText className="w-5 h-5 text-primary" />
                    <div>
                      <p className="text-sm font-medium">{log.filename}</p>
                      <p className="text-xs text-muted-foreground">
                        {log.date} • {log.size}
                      </p>
                    </div>
                  </div>
                  <button
                    onClick={() => onExportLog(log.filename)}
                    className="p-2 hover:bg-muted rounded-lg transition-colors"
                    aria-label="Export log"
                  >
                    <Share2 className="w-4 h-4 text-primary" />
                  </button>
                </div>
              ))}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
