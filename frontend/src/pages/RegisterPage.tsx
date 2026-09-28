import { useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { useAuth } from "../auth/AuthContext";

export function RegisterPage() {
  const { register, loading } = useAuth();
  const navigate = useNavigate();
  const [username, setUsername] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [serverError, setServerError] = useState<string | null>(null);

  async function onSubmit(e: FormEvent) {
    e.preventDefault();
    const next: Record<string, string> = {};
    if (!/^[a-zA-Z0-9_.]{3,32}$/.test(username.trim())) {
      next.username = "Username must be 3-32 chars: letters, digits, underscore and dot.";
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email.trim()) || email.trim().length > 320) {
      next.email = "Email must be valid.";
    }
    if (password.length < 8 || password.length > 72) {
      next.password = "Password must contain between 8 and 72 characters.";
    }
    if (confirm !== password) {
      next.confirm = "Passwords do not match.";
    }
    setErrors(next);
    if (Object.keys(next).length > 0) {
      return;
    }
    setServerError(null);
    try {
      await register(username.trim(), email.trim(), password);
      navigate("/", { replace: true });
    } catch (err) {
      const withFields = err as Error & { fields?: Record<string, string> };
      if (withFields.fields) {
        setErrors(withFields.fields);
      }
      setServerError(err instanceof Error ? err.message : "Registration failed.");
    }
  }

  return (
    <div className="auth-split">
      <div className="auth-form-pane">
        <div className="auth-form-inner">
          <div className="auth-brand">
            <span className="auth-brand-mark">R</span> Relayra
          </div>
          <h1 className="auth-title">Create your account</h1>
          <p className="auth-subtitle">
            Join a growing global community of builders, designers, and product people on Relayra.
          </p>
          {serverError && <div className="auth-alert">{serverError}</div>}
          <form onSubmit={onSubmit} noValidate>
            <div className="auth-field">
              <label className="auth-label" htmlFor="username">
                Username
              </label>
              <input
                id="username"
                className={`auth-input${errors.username ? " auth-input-error" : ""}`}
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                placeholder="johndoe"
                autoComplete="username"
              />
              {errors.username ? (
                <div className="auth-field-error">{errors.username}</div>
              ) : (
                <div className="auth-field-error" style={{ color: "var(--text-muted)" }}>
                  This will be your public profile name.
                </div>
              )}
            </div>
            <div className="auth-field">
              <label className="auth-label" htmlFor="email">
                Email address
              </label>
              <input
                id="email"
                className={`auth-input${errors.email ? " auth-input-error" : ""}`}
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                placeholder="you@company.com"
                autoComplete="email"
              />
              {errors.email && <div className="auth-field-error">{errors.email}</div>}
            </div>
            <div className="auth-field">
              <label className="auth-label" htmlFor="password">
                Password
              </label>
              <input
                id="password"
                type="password"
                className={`auth-input${errors.password ? " auth-input-error" : ""}`}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Create a password"
                autoComplete="new-password"
              />
              {errors.password && <div className="auth-field-error">{errors.password}</div>}
            </div>
            <div className="auth-field">
              <label className="auth-label" htmlFor="confirm">
                Confirm password
              </label>
              <input
                id="confirm"
                type="password"
                className={`auth-input${errors.confirm ? " auth-input-error" : ""}`}
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                placeholder="Confirm your password"
                autoComplete="new-password"
              />
              {errors.confirm && <div className="auth-field-error">{errors.confirm}</div>}
            </div>
            <button className="auth-btn-primary" type="submit" disabled={loading}>
              {loading ? "Creating..." : "Create account"}
            </button>
          </form>
          <p className="auth-switch">
            Already have an account? <Link to="/login">Sign in</Link>
          </p>
        </div>
      </div>
      <div className="auth-hero-pane" aria-hidden="true">
        <div className="auth-hero-card">
          <h2>
            Join communities. <span className="auth-title-accent">Build what&apos;s next.</span>
          </h2>
          <p>Relayra brings together designers, builders, and product people to share knowledge.</p>
          <ul className="auth-hero-points">
            <li>
              <strong>Find your people</strong>
              <span>Join communities around your interests and skills.</span>
            </li>
            <li>
              <strong>Share and collaborate</strong>
              <span>Exchange ideas, get feedback, and work together on new projects.</span>
            </li>
            <li>
              <strong>Grow your opportunities</strong>
              <span>Discover events, find collaborators, and unlock new career possibilities.</span>
            </li>
          </ul>
        </div>
      </div>
    </div>
  );
}
