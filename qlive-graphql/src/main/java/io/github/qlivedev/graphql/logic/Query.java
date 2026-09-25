package io.github.qlivedev.graphql.logic;

import com.esotericsoftware.reflectasm.MethodAccess;
import io.github.qlivedev.graphql.QLiveDomain;

import java.util.function.Supplier;
import io.github.qlivedev.graphql.TypeContext;
import io.github.qlivedev.graphql.param.ParameterProvider;
import graphql.schema.GraphQLOutputType;

import java.util.List;

/**
 * Internal configuration for a query type.
 */
public class Query
    extends QLiveDomainMethod
{
    public Query(
        Supplier<QLiveDomain> domain,
        String name,
        String description,
        Object logicBean,
        MethodAccess methodAccess,
        int methodIndex,
        List<ParameterProvider> parameterProviders,
        GraphQLOutputType resultType,
        TypeContext typeParam,
        String genericMethodName
    )
    {
        super(domain, name, description, logicBean, methodAccess, methodIndex, parameterProviders, resultType, typeParam,genericMethodName);
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "name = '" + name + '\''
            + ", description = '" + description + '\''
            + ", parameterProviders = " + parameterProviders
            ;
    }
}
