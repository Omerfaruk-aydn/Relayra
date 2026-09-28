import { describe, expect, it } from "vitest";
import { isOfflineError, toMessage } from "./async";

describe("isOfflineError", () => {
  it("treats TypeError as offline", () => {
    expect(isOfflineError(new TypeError("Failed to fetch"))).toBe(true);
  });

  it("does not label server errors offline", () => {
    const err = new Error("Forbidden") as Error & { status: number };
    err.status = 403;
    expect(isOfflineError(err)).toBe(false);
  });

  it("detects fetch failure messages without status", () => {
    expect(isOfflineError(new Error("load failed"))).toBe(true);
  });
});

describe("toMessage", () => {
  it("returns error message", () => {
    expect(toMessage(new Error("boom"), "fallback")).toBe("boom");
  });

  it("falls back for non-errors", () => {
    expect(toMessage(null, "fallback")).toBe("fallback");
  });
});
