/*
 * Derived from babel-plugin-track-usage, Copyright Quinscape GmbH, used under the
 * Apache License, Version 2.0. Vendored from commit
 * 1aa8c7c451a3683390ba991e6a19e89dc1c5e5e2 and converted to ES modules. See the
 * NOTICE file at the root of this repository for the full statement of changes.
 *
 * What it offers the rest of the package is declared in trackUsageData.d.ts.
 */
var dataStore;

function flattenRequires(requires)
{
    var varName, module;
    var array = [];
    var added = {};

    for (varName in requires)
    {
        if (requires.hasOwnProperty(varName))
        {
            module = requires[varName];
            if (!added[module])
            {
                added[module] = true;
                array.push(module);
            }
        }
    }

    return array;
}

var DataStore = {
    _internal: function ()
    {
        return dataStore.usages;
    },

    clear: function ()
    {
        dataStore = {
            usages : {

            }
        };
    },
    get: function()
    {
        var usages = dataStore.usages;
        var cleaned = {};
        for (var module in usages)
        {
            if (usages.hasOwnProperty(module))
            {
                var e = usages[module];

                cleaned[module] = {
                    module: e.module,
                    requires: flattenRequires(e.requires),
                    calls: e.calls
                }

                if (e.indexes)
                {
                    cleaned[module].indexes = e.indexes;
                }
                if (e.contexts)
                {
                    cleaned[module].contexts = e.contexts;
                }

            }
        }

        return {
            usages: cleaned
        };
    }
};

DataStore.clear();

export default DataStore;

