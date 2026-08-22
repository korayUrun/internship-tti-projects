import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { listDatasets, ApiError } from "../api/client";
import type { DatasetSummary } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { formatDateTime } from "../utils/formatDate";
import { StatCard } from "../components/StatCard";
import { TableSkeleton } from "../components/Skeleton";
import { ClockIcon, DatabaseIcon, HistoryIcon, PlusIcon, UploadIcon, UserIcon } from "../components/Icons";

export function DatasetListPage() {
  const { token } = useAuth();
  const [datasets, setDatasets] = useState<DatasetSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!token) return;
    listDatasets(token)
      .then(setDatasets)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Dataset listesi alınamadı"));
  }, [token]);

  const lastUpdate = useMemo(() => {
    if (!datasets || datasets.length === 0) return null;
    const timestamps = datasets.map((d) => d.last_uploaded_at).filter((v): v is string => !!v);
    if (timestamps.length === 0) return null;
    return timestamps.reduce((latest, current) => (current > latest ? current : latest));
  }, [datasets]);

  return (
    <>
      <div className="dashboard-header">
        <div>
          <h1>Veri Yönetimi</h1>
          <p>Kurumsal veri setlerinizi yönetin, güncelleyin ve geçmiş değişiklikleri takip edin.</p>
        </div>
        <div className="dashboard-header-icon">
          <DatabaseIcon width={26} height={26} />
        </div>
      </div>

      <div className="stat-grid">
        <StatCard
          icon={<DatabaseIcon />}
          label="Toplam Veri Seti"
          value={datasets === null ? "—" : datasets.length}
        />
        <StatCard
          icon={<ClockIcon />}
          label="Son Güncelleme"
          value={lastUpdate ? formatDateTime(lastUpdate) : "—"}
        />
        <StatCard
          icon={<UserIcon />}
          label="Aktif Yönetici"
          value="Admin"
          hint={
            <span className="status-badge status-success" style={{ marginTop: "0.2rem" }}>
              Aktif
            </span>
          }
        />
      </div>

      <div className="card">
        <div className="page-header">
          <div className="page-header-title">
            <h2>Dataset'ler</h2>
            <span className="muted">
              {datasets === null ? "" : `Toplam ${datasets.length} veri seti`}
            </span>
          </div>
          <Link to="/datasets/new">
            <button type="button">
              <PlusIcon width={16} height={16} />
              Yeni Dataset
            </button>
          </Link>
        </div>

        {error && <div className="alert alert-error">{error}</div>}
        {datasets === null && !error && <TableSkeleton />}
        {datasets !== null && datasets.length === 0 && <p className="muted">Henüz tanımlı dataset yok.</p>}

        {datasets !== null && datasets.length > 0 && (
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>Dataset</th>
                  <th>Açıklama</th>
                  <th>Son Güncelleme</th>
                  <th>Güncelleyen</th>
                  <th></th>
                </tr>
              </thead>
              <tbody>
                {datasets.map((dataset) => (
                  <tr key={dataset.id}>
                    <td>
                      <span className="dataset-name">
                        <span className="dataset-name-icon">
                          <DatabaseIcon width={14} height={14} />
                        </span>
                        {dataset.name}
                      </span>
                    </td>
                    <td className="muted">{dataset.description ?? "—"}</td>
                    <td>
                      <span className="cell-with-icon">
                        <ClockIcon width={14} height={14} />
                        {formatDateTime(dataset.last_uploaded_at)}
                      </span>
                    </td>
                    <td>
                      {dataset.last_uploaded_by ? (
                        <span className="cell-with-icon">
                          <UserIcon width={14} height={14} />
                          {dataset.last_uploaded_by}
                        </span>
                      ) : (
                        "—"
                      )}
                    </td>
                    <td className="row-actions">
                      <Link to={`/datasets/${dataset.name}/history`}>
                        <button type="button" className="button-ghost">
                          <HistoryIcon width={14} height={14} />
                          Geçmiş
                        </button>
                      </Link>
                      <Link to={`/datasets/${dataset.name}/upload`}>
                        <button type="button" className="button-ghost">
                          <UploadIcon width={14} height={14} />
                          Güncelle
                        </button>
                      </Link>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </>
  );
}
