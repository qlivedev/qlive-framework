import type {PluginObj, PluginPass} from "@babel/core";

/**
 * The babel plugin that records tracked calls into {@link ./trackUsageData}. Vendored
 * JavaScript, so this declares the surface rather than describing the implementation.
 *
 * Called with babel itself: the plugin reads `t.types` off it and returns the visitor.
 */
export default function trackUsage(babelCore: typeof import("@babel/core")): PluginObj<PluginPass>;
