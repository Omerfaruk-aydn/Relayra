import { avatarColor, initials } from "../lib/format";

interface AvatarProps {
  id: string;
  name: string;
  size?: number;
  presence?: "online" | "idle" | "offline";
}

export function Avatar({ id, name, size = 40, presence }: AvatarProps) {
  return (
    <span style={{ position: "relative", display: "inline-flex", flexShrink: 0 }}>
      <span
        aria-hidden="true"
        style={{
          width: size,
          height: size,
          borderRadius: "50%",
          background: avatarColor(id),
          color: "#fff",
          fontWeight: 700,
          fontSize: Math.max(12, size * 0.36),
          display: "inline-flex",
          alignItems: "center",
          justifyContent: "center",
        }}
      >
        {initials(name)}
      </span>
      {presence && (
        <span
          className={`presence-dot presence-${presence}`}
          style={{
            position: "absolute",
            right: 0,
            bottom: 0,
            width: Math.max(10, size * 0.28),
            height: Math.max(10, size * 0.28),
            border: "2px solid var(--bg-panel)",
          }}
        />
      )}
    </span>
  );
}
