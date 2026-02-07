import { Bluetooth, Gauge, AlertCircle, Map } from "lucide-react";

type NavTab = "dashboard" | "diagnostics" | "trips";

interface BottomNavigationProps {
  activeTab: NavTab;
  onTabChange: (tab: NavTab) => void;
}

export function BottomNavigation({ activeTab, onTabChange }: BottomNavigationProps) {
  const tabs = [
    { id: "dashboard" as NavTab, icon: Gauge, label: "Dashboard" },
    { id: "diagnostics" as NavTab, icon: AlertCircle, label: "Diagnostics" },
    { id: "trips" as NavTab, icon: Map, label: "Trips" },
  ];

  return (
    <nav className="bg-card border-t border-border">
      <div className="grid grid-cols-3">
        {tabs.map((tab) => {
          const Icon = tab.icon;
          const isActive = activeTab === tab.id;
          
          return (
            <button
              key={tab.id}
              onClick={() => onTabChange(tab.id)}
              className={`flex flex-col items-center justify-center py-3 px-2 transition-colors ${
                isActive
                  ? "text-primary"
                  : "text-muted-foreground hover:text-foreground"
              }`}
            >
              <Icon className="w-6 h-6 mb-1" strokeWidth={isActive ? 2.5 : 2} />
              <span className="text-xs">{tab.label}</span>
            </button>
          );
        })}
      </div>
    </nav>
  );
}