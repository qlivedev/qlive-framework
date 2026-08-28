import { describe, expect, it } from "vitest";
import { createFrameworkConfig } from "./index";

describe("createFrameworkConfig", () => {
  it("defaults apiBaseUrl to /api", () => {
    expect(createFrameworkConfig().apiBaseUrl).toBe("/api");
  });

  it("allows overriding apiBaseUrl", () => {
    expect(createFrameworkConfig({ apiBaseUrl: "/custom" }).apiBaseUrl).toBe("/custom");
  });
});
