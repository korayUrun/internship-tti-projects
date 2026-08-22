import type { ReactNode } from "react";

export function StatCard({
  icon,
  label,
  value,
  hint,
}: {
  icon: ReactNode;
  label: string;
  value: ReactNode;
  hint?: ReactNode;
}) {
  return (
    <div className="stat-card">
      <div className="stat-card-icon">{icon}</div>
      <div>
        <p className="stat-card-label">{label}</p>
        <p className="stat-card-value">{value}</p>
        {hint && <p className="stat-card-hint">{hint}</p>}
      </div>
    </div>
  );
}
