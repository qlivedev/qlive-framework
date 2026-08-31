/*
 * `import.meta.env` is Vite's, not a standard part of ImportMeta. It is there
 * when Vite processed the module and simply absent from the built dist when a
 * consumer loads it outside Vite, so the lookup has to tolerate it missing.
 *
 * Read structurally rather than by augmenting ImportMeta: consuming apps bring
 * their own vite/client declaration, and two declarations of the same member
 * would collide. Keeping it behind this function means the awkward part exists
 * once, with a name, instead of at every call site.
 */
type ViteImportMeta = {
    env?: {
        DEV?: boolean
    }
};

/**
 * True when this module is running under `vite dev`.
 */
export default function isViteDev(): boolean
{
    return (import.meta as ViteImportMeta).env?.DEV === true;
}
