export function TableSkeleton({ rows = 4 }: { rows?: number }) {
  return (
    <div className="skeleton-table">
      {Array.from({ length: rows }).map((_, i) => (
        <div className="skeleton-row" key={i}>
          <span className="skeleton-bar skeleton-bar-wide" />
          <span className="skeleton-bar" />
          <span className="skeleton-bar skeleton-bar-narrow" />
          <span className="skeleton-bar skeleton-bar-narrow" />
        </div>
      ))}
    </div>
  );
}
