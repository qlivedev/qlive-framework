import React from "react";

class InjectionAPI
{

    resolve(name: string): any
    {

    }
}

export const InjectionContext = React.createContext<InjectionAPI>(new InjectionAPI());
