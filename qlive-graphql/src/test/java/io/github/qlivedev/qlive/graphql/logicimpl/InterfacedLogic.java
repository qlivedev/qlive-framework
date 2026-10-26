package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;

import java.util.function.Supplier;

/// A logic bean implementing an interface, which Spring proxies through that interface unless told otherwise.
@QLiveLogic
public class InterfacedLogic
    implements Supplier<String>
{
    @QLiveQuery
    public boolean query()
    {
        return true;
    }


    @Override
    public String get()
    {
        return "interfaced";
    }
}
