import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { ApiError, getDatasetHistory, rollbackDataset } from "../api/client";
import type { UploadHistoryEntry } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { formatDateTime } from "../utils/formatDate";
import { useToast } from "../components/Toast";
import { TableSkeleton } from "../components/Skeleton";
import { ClockIcon, HistoryIcon, UserIcon } from "../components/Icons";

const STATUS_LABELS: Record<UploadHistoryEntry["status"], string> = {
  success: "Başarılı",
  failed: "Başarısız",
  rolled_back: "Geri alındı",
};

export function DatasetHistoryPage() {
  const { datasetName } = useParams<{ datasetName: string }>();
  const { token } = useAuth();
  const { showToast } = useToast();
  const [history, setHistory] = useState<UploadHistoryEntry[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [rollingBackId, setRollingBackId] = useState<string | null>(null);

  const loadHistory = useCallback(() => {
    if (!token || !datasetName) return;
    getDatasetHistory(token, datasetName)
      .then(setHistory)
      .catch((err) => setError(err instanceof ApiError ? err.message : "Geçmiş alınamadı"));
  }, [token, datasetName]);

  useEffect(loadHistory, [loadHistory]);

  async function handleRollback(uploadId: string) {
    if (!token || !datasetName) return;
    if (!window.confirm("Bu versiyona dönmek istediğinize emin misiniz? Mevcut aktif veri değişecek.")) return;

    setRollingBackId(uploadId);
    setError(null);
    try {
      await rollbackDataset(token, datasetName, uploadId);
      showToast("success", "Önceki versiyona başarıyla dönüldü.");
      loadHistory();
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Geri alma işlemi başarısız oldu");
    } finally {
      setRollingBackId(null);
    }
  }

  return (
    <div className="card">
      <div className="page-header">
        <div className="page-header-title">
          <h2>{datasetName}</h2>
          <span className="muted">Yükleme geçmişi</span>
        </div>
      </div>
      <Link to="/datasets" className="back-link">
        ← Dataset listesine dön
      </Link>

      {error && <div className="alert alert-error">{error}</div>}
      {history === null && !error && <TableSkeleton rows={3} />}
      {history !== null && history.length === 0 && <p className="muted">Henüz yükleme yapılmamış.</p>}

      {history !== null && history.length > 0 && (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                <th>Tarih</th>
                <th>Kullanıcı</th>
                <th>Dosya</th>
                <th>Durum</th>
                <th>Satır</th>
                <th></th>
              </tr>
            </thead>
            <tbody>
              {history.map((entry) => (
                <tr key={entry.id}>
                  <td>
                    <span className="cell-with-icon">
                      <ClockIcon width={14} height={14} />
                      {formatDateTime(entry.uploaded_at)}
                    </span>
                  </td>
                  <td>
                    <span className="cell-with-icon">
                      <UserIcon width={14} height={14} />
                      {entry.uploaded_by}
                    </span>
                  </td>
                  <td className="muted">{entry.original_filename}</td>
                  <td>
                    <span className={`status-badge status-${entry.status}`}>{STATUS_LABELS[entry.status]}</span>
                  </td>
                  <td>{entry.row_count}</td>
                  <td>
                    {entry.status !== "failed" && (
                      <button
                        type="button"
                        className="button-ghost"
                        onClick={() => handleRollback(entry.id)}
                        disabled={rollingBackId === entry.id}
                      >
                        <HistoryIcon width={14} height={14} />
                        {rollingBackId === entry.id ? "Dönülüyor…" : "Bu versiyona dön"}
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
