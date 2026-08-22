import { useCallback, useState } from "react";
import { useDropzone } from "react-dropzone";
import { useParams, Link } from "react-router-dom";
import { uploadDataset, ApiError } from "../api/client";
import type { UploadResult, ValidationError } from "../api/types";
import { useAuth } from "../auth/AuthContext";
import { useToast } from "../components/Toast";
import { UploadIcon } from "../components/Icons";

function ValidationErrorTable({ errors }: { errors: ValidationError[] }) {
  return (
    <table>
      <thead>
        <tr>
          <th>Satır</th>
          <th>Alan</th>
          <th>Hata</th>
        </tr>
      </thead>
      <tbody>
        {errors.map((validationError, index) => (
          <tr key={index}>
            <td>{validationError.row || "—"}</td>
            <td>{validationError.field}</td>
            <td>{validationError.message}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}

export function DatasetUploadPage() {
  const { datasetName } = useParams<{ datasetName: string }>();
  const { token } = useAuth();
  const { showToast } = useToast();
  const [file, setFile] = useState<File | null>(null);
  const [result, setResult] = useState<UploadResult | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const onDrop = useCallback((acceptedFiles: File[]) => {
    setResult(null);
    setError(null);
    setFile(acceptedFiles[0] ?? null);
  }, []);

  const { getRootProps, getInputProps, isDragActive } = useDropzone({
    onDrop,
    maxFiles: 1,
    accept: {
      "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet": [".xlsx"],
    },
  });

  async function submit(force: boolean) {
    if (!file || !token || !datasetName) return;
    setSubmitting(true);
    setError(null);
    try {
      const uploadResult = await uploadDataset(token, datasetName, file, force);
      setResult(uploadResult);
      if (uploadResult.status === "success") {
        showToast("success", `${uploadResult.row_count} satır kaydedildi.`);
      }
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Yükleme başarısız oldu");
    } finally {
      setSubmitting(false);
    }
  }

  const isSuccess = result?.status === "success";
  const hasRowCountWarning = result?.status === "failed" && !!result.warning;
  // Dosya tamamen reddedildi (zorunlu kolon eksik ya da hiç geçerli satır kalmadı).
  const isFullyRejected = result?.status === "failed" && !result.warning && result.validation_errors.length > 0;
  // Yükleme başarılı oldu ama bazı satırlar hatalı olduğu için atlandı (kısmi kabul).
  const hasSkippedRows = isSuccess && (result?.validation_errors.length ?? 0) > 0;

  return (
    <div className="card">
      <h2>{datasetName} — Güncelle</h2>
      <Link to="/datasets" className="back-link">
        ← Dataset listesine dön
      </Link>

      <div {...getRootProps()} className={`dropzone ${isDragActive ? "dropzone-active" : ""}`}>
        <input {...getInputProps()} />
        <UploadIcon width={26} height={26} style={{ marginBottom: "0.5rem", opacity: 0.6 }} />
        {file ? <p>{file.name}</p> : <p>Excel dosyasını buraya sürükleyin ya da seçmek için tıklayın</p>}
      </div>

      <button onClick={() => submit(false)} disabled={!file || submitting}>
        <UploadIcon width={16} height={16} />
        {submitting ? "Yükleniyor…" : "Yükle"}
      </button>

      {error && <div className="alert alert-error">{error}</div>}

      {isSuccess && (
        <div className="alert alert-success">
          Yükleme başarılı. {result.row_count} satır kaydedildi.
        </div>
      )}

      {hasRowCountWarning && (
        <div className="alert alert-warning">
          <p>{result?.warning}</p>
          <button onClick={() => submit(true)} disabled={submitting}>
            Yine de devam et
          </button>
        </div>
      )}

      {isFullyRejected && (
        <div className="alert alert-error">
          <p>Yükleme reddedildi, hiçbir geçerli satır bulunamadı:</p>
          <ValidationErrorTable errors={result?.validation_errors ?? []} />
        </div>
      )}

      {hasSkippedRows && (
        <div className="alert alert-warning">
          <p>
            {result?.validation_errors.length} satır hatalı olduğu için atlandı, geri kalanı kaydedildi:
          </p>
          <ValidationErrorTable errors={result?.validation_errors ?? []} />
        </div>
      )}
    </div>
  );
}
