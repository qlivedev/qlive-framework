/*
 * Generates src/styles/qlive.css from the six brand colours.
 *
 * Every derived value is a mix of two palette colours -- never a mix with
 * black or white -- so nothing outside the palette ever reaches the page.
 * The mixing is spectral (Kubelka-Munk, via spectral.js) rather than a
 * linear sRGB interpolation: pigment mixing keeps a hue's identity through
 * the middle of a blend, where averaging channels desaturates it into grey.
 *
 * How dark or light each variant ends up is not chosen by eye. Each one is
 * mixed just far enough to clear WCAG AA body text (4.5:1) on the background
 * it will sit on, and no further, so it stays as close to the brand hue as
 * readability allows.
 *
 * Run with: pnpm palette
 */
import {Color, mix} from "spectral.js";
import {writeFileSync} from "node:fs";

/*
 * How two palette colours are blended.
 *
 *   "spectral"    Kubelka-Munk pigment mixing via spectral.js. Blends behave
 *                 the way paint does, so a mix travels through hues a channel
 *                 average never visits -- the ink being a blue-black, mixing
 *                 into it carries the warm hues through olive and green and
 *                 takes the neutral ramp through blue.
 *   "perceptual"  interpolate in OKLab. Holds each hue steady while only its
 *                 lightness moves.
 *
 * Spectral is the choice. What a variant may be used for is settled by
 * measurement further down -- every one of them is mixed until it clears the
 * contrast target -- so the mixer is free to be the interesting one. The
 * hue it arrives at is a look; the contrast it arrives at is the constraint,
 * and the constraint is enforced either way.
 */
const MIX_MODE = "spectral";

const INK = "#000217";
const CREAM = "#fff8e5";
const ORANGE = "#ff6b01";
const AMBER = "#ffbe00";
const SALMON = "#ed8c57";
const TEAL = "#075d5a";

const channels = (hex) => hex.slice(1).match(/../g).map((h) => parseInt(h, 16));
const toHex = (rgb) =>
    "#" + rgb.map((x) => Math.round(Math.max(0, Math.min(255, x))).toString(16).padStart(2, "0")).join("");

const uncompand = (x) => (x <= 0.04045 ? x / 12.92 : ((x + 0.055) / 1.055) ** 2.4);
const compand = (x) => (x <= 0.0031308 ? x * 12.92 : 1.055 * x ** (1 / 2.4) - 0.055);

const toOKLab = (hex) => {
    const [r, g, b] = channels(hex).map((c) => uncompand(c / 255));
    const l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b);
    const m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b);
    const s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b);
    return [
        0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s,
        1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s,
        0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s,
    ];
};

const fromOKLab = ([L, A, B]) => {
    const l = (L + 0.3963377774 * A + 0.2158037573 * B) ** 3;
    const m = (L - 0.1055613458 * A - 0.0638541728 * B) ** 3;
    const s = (L - 0.0894841775 * A - 1.2914855480 * B) ** 3;
    return toHex([
        compand(4.0767416621 * l - 3.3077115913 * m + 0.2309699292 * s) * 255,
        compand(-1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s) * 255,
        compand(-0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s) * 255,
    ]);
};

/** Mix of `a` into `b`, `t` being how much of `b` ends up in it. */
const blend = (a, b, t) => {
    if (t <= 0) return a;
    if (t >= 1) return b;
    if (MIX_MODE === "spectral")
    {
        return mix([new Color(a), 1 - t], [new Color(b), t]).toString({format: "hex"});
    }
    const A = toOKLab(a), B = toOKLab(b);
    return fromOKLab(A.map((v, i) => v + (B[i] - v) * t));
};
const luminance = (hex) =>
    channels(hex)
        .map((c) => c / 255)
        .map((c) => (c <= 0.04045 ? c / 12.92 : ((c + 0.055) / 1.055) ** 2.4))
        .reduce((acc, c, i) => acc + [0.2126, 0.7152, 0.0722][i] * c, 0);

/** WCAG 2 contrast ratio. */
const contrast = (a, b) => {
    const [hi, lo] = [luminance(a), luminance(b)].sort((x, y) => y - x);
    return (hi + 0.05) / (lo + 0.05);
};

/** The least amount of `agent` that makes `hue` readable on `bg`. */
const readable = (hue, agent, bg, target = 4.5) => {
    for (let i = 0; i <= 100; i++)
    {
        const candidate = blend(hue, agent, i / 100);
        if (contrast(candidate, bg) >= target)
        {
            return {hex: candidate, pct: i, ratio: contrast(candidate, bg)};
        }
    }
    return {hex: blend(hue, agent, 1), pct: 100, ratio: contrast(bg, bg)};
};

// Text-safe variant of every hue, per theme.
const onCream = Object.fromEntries(
    [["orange", ORANGE], ["amber", AMBER], ["salmon", SALMON], ["teal", TEAL]]
        .map(([name, hue]) => [name, readable(hue, INK, CREAM)])
);
const onInk = Object.fromEntries(
    [["orange", ORANGE], ["amber", AMBER], ["salmon", SALMON], ["teal", TEAL]]
        .map(([name, hue]) => [name, readable(hue, CREAM, INK)])
);

/*
 * Neutral ramp: the two brand neutrals blended into each other, so even the
 * greys carry the palette instead of falling back to slate.
 *
 * The first three stops carry text, so they are solved for contrast rather
 * than set to a fixed mix -- with a mixer that travels through hues, a fixed
 * fraction lands wherever the curve happens to be, and gray-3 fell to 2.40 on
 * the cream. Each one is mixed only as far as its target allows. The last
 * three are surfaces (borders, code backgrounds) and stay fixed, since their
 * job is to stay close to the page.
 */
const rampStop = (from, to, target) => {
    let last = from;
    for (let i = 0; i <= 100; i++)
    {
        const candidate = blend(from, to, i / 100);
        if (contrast(candidate, to) < target) return last;
        last = candidate;
    }
    return last;
};

// gray-1..3 carry text: strong, body, muted. gray-4..6 are surfaces.
const TEXT_TARGETS = [12, 7, 4.5];
const SURFACE_STOPS = [0.68, 0.84, 0.92];

const ramp = (from, to) => [
    ...TEXT_TARGETS.map((target) => rampStop(from, to, target)),
    ...SURFACE_STOPS.map((t) => blend(from, to, t)),
];

const darkGrays = ramp(CREAM, INK);
const lightGrays = ramp(INK, CREAM);

/*
 * Brand surfaces -- the top bar and the wordmark on it.
 *
 * The bar is the orange mixed into each neutral, which lands on a deep teal
 * against the ink and a light orange against the cream: the mixer carries a
 * warm hue through green on its way to a blue-black, and both ends of that
 * belong to the palette.
 *
 * The wordmark sits on the bar rather than the page, so it is measured
 * against the bar and mixed only as far as clearing AA there requires.
 */
const darkHeader = blend(ORANGE, INK, 0.70);
const lightHeader = TEAL;

/*
 * The bar is dark in both themes, so what sits directly on it -- wordmark,
 * social icons, theme select -- is coloured against the bar rather than
 * inheriting the page's. Without that the light theme puts its dark-on-light
 * foreground onto a dark bar: the social icon lands at 1.60 on the teal.
 *
 * All of it is the cream, the wordmark included, which is also the strongest
 * reading either bar offers. The amber is the hover, being the one other hue
 * that clears AA on both.
 */
const onBar = CREAM;
const onBarHover = AMBER;

// Subtle fills: the accent taken most of the way to the background.
const darkAccentLow = blend(ORANGE, INK, 0.82);
const lightAccentLow = blend(ORANGE, CREAM, 0.84);

const row = (name, base, v, bg) =>
    ` *     ${name.padEnd(7)} ${base} + ${String(v.pct).padStart(2)}% ` +
    `${bg === CREAM ? "ink  " : "cream"} = ${v.hex}   ${v.ratio.toFixed(2)}`;

const grays = (list) =>
    list.map((hex, i) => `    --sl-color-gray-${i + 1}: ${hex};`).join("\n");

const css = `/*
 * QLive brand palette -- GENERATED by tooling/palette.mjs, do not edit.
 * Change the six colours or the contrast target there and run \`pnpm palette\`.
 *
 *   ${INK}  ink      ${CREAM}  cream
 *   ${ORANGE}  orange   ${AMBER}  amber   ${SALMON}  salmon   ${TEAL}  teal
 *
 * Which hue may carry text is measured, not chosen. The brand hues as they
 * are, against the two neutrals:
 *
 *                on cream   on ink
 *     orange       ${contrast(ORANGE, CREAM).toFixed(2)}      ${contrast(ORANGE, INK).toFixed(2)}
 *     amber        ${contrast(AMBER, CREAM).toFixed(2)}     ${contrast(AMBER, INK).toFixed(2)}
 *     salmon       ${contrast(SALMON, CREAM).toFixed(2)}      ${contrast(SALMON, INK).toFixed(2)}
 *     teal         ${contrast(TEAL, CREAM).toFixed(2)}      ${contrast(TEAL, INK).toFixed(2)}
 *
 * Only teal clears AA body text on the cream; only teal fails it on the ink.
 * So each hue is spectrally mixed toward the opposite neutral, just far
 * enough to clear 4.5:1 and no further:
 *
${row("orange", ORANGE, onCream.orange, CREAM)}
${row("amber", AMBER, onCream.amber, CREAM)}
${row("salmon", SALMON, onCream.salmon, CREAM)}
${row("teal", TEAL, onCream.teal, CREAM)}
 *
${row("orange", ORANGE, onInk.orange, INK)}
${row("amber", AMBER, onInk.amber, INK)}
${row("salmon", SALMON, onInk.salmon, INK)}
${row("teal", TEAL, onInk.teal, INK)}
 */

/* Dark theme -- Starlight's default. Token names describe the dark role:
   --sl-color-white is the strongest text, --sl-color-black is the page. */
:root {
    --sl-color-accent-low: ${darkAccentLow};
    --sl-color-accent: ${ORANGE};
    --sl-color-accent-high: ${onInk.orange.hex};

    /* Starlight colours prose links with this and would otherwise take the raw
       accent, which is only readable on one of the two backgrounds. */
    --sl-color-text-accent: ${onInk.orange.hex};

    /* the top bar and the wordmark on it */
    --sl-color-bg-nav: ${darkHeader};
    --qlive-on-bar: ${onBar};
    --qlive-on-bar-hover: ${onBarHover};

    --sl-color-white: ${CREAM};
${grays(darkGrays)}
    --sl-color-black: ${INK};

    --qlive-orange: ${onInk.orange.hex};
    --qlive-amber: ${onInk.amber.hex};
    --qlive-salmon: ${onInk.salmon.hex};
    --qlive-teal: ${onInk.teal.hex};
    --qlive-on-accent: ${INK};
}

/* Light theme -- the same tokens inverted onto the cream. */
:root[data-theme="light"] {
    --sl-color-accent-low: ${lightAccentLow};
    --sl-color-accent: ${ORANGE};
    --sl-color-accent-high: ${onCream.orange.hex};
    --sl-color-text-accent: ${onCream.orange.hex};

    --sl-color-bg-nav: ${lightHeader};
    --sl-color-gray-7: ${lightGrays[5]};
    --qlive-on-bar: ${onBar};
    --qlive-on-bar-hover: ${onBarHover};

    --sl-color-white: ${INK};
${grays(lightGrays)}
    --sl-color-black: ${CREAM};

    --qlive-orange: ${onCream.orange.hex};
    --qlive-amber: ${onCream.amber.hex};
    --qlive-salmon: ${onCream.salmon.hex};
    --qlive-teal: ${onCream.teal.hex};
    --qlive-on-accent: ${CREAM};
}

/* Full-strength orange is ${contrast(ORANGE, CREAM).toFixed(2)} on the cream, so where the theme fills a
   surface with the accent and puts text on it, the text goes on the orange
   rather than in it. Both of these are fills: the ink on top of them reads at
   ${contrast(INK, ORANGE).toFixed(2)}, where the orange as link text would have read at ${contrast(ORANGE, CREAM).toFixed(2)}. */
:root[data-theme="light"] .action.primary,
:root[data-theme="light"] a[aria-current="page"] {
    background: var(--sl-color-accent);
    color: var(--qlive-on-accent);
}

/* The bar carries its own colour, so what sits on it is measured against the
   bar rather than the page: the cream reads at ${contrast(onBar, darkHeader).toFixed(2)} on the dark bar
   and ${contrast(onBar, lightHeader).toFixed(2)} on the teal one.

   The search field is left out on purpose: it brings its own background, so
   its text is measured against that rather than against the bar. */
.header {
    background: var(--sl-color-bg-nav);
}

.site-title,
.header .social-icons a,
.header starlight-theme-select,
.header starlight-theme-select select {
    color: var(--qlive-on-bar);
}

.site-title:hover,
.header .social-icons a:hover {
    color: var(--qlive-on-bar-hover);
}

/* Amber is the brightest thing in the palette on ink -- kept for the one
   mark that is meant to catch the eye. */
mark {
    background: var(--qlive-amber);
    color: var(--qlive-on-accent);
}
`;

writeFileSync(new URL("../src/styles/qlive.css", import.meta.url), css);

/*
 * An Inkscape palette, so a diagram drawn by hand uses these colours and not
 * approximations of them.
 *
 * Besides the six brand colours it carries a "both themes" set: a single SVG
 * is served to the light and the dark page alike, and none of the six clears
 * 3:1 on both backgrounds -- they are 19.4:1 apart. Each of these is its hue
 * walked toward the opposite neutral until it does, which is the most of a
 * hue that survives having to work on either page.
 */
const bothThemes = (from, to, label) => {
    let best = null;
    for (let i = 0; i <= 100; i++)
    {
        const c = blend(from, to, i / 100);
        const onCream = contrast(c, CREAM), onInk = contrast(c, INK);
        if (onCream < 3 || onInk < 3) continue;
        // the most balanced point of the usable band
        const skew = Math.abs(onCream - onInk);
        if (!best || skew < best.skew) best = {hex: c, onCream, onInk, skew, label};
    }
    return best;
};

const diagram = [
    bothThemes(ORANGE, INK, "diagram bronze"),
    bothThemes(TEAL, CREAM, "diagram teal"),
    bothThemes(SALMON, INK, "diagram sage"),
].filter(Boolean);

const gplRow = (hex, name) => {
    const [r, g, b] = channels(hex);
    return `${String(r).padStart(3)} ${String(g).padStart(3)} ${String(b).padStart(3)}\t${name}`;
};

const gpl = [
    "GIMP Palette",
    "Name: QLive",
    "Columns: 6",
    "# Generated by tooling/palette.mjs -- do not edit.",
    "#",
    "# The six brand colours, then colours safe on both the light and the dark",
    "# page. Use the second set for anything in a diagram: one SVG is served to",
    "# both themes, and no brand colour clears 3:1 on both backgrounds.",
    "#",
    gplRow(INK, "ink"),
    gplRow(CREAM, "cream"),
    gplRow(ORANGE, "orange"),
    gplRow(AMBER, "amber"),
    gplRow(SALMON, "salmon"),
    gplRow(TEAL, "teal"),
    "#",
    ...diagram.map((d) => gplRow(d.hex, `${d.label} (${d.onCream.toFixed(1)} / ${d.onInk.toFixed(1)})`)),
    "",
].join("\n");

writeFileSync(new URL("../qlive.gpl", import.meta.url), gpl);


console.log("wrote src/styles/qlive.css and qlive.gpl");
console.log("\nboth-theme diagram colours:");
for (const d of diagram) console.log(`  ${d.label.padEnd(15)} ${d.hex}  cream ${d.onCream.toFixed(2)}  ink ${d.onInk.toFixed(2)}`);
console.log("\nshades (into ink), for text on the cream:");
for (const [n, v] of Object.entries(onCream)) console.log(`  ${n.padEnd(7)} ${v.hex}  +${v.pct}%  ${v.ratio.toFixed(2)}`);
console.log("tints (into cream), for text on the ink:");
for (const [n, v] of Object.entries(onInk)) console.log(`  ${n.padEnd(7)} ${v.hex}  +${v.pct}%  ${v.ratio.toFixed(2)}`);
console.log("\nneutral ramp dark :", darkGrays.join(" "));
console.log("neutral ramp light:", lightGrays.join(" "));
console.log("\nneutral ramp contrast (gray-1..6):");
console.log("  on ink  :", darkGrays.map((g) => contrast(g, INK).toFixed(2).padStart(5)).join(" "));
console.log("  on cream:", lightGrays.map((g) => contrast(g, CREAM).toFixed(2).padStart(5)).join(" "));
console.log("  AA body text needs 4.5; gray-1/2 carry text, 5/6 are surfaces.");
