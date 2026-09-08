/*
 * The filter DSL as a flat set of names, imported from "@quinscape/qlive-ts/filter".
 *
 * The main entry exports the same module as the FilterDSL namespace, because field(), value() and and()
 * are short, common names that collide in an application's import list. That is the safe default and not
 * always the convenient one: a module that does nothing but build conditions would rather say field()
 * than FilterDSL.field(), and this entry lets it, at the price of naming the collisions itself.
 *
 * Both routes reach the same module, so there is one condition prototype chain and not two.
 */
export * from "./FilterDSL";
