interface CircularFuelGaugeProps {
  fuelPercent: number;
  size?: number;
  strokeWidth?: number;
}

export function CircularFuelGauge({ 
  fuelPercent, 
  size = 200, 
  strokeWidth = 16 
}: CircularFuelGaugeProps) {
  const radius = (size - strokeWidth) / 2;
  const circumference = 2 * Math.PI * radius;
  const offset = circumference - (fuelPercent / 100) * circumference;

  return (
    <div className="relative" style={{ width: size, height: size }}>
      <svg width={size} height={size} className="transform -rotate-90">
        {/* Background circle */}
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="currentColor"
          strokeWidth={strokeWidth}
          className="text-muted/30"
        />
        
        {/* Progress circle */}
        <circle
          cx={size / 2}
          cy={size / 2}
          r={radius}
          fill="none"
          stroke="currentColor"
          strokeWidth={strokeWidth}
          strokeDasharray={circumference}
          strokeDashoffset={offset}
          strokeLinecap="round"
          className={`transition-all duration-1000 ${
            fuelPercent > 50
              ? "text-primary"
              : fuelPercent > 25
              ? "text-secondary"
              : "text-destructive"
          }`}
        />
      </svg>
      
      {/* Center content */}
      <div className="absolute inset-0 flex flex-col items-center justify-center">
        <div className="text-5xl font-semibold text-foreground tabular-nums">
          {fuelPercent.toFixed(1)}
        </div>
        <div className="text-lg text-muted-foreground">%</div>
      </div>
    </div>
  );
}
