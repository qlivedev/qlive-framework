/*
 * Build-time half of QLive, imported from "@quinscape/qlive-ts/vite".
 *
 * Kept apart from the package's main entry because nothing in here runs in a browser: it is Node code an
 * application's vite.config.ts loads, and pulling babel into the runtime bundle's dependency graph to say
 * so would be wrong. The two entries are built separately for the same reason.
 */
export {trackUsage} from "./trackUsage";
export type {TrackUsagePluginOptions, TrackedFunctionSpec} from "./trackUsage";
