/*
 * The one place QLive takes Temporal from. Every module naming it imports it from here, and so does index.ts,
 * which makes it one binding in the bundled declarations: a re-export of the package next to modules importing
 * the package directly leaves the declaration bundler two, one of them renamed to Temporal$1 in what an
 * application's editor shows.
 */
export {Temporal} from "temporal-polyfill";
