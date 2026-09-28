import { useState } from "react";
import { PageHeader } from "../components/chrome";

const STEPS = ["Basic information", "Visibility & access", "Default channels", "Review"];

export function CreateCommunityPage() {
  const [step, setStep] = useState(0);
  const [name, setName] = useState("Relayra Design Community");
  const [description, setDescription] = useState("");
  const [visibility, setVisibility] = useState("Public");

  return (
    <>
      <PageHeader title="Create a Community" subtitle="Build a space for designers, builders, and product people" />
      <div className="app-content">
        <div style={{ display: "grid", gridTemplateColumns: "minmax(0, 1fr) 320px", gap: 16 }}>
          <div className="card">
            <div style={{ display: "flex", gap: 8, marginBottom: 16 }}>
              {STEPS.map((s, i) => (
                <span
                  key={s}
                  style={{
                    fontSize: 12,
                    padding: "4px 10px",
                    borderRadius: 999,
                    background: i === step ? "var(--accent-primary)" : "var(--bg-hover)",
                    color: "#fff",
                  }}
                >
                  {i + 1}. {s}
                </span>
              ))}
            </div>
            {step === 0 && (
              <div className="stack">
                <div className="auth-field">
                  <label className="auth-label" htmlFor="community-name">Community name</label>
                  <input id="community-name" className="auth-input" value={name} onChange={(e) => setName(e.target.value)} maxLength={100} />
                </div>
                <div className="auth-field">
                  <label className="auth-label" htmlFor="community-desc">Description</label>
                  <textarea id="community-desc" className="auth-input" rows={3} value={description} onChange={(e) => setDescription(e.target.value)} maxLength={500} placeholder="A community for designers, builders, and product people..." />
                </div>
              </div>
            )}
            {step === 1 && (
              <div className="stack">
                {["Public", "Private", "Hidden"].map((v) => (
                  <button
                    key={v}
                    className="card"
                    style={{ textAlign: "left", borderColor: visibility === v ? "var(--accent-primary)" : undefined }}
                    onClick={() => setVisibility(v)}
                  >
                    <h3>{v} community</h3>
                    <p>{v === "Public" ? "Anyone can find this community, view its content, and request to join." : v === "Private" ? "Only invited members can find and join this community." : "Only invited members can join. This community won't appear in search."}</p>
                  </button>
                ))}
              </div>
            )}
            {step === 2 && (
              <div className="stack">
                {["announcements", "general", "introductions", "resources", "showcase"].map((c) => (
                  <label key={c} className="auth-check card" style={{ padding: 12 }}>
                    <input type="checkbox" defaultChecked={c === "announcements" || c === "general"} /> #{c}
                  </label>
                ))}
              </div>
            )}
            {step === 3 && (
              <div className="stack">
                <div className="card">
                  <h3>{name}</h3>
                  <p>{visibility} · 5 starter channels</p>
                </div>
                <p style={{ color: "var(--text-secondary)", fontSize: 13 }}>
                  By creating a community, you agree to Relayra&apos;s Community Guidelines and Terms of Service.
                </p>
              </div>
            )}
            <div className="row-between" style={{ marginTop: 16 }}>
              <button className="auth-btn-ghost" style={{ width: "auto", padding: "8px 20px" }} disabled={step === 0} onClick={() => setStep((s) => Math.max(0, s - 1))}>
                Back
              </button>
              {step < STEPS.length - 1 ? (
                <button className="auth-btn-primary" style={{ width: "auto", padding: "8px 20px" }} onClick={() => setStep((s) => Math.min(STEPS.length - 1, s + 1))}>
                  Continue
                </button>
              ) : (
                <button className="auth-btn-primary" style={{ width: "auto", padding: "8px 20px" }}>
                  Create Community
                </button>
              )}
            </div>
          </div>
          <div className="card">
            <h3>Live preview</h3>
            <p>This is how your community will appear to others</p>
            <div className="card" style={{ marginTop: 12 }}>
              <h3>{name || "Untitled community"}</h3>
              <p>{visibility} community</p>
              <div style={{ fontSize: 12, color: "var(--text-muted)", marginTop: 8 }}>
                # announcements · # general · # introductions
              </div>
            </div>
          </div>
        </div>
      </div>
    </>
  );
}
