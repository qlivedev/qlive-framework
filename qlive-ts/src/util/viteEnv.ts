/*
 * `import.meta.env` is Vite's, not a standard part of ImportMeta. It is there when Vite processed the module
 * and simply absent from the built dist when a consumer loads it outside Vite, so every lookup has to
 * tolerate it missing.
 *
 * Read structurally rather than by augmenting ImportMeta: consuming apps bring their own vite/client
 * declaration, and two declarations of the same member would collide. Keeping it behind these functions means
 * the awkward part exists once, with a name, instead of at every call site.
 *
 * Each function spells out the whole `import.meta.env.X` expression because that is the shape Vite replaces
 * at build time -- reading `env` into a local first would leave the lookup to run against nothing.
 */
type ViteImportMeta = {
    env?: {
        DEV?: boolean
        BASE_URL?: string
    }
};

/**
 * True when this module is running under `vite dev`.
 */
export function isViteDev(): boolean
{
    return (import.meta as ViteImportMeta).env?.DEV === true;
}


/**
 * The application's Vite `base`, always with a trailing slash. Defaults to "/" so that a consumer loading the
 * built package outside Vite still gets a usable base.
 */
export function viteBaseUrl(): string
{
    const base = (import.meta as ViteImportMeta).env?.BASE_URL ?? "/";

    return base.endsWith("/") ? base : base + "/";
}
