package io.github.qlivedev.graphql.logic;

import com.esotericsoftware.reflectasm.MethodAccess;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.TypeContext;
import io.github.qlivedev.graphql.param.ParameterProvider;
import graphql.schema.GraphQLOutputType;

import java.util.List;

/**
 * Internal configuration for a mutation type.
 */
public class Mutation
    extends DomainQLMethod
{
    public Mutation(
        QLiveDomain domainQL,
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
        super(domainQL, name, description, logicBean, methodAccess, methodIndex, parameterProviders, resultType, typeParam, genericMethodName);

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
