import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

function isEmail(value: string): boolean {
  return /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(value);
}

export function LoginPage() {
  const { login, loading } = useAuth();
  const navigate = useNavigate();
  const [identifier, setIdentifier] = useState("");
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(true);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!identifier.trim()) {
      next.identifier = "Email or username is required.";
    } else if (identifier.includes("@") && !isEmail(identifier.trim())) {
      next.identifier = "Email must be valid.";
    }
    if (!password) {
      next.password = "Password is required.";
    }
    setErrors(next);
    if (Object.keys(next).length > 0) {
      return;
    }
    setServerError(null);
    try {
      await login(identifier.trim(), password);
      if (!remember) {
        sessionStorage.removeItem("relayra.session");
      }
      navigate("/", { replace: true });
    } catch (err) {
      setServerError(err instanceof Error ? err.message : "Login failed.");
    }
  }

  return (
    <div className="auth-split">
      <div className="auth-form-pane">
        <div className="auth-form-inner">
          <div className="auth-brand">
            <span className="auth-brand-mark">R</span> Relayra
          </div>
          <p className="auth-eyebrow">Welcome back</p>
          <h1 className="auth-title">
            Sign in to your <span className="auth-title-accent">Relayra</span> account
          </h1>
          <p className="auth-subtitle">
            Continue building, collaborating, and turning ideas into real products.
          </p>
          {serverError && <div className="auth-alert">{serverError}</div>}
          <form onSubmit={onSubmit} noValidate>
            <div className="auth-field">
              <label className="auth-label" htmlFor="identifier">
                Email or username
              </label>
              <input
                id="identifier"
                className={`auth-input${errors.identifier ? " auth-input-error" : ""}`}
                value={identifier}
                onChange={(e) => setIdentifier(e.target.value)}
                placeholder="you@company.com"
                autoComplete="username"
              />
              {errors.identifier && <div className="auth-field-error">{errors.identifier}</div>}
            </div>
            <div className="auth-field">
              <label className="auth-label" htmlFor="password">
                Password
              </label>
              <div className="auth-input-wrap">
                <input
                  id="password"
                  type="password"
                  className={`auth-input${errors.password ? " auth-input-error" : ""}`}
                  value={password}
                  onChange={(e) => setPassword(e.target.value)}
                  placeholder="Enter your password"
                  autoComplete="current-password"
                />
              </div>
              {errors.password && <div className="auth-field-error">{errors.password}</div>}
            </div>
            <div className="auth-row">
              <label className="auth-check">
                <input
                  type="checkbox"
                  checked={remember}
                  onChange={(e) => setRemember(e.target.checked)}
                />
                Remember me
              </label>
              <span>Password reset is handled by your workspace admin.</span>
            </div>
            <button className="auth-btn-primary" type="submit" disabled={loading}>
              {loading ? "Signing in..." : "Sign in"}
            </button>
          </form>
          <p className="auth-switch">
            Don&apos;t have an account? <Link to="/register">Create an account</Link>
          </p>
        </div>
      </div>
      <div className="auth-hero-pane" aria-hidden="true">
        <div className="auth-hero-card">
          <h2>
            Build together, <span className="auth-title-accent">go further.</span>
          </h2>
          <p>Relayra helps teams and communities connect, collaborate, and create what&apos;s next.</p>
          <ul className="auth-hero-points">
            <li>
              <strong>Stronger communities</strong>
              <span>Bring people together around shared goals and ideas.</span>
            </li>
            <li>
              <strong>Seamless collaboration</strong>
              <span>Tools that make it simple to organize, share, and move forward.</span>
            </li>
            <li>
              <strong>Built for what&apos;s next</strong>
              <span>A flexible, modern platform that grows with you.</span>
            </li>
          </ul>
        </div>
      </div>
    </div>
  );
}
