import { Bluetooth, Circle } from "lucide-react";

interface ConnectScreenProps {
  connectionStatus: "disconnected" | "connecting" | "connected";
  deviceName: string;
  onConnect: () => void;
  onDisconnect: () => void;
  errorMessage?: string;
}

export function ConnectScreen({
  connectionStatus,
  deviceName,
  onConnect,
  onDisconnect,
  errorMessage,
}: ConnectScreenProps) {
  return (
    <div className="flex flex-col px-6 py-8 gap-8">
      {/* Header */}
      <div className="text-center space-y-2">
        <h1 className="text-3xl font-semibold text-foreground">SonataOBD</h1>
        <p className="text-muted-foreground">Hyundai Sonata 2013 GLS</p>
      </div>

      {/* Connection Card */}
      <div className="bg-card border border-border rounded-lg p-6 space-y-6">
        {/* Status Indicator */}
        <div className="flex items-center gap-3">
          <div className="relative">
            <Bluetooth className="w-8 h-8 text-primary" />
            {connectionStatus === "connected" && (
              <Circle className="absolute -top-1 -right-1 w-3 h-3 text-green-500 fill-green-500" />
            )}
            {connectionStatus === "connecting" && (
              <Circle className="absolute -top-1 -right-1 w-3 h-3 text-secondary fill-secondary animate-pulse" />
            )}
          </div>
          <div className="flex-1">
            <div className="text-sm text-muted-foreground">Connection Status</div>
            <div className="text-lg capitalize">
              {connectionStatus === "connected" && (
                <span className="text-green-500">Connected</span>
              )}
              {connectionStatus === "connecting" && (
                <span className="text-secondary">Connecting...</span>
              )}
              {connectionStatus === "disconnected" && (
                <span className="text-muted-foreground">Disconnected</span>
              )}
            </div>
          </div>
        </div>

        {/* Device Name */}
        <div className="border-t border-border pt-4">
          <div className="text-sm text-muted-foreground mb-1">Selected Device</div>
          <div className="text-base text-foreground">{deviceName}</div>
        </div>

        {/* Action Buttons */}
        <div className="space-y-3 pt-2">
          {connectionStatus !== "connected" ? (
            <button
              onClick={onConnect}
              disabled={connectionStatus === "connecting"}
              className="w-full bg-primary text-primary-foreground py-3 px-4 rounded-lg hover:bg-primary/90 disabled:opacity-50 disabled:cursor-not-allowed transition-all"
            >
              {connectionStatus === "connecting" ? "Connecting..." : "Connect"}
            </button>
          ) : (
            <button
              onClick={onDisconnect}
              className="w-full bg-muted text-foreground py-3 px-4 rounded-lg hover:bg-muted/80 transition-all"
            >
              Disconnect
            </button>
          )}
        </div>

        {/* Error/Status Message */}
        {errorMessage && (
          <div className="text-sm text-destructive bg-destructive/10 border border-destructive/20 rounded-lg p-3">
            {errorMessage}
          </div>
        )}
      </div>

      {/* Info Text */}
      <div className="text-center text-sm text-muted-foreground">
        <p>Ensure your OBD-II adapter is plugged in</p>
        <p className="mt-1">and Bluetooth is enabled</p>
      </div>
    </div>
  );
}
