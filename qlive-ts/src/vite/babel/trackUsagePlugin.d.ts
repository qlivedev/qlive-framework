import type {PluginObj, PluginPass} from "@babel/core";

/**
 * A tracked call left out of the data because one of its arguments did not evaluate statically. What the
 * plugin hands its `onSkippedCall` option.
 */
export interface SkippedCall
{
    /** symbolic name of the tracked function, its key in `trackedFunctions` */
    name: string;
    /** file the call is in, as babel was given it */
    filename: string;
    /** 1-based line of the part that did not evaluate, null where babel has no location */
    line: number | null;
    /** 1-based column of it */
    column: number | null;
    /** 0-based index of the argument */
    argument: number;
    /** property path from the argument to the part, like ".config.sortFields[0]", "" for the argument itself */
    path: string;
    /** source text of the part */
    code: string;
}

/**
 * The babel plugin that records tracked calls into {@link ./trackUsageData}. Vendored
 * JavaScript, so this declares the surface rather than describing the implementation.
 *
 * Called with babel itself: the plugin reads `t.types` off it and returns the visitor.
 */
export default function trackUsage(babelCore: typeof import("@babel/core")): PluginObj<PluginPass>;
