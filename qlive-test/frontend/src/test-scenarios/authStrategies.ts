import { createFrameworkConfig, type FrameworkConfig } from "@quinscape/qlive-ts";

/**
 * Fixtures only - deliberately not imported by ../framework-wiring. This
 * folder exists purely to exercise/stress-test the framework: multiple auth
 * strategies, edge cases, and intentionally broken configs.
 */
export const authStrategyFixtures: Record<string, FrameworkConfig> = {
  anonymous: createFrameworkConfig(),
  apiKeyHeader: createFrameworkConfig({ apiBaseUrl: "/api" }),
  brokenBaseUrl: createFrameworkConfig({ apiBaseUrl: "" }),
};
