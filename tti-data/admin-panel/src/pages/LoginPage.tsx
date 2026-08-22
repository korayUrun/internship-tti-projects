import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import { ApiError } from "../api/client";
import ttiLogo from "../assets/logo.png";

export function LoginPage() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(username, password);
      navigate("/datasets", { replace: true });
    } catch (err) {
      setError(err instanceof ApiError ? err.message : "Giriş başarısız oldu");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="centered-page">
      <div className="card login-card">
        <div className="login-brand">
          <img src={ttiLogo} alt="TTI" className="login-brand-logo" />
          <div>
            <h1>TTI Veri Yönetimi</h1>
            <p className="muted">Kurumsal veri yönetim paneline giriş yapın</p>
          </div>
        </div>

        <form onSubmit={handleSubmit}>
          <label htmlFor="username">Kullanıcı adı</label>
          <input
            id="username"
            value={username}
            onChange={(e) => setUsername(e.target.value)}
            autoFocus
            required
          />

          <label htmlFor="password">Şifre</label>
          <input
            id="password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            required
          />

          {error && <div className="alert alert-error">{error}</div>}

          <button type="submit" disabled={submitting}>
            {submitting ? "Giriş yapılıyor…" : "Giriş yap"}
          </button>
        </form>
      </div>
    </div>
  );
}
