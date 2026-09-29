import { describe, expect, it } from "vitest";
import { isOfflineError, toMessage } from "./async";

describe("offline error edge cases", () => {
  it("lets an HTTP status override an offline-looking message", () => {
    expect(isOfflineError({ status: 500, message: "load failed" })).toBe(false);
  });

  it("uses the fallback for an Error with an empty message", () => {
    expect(toMessage(new Error(""), "fb")).toBe("fb");
  });

  it("recognizes the FETCH_FAILED error code", () => {
    expect(isOfflineError({ code: "FETCH_FAILED" })).toBe(true);
  });

  it("does not treat a regular Error as offline in Node", () => {
    expect(isOfflineError(new Error("ok"))).toBe(false);
  });
});
