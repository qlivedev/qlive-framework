import { FormContext } from "@qlivedev/qlive-ts";

/**
 * Effects of the application's own: what a reaction sets a field's value to where a FilterDSL expression can't say
 * it. main.tsx registers them under the names server layouts use.
 */

/**
 * What a foo's number is, as a description: a lookup that takes its time, as one asking the server would. Of
 * several started while the user types, the latest one wins.
 */
export function describeNum({ row }: FormContext): Promise<string>
{
    const num = (row as { num: number }).num
    return new Promise(resolve => setTimeout(
        () => resolve(num + " is " + (num % 2 === 0 ? "even" : "odd") + "."),
        400
    ))
}
