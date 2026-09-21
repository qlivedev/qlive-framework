/** One source tree's analysis as the plugin leaves it. */
export interface TrackUsageSnapshot
{
    usages: Record<string, unknown>;
}

/**
 * Where the plugin accumulates what it finds. A module-level singleton, because babel
 * gives a plugin no way to hand results back other than a side channel: clear it, run
 * the transform over every file, then read it.
 */
declare const data: {
    clear(): void;
    get(): TrackUsageSnapshot;
};

export default data;
