import { describe, expect, it, vi, beforeEach } from "vitest";
import { fetchGreeting } from "@qlive/frontend-lib";
import { authStrategyFixtures } from "./authStrategies";

describe("fetchGreeting stress scenarios", () => {
  beforeEach(() => {
    vi.stubGlobal(
      "fetch",
      vi.fn(async () => new Response("Hello, world, from qlive backend-lib!", { status: 200 })),
    );
  });

  it("succeeds for the anonymous strategy", async () => {
    const result = await fetchGreeting(authStrategyFixtures.anonymous);
    expect(result).toContain("Hello");
  });

  it("propagates a readable error on non-2xx responses", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => new Response("nope", { status: 500 })));
    await expect(fetchGreeting(authStrategyFixtures.anonymous)).rejects.toThrow(/failed: 500/);
  });
});
