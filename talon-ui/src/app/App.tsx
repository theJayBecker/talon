import { useState, useEffect } from "react";
import { DashboardScreen } from "./components/dashboard-screen";
import { DiagnosticsScreen } from "./components/diagnostics-screen";
import { TripsScreen } from "./components/trips-screen";
import { TripDetailScreen } from "./components/trip-detail-screen";
import { BottomNavigation } from "./components/bottom-navigation";
import { TripRepository } from "./lib/trip-repository";

type NavTab = "dashboard" | "diagnostics" | "trips";
type ConnectionStatus = "disconnected" | "connecting" | "connected";

export default function App() {
  const [activeTab, setActiveTab] = useState<NavTab>("dashboard");
  const [connectionStatus, setConnectionStatus] = useState<ConnectionStatus>("disconnected");
  
  // Trip management
  const [tripRepository] = useState(() => new TripRepository());
  const [trips, setTrips] = useState(tripRepository.getAllTrips());
  const [selectedTripId, setSelectedTripId] = useState<string | null>(null);
  const [autoDetect, setAutoDetect] = useState(false);

  // Subscribe to trip repository changes
  useEffect(() => {
    const unsubscribe = tripRepository.subscribe(() => {
      setTrips(tripRepository.getAllTrips());
    });
    return unsubscribe;
  }, [tripRepository]);

  const handleConnect = () => {
    setConnectionStatus("connecting");
    setTimeout(() => {
      setConnectionStatus("connected");
    }, 2000);
  };

  const handleDisconnect = () => {
    setConnectionStatus("disconnected");
  };

  const handleSelectTrip = (tripId: string) => {
    setSelectedTripId(tripId);
    setActiveTab("trips");
  };

  const handleDeleteTrip = (tripId: string) => {
    tripRepository.deleteTrip(tripId);
  };

  return (
    <div className="dark">
      <div className="flex flex-col h-screen max-w-md mx-auto bg-background text-foreground">
        {/* Main Content */}
        <div className="flex-1 overflow-hidden">
          {activeTab === "dashboard" && (
            <DashboardScreen
              isConnected={connectionStatus === "connected"}
              tripRepository={tripRepository}
              autoDetect={autoDetect}
              onToggleAutoDetect={() => setAutoDetect(!autoDetect)}
              connectionStatus={connectionStatus}
              deviceName="OBD-II Adapter (ELM327)"
              onConnect={handleConnect}
              onDisconnect={handleDisconnect}
            />
          )}
          {activeTab === "diagnostics" && (
            <DiagnosticsScreen
              isConnected={connectionStatus === "connected"}
            />
          )}
          {activeTab === "trips" && !selectedTripId && (
            <TripsScreen
              trips={trips}
              onSelectTrip={handleSelectTrip}
            />
          )}
          {activeTab === "trips" && selectedTripId && (
            <TripDetailScreen
              trip={tripRepository.getTrip(selectedTripId)!}
              onBack={() => setSelectedTripId(null)}
              onDelete={handleDeleteTrip}
            />
          )}
        </div>

        {/* Bottom Navigation */}
        <BottomNavigation activeTab={activeTab} onTabChange={setActiveTab} />
      </div>
    </div>
  );
}