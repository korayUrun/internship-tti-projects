import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { ApiError, createDataset } from "../api/client";
import { useAuth } from "../auth/AuthContext";
import { useToast } from "../components/Toast";

export function CreateDatasetPage() {
  const { token } = useAuth();
  const { showToast } = useToast();
  const navigate = useNavigate();
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!token) return;
    setError(null);
    setSubmitting(true);
    try {
      // Kolon şeması henüz tanımlanmadı (columns: []). Şema belirlenene kadar bu
      // dataset'e Excel yüklenemez — backend bunu ayrıca reddeder.
      await createDataset(token, name.trim(), description.trim() || null, { columns: [] });
      showToast("success", `"${name.trim()}" dataset'i oluşturuldu.`);
      navigate("/datasets", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Dataset oluşturulamadı");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="card">
      <h2>Yeni Dataset Ekle</h2>
      <Link to="/datasets" className="back-link">
        ← Dataset listesine dön
      </Link>

      <form onSubmit={handleSubmit}>
        <label htmlFor="name">Dataset adı</label>
        <input
          id="name"
          value={name}
          onChange={(e) => setName(e.target.value)}
          placeholder="örn. cable_data"
          autoFocus
          required
        />

        <label htmlFor="description">Açıklama</label>
        <input
          id="description"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="örn. Kablo verileri"
        />

        <p className="muted" style={{ marginTop: "0.75rem" }}>
          Kolon şeması (Excel'den hangi bilgilerin alınacağı) henüz ayarlanmadı. Dataset
          oluşturulduktan sonra şema tanımlanana kadar Excel yüklenemez.
        </p>

        {error && <div className="alert alert-error">{error}</div>}

        <button type="submit" disabled={submitting || !name.trim()}>
          {submitting ? "Oluşturuluyor…" : "Dataset Oluştur"}
        </button>
      </form>
    </div>
  );
}
