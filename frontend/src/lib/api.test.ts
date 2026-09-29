import { afterEach, describe, expect, it, vi } from "vitest";
import { apiDelete, apiGet } from "./api";

afterEach(() => {
  vi.unstubAllGlobals();
});

describe("apiGet", () => {
  it("returns parsed JSON for a successful response", async () => {
    const payload = { id: "user-1", name: "Ada" };
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify(payload), {
          status: 200,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );

    await expect(apiGet<typeof payload>("/api/users/user-1", "token")).resolves.toEqual(payload);
  });

  it("throws an error with the API code and status", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(
          JSON.stringify({
            status: 404,
            code: "USER_NOT_FOUND",
            message: "User not found",
          }),
          { status: 404, headers: { "Content-Type": "application/json" } },
        ),
      ),
    );

    const request = apiGet("/api/users/missing", "token");
    await expect(request).rejects.toMatchObject({
      message: "User not found",
      code: "USER_NOT_FOUND",
      status: 404,
    });
  });

  it("returns undefined for an empty successful response", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 200 })));

    await expect(apiGet<undefined>("/api/empty", "token")).resolves.toBeUndefined();
  });
});

describe("apiDelete", () => {
  it("resolves void for a 204 response", async () => {
    vi.stubGlobal("fetch", vi.fn().mockResolvedValue(new Response(null, { status: 204 })));

    await expect(apiDelete("/api/users/user-1", "token")).resolves.toBeUndefined();
  });

  it("throws with status 403 for a forbidden response", async () => {
    vi.stubGlobal(
      "fetch",
      vi.fn().mockResolvedValue(
        new Response(JSON.stringify({ message: "Forbidden", status: 403, code: "FORBIDDEN" }), {
          status: 403,
          headers: { "Content-Type": "application/json" },
        }),
      ),
    );

    await expect(apiDelete("/api/users/user-1", "token")).rejects.toMatchObject({
      message: "Forbidden",
      status: 403,
    });
  });
});
