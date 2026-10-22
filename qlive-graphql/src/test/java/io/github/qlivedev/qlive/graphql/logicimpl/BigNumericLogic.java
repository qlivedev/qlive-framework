package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.GraphQLLogic;
import io.github.qlivedev.qlive.api.annotation.GraphQLQuery;
import io.github.qlivedev.qlive.graphql.beans.BDContainer;
import io.github.qlivedev.qlive.graphql.beans.BIContainer;

import java.math.BigDecimal;
import java.math.BigInteger;

@GraphQLLogic
public class BigNumericLogic
{
    @GraphQLQuery
    public BDContainer bigDecimalQuery(BDContainer in)
    {
        in.setValue(
            in.getValue().add(
                new BigDecimal("1.23")
            )
        );
        return in;
    }


    @GraphQLQuery
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
