package com.dataciders.qlive.runtime.query;

import org.jspecify.annotations.NonNull;

public record QueryField(String tableAlias, String fieldName)
{

    @Override
    public @NonNull String toString()
    {
        return tableAlias + "." + fieldName;
    }
}
