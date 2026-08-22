import { useEffect, useState, type ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";
import ttiLogo from "../assets/logo.png";
import { LogoutIcon } from "./Icons";

export function Layout({ children }: { children: ReactNode }) {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const [isScrolled, setIsScrolled] = useState(false);

  useEffect(() => {
    function onScroll() {
      setIsScrolled(window.scrollY > 4);
    }
    window.addEventListener("scroll", onScroll, { passive: true });
    return () => window.removeEventListener("scroll", onScroll);
  }, []);

  function handleLogout() {
    logout();
    navigate("/login", { replace: true });
  }

  return (
    <div className="app-shell">
      <header className={`app-header ${isScrolled ? "is-scrolled" : ""}`}>
        <Link to="/datasets" className="app-brand">
          <img src={ttiLogo} alt="TTI" className="app-brand-logo" />
          <span className="app-brand-text">
            <span className="app-brand-title">TTI Veri Yönetimi</span>
            <span className="app-brand-subtitle">Data Management Platform</span>
          </span>
        </Link>

        <div className="app-user-menu">
          <div className="app-user-info">
            <span className="app-user-avatar">A</span>
            <span className="app-user-name">Admin</span>
          </div>
          <button className="app-logout-button" onClick={handleLogout}>
            <LogoutIcon width={15} height={15} />
            Çıkış Yap
          </button>
        </div>
      </header>
      <main className="app-content">{children}</main>
    </div>
  );
}
