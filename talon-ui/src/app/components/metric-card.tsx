import { LucideIcon } from "lucide-react";

interface MetricCardProps {
  label: string;
  value: string | number;
  unit: string;
  icon?: LucideIcon;
}

export function MetricCard({ label, value, unit, icon: Icon }: MetricCardProps) {
  return (
    <div className="bg-card border border-border rounded-lg p-4 relative overflow-hidden">
      {/* Subtle glow effect */}
      <div className="absolute inset-0 bg-gradient-to-br from-primary/5 to-transparent pointer-events-none" />
      
      <div className="relative z-10">
        <div className="flex items-center justify-between mb-2">
          <span className="text-sm text-muted-foreground uppercase tracking-wide">{label}</span>
          {Icon && <Icon className="w-4 h-4 text-primary/40" />}
        </div>
        <div className="flex items-baseline gap-1">
          <span className="text-4xl font-semibold text-foreground tabular-nums">{value}</span>
          <span className="text-lg text-muted-foreground ml-1">{unit}</span>
        </div>
      </div>
    </div>
  );
}
