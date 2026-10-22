package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.graphql.beans.BDContainer;
import io.github.qlivedev.qlive.graphql.beans.BIContainer;

import java.math.BigDecimal;
import java.math.BigInteger;

@QLiveLogic
public class BigNumericLogic
{
    @QLiveQuery
    public BDContainer bigDecimalQuery(BDContainer in)
    {
        in.setValue(
            in.getValue().add(
                new BigDecimal("1.23")
            )
        );
        return in;
    }


    @QLiveQuery
    public BIContainer bigIntegerQuery(BIContainer in)
    {
        in.setValue(
            in.getValue().add(
                new BigInteger("1")
            )
        );

        return in;
    }
}
