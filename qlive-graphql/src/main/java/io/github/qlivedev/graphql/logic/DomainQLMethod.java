package io.github.qlivedev.graphql.logic;

import com.esotericsoftware.reflectasm.MethodAccess;
import io.github.qlivedev.graphql.QLiveDomain;

import java.util.function.Supplier;
import io.github.qlivedev.graphql.TypeContext;
import io.github.qlivedev.graphql.param.ParameterProvider;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLOutputType;

import java.util.List;

/**
 * Abstract base class for @{@link io.github.qlivedev.graphql.annotation.GraphQLLogic} methods.
 */
public abstract class DomainQLMethod
    implements DataFetcher<Object>
{
    protected final String name;

    protected final String description;

    protected final Object logicBean;

    protected final MethodAccess methodAccess;

    protected final int methodIndex;

    protected final GraphQLOutputType resultType;

    protected final List<ParameterProvider> parameterProviders;

    protected final Supplier<QLiveDomain> domainQL;

    protected final Class<?> typeParam;

    private final String genericMethodName;

    private final TypeContext typeContext;


    public DomainQLMethod(
        Supplier<QLiveDomain> domainQL,
        String name,
        String description,
        Object logicBean,
        MethodAccess methodAccess,
        int methodIndex,
        List<ParameterProvider> parameterProviders,
        GraphQLOutputType resultType,
        TypeContext typeContext,
        String genericMethodName
    )
    {
        this.domainQL = domainQL;
        this.typeContext = typeContext;
        this.typeParam = typeContext != null ? typeContext.getFirstActualType() : null;
        this.genericMethodName = genericMethodName;
        if (name == null)
        {
            throw new IllegalArgumentException("name can't be null");
        }

        if (resultType == null)
        {
            throw new IllegalArgumentException("resultType can't be null");
        }


        this.name = name;
        this.description = description;
        this.logicBean = logicBean;
        this.methodAccess = methodAccess;
        this.methodIndex = methodIndex;
        this.parameterProviders = parameterProviders;
        this.resultType = resultType;
    }


    public String getDescription()
    {
        return description;
    }


    public List<ParameterProvider> getParameterProviders()
    {
        return parameterProviders;
    }



    public String getName()
    {
        return name;
    }


    public String getGenericMethodName()
    {
        return genericMethodName;
    }


    public GraphQLOutputType getResultType()
    {
        return resultType;
    }
    
    @Override
    public Object get(DataFetchingEnvironment env)
    {

        DomainQLDataFetchingEnvironment environment = new DomainQLDataFetchingEnvironment(domainQL.get(), env, typeParam);

        final Object[] paramValues = new Object[parameterProviders.size()];

        for (int i = 0; i < parameterProviders.size(); i++)
        {
            ParameterProvider parameterProvider = parameterProviders.get(i);

            final Object value = parameterProvider.provide(environment);
            paramValues[i] = value;
        }
        return methodAccess.invoke(logicBean, methodIndex, paramValues);
    }


    public TypeContext getTypeContext()
    {
        return typeContext;
    }

}
