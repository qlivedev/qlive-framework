import {Injection, InjectionSource} from "./config";
import {convertResultFromServer} from "./converter";

let injectedData: { [key: string]: Injection }

function convertInjection(value: InjectionSource) : Injection
{

    const { data, type, meta, conversion } = value;

    return {
        value: convertResultFromServer(data, conversion),
        type,
        meta,
        conversion
    }
}

export function initData(data : { [key: string]: InjectionSource })
{
    const convertedInjections : { [key: string]: Injection } = {}
    if (data)
    {
        for (let key in data)
        {
            if (data.hasOwnProperty(key))
            {
                const value = data[key];
                convertedInjections[key] = convertInjection(value);
            }
        }
    }

    injectedData = convertedInjections
}

export default function data(injectionId : string): Injection {
    if (!injectedData)
    {
        throw new Error("Injected data not initialized")
    }

    return injectedData[injectionId]
}
