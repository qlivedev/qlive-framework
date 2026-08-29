declare module "babel-plugin-track-usage" {
  import type { PluginObj, PluginPass } from "@babel/core";

  export default function trackUsage(babelCore: typeof import("@babel/core")): PluginObj<PluginPass>;
}

declare module "babel-plugin-track-usage/data" {
  interface TrackUsageSnapshot {
    usages: Record<string, unknown>;
  }

  const data: {
    clear(): void;
    get(): TrackUsageSnapshot;
  };

  export default data;
}
