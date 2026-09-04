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

export default function data(injectionId : string): Injection {
    if (!injectedData)
    {
        throw new Error("Injected data not initialized")
    }

    return injectedData[injectionId]
}
