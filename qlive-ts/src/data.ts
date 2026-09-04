import {Injection, InjectionSource} from "./config";

let injectedData: { [key: string]: Injection }

/**
 * Turns one injection as it came over the wire into the record the application reads
 * from. The data is not converted here: the conversion needs the selections of the
 * query the injection was produced from, and that query is only known once a view
 * actually calls inject() with it.
 */
function toInjection(value: InjectionSource) : Injection
{
    const { data, type, meta } = value;

    return {
        value: data,
        type,
        meta
    }
}

export function initData(data : { [key: string]: InjectionSource })
{
    const injections : { [key: string]: Injection } = {}
    if (data)
    {
        for (let key in data)
        {
            if (data.hasOwnProperty(key))
            {
                const value = data[key];
                injections[key] = toInjection(value);
            }
        }
    }

    injectedData = injections
}

/**
 * Returns the injection the server shipped under the given id, with the type and
 * meta information it came with.
 *
 * The value is in whatever state it was last left in -- raw as received until an
 * inject() call for the same id converts it in place. inject() is the normal way
 * to read injected data; this is for the cases that need the type or the meta
 * alongside the value, or the id of an injection no view claimed.
 *
 * @param injectionId   injection id, normally the query name
 */
export default function data(injectionId : string): Injection {
    if (!injectedData)
    {
        throw new Error("Injected data not initialized")
    }

    return injectedData[injectionId]
}
