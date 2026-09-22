package io.github.qlivedev.runtime.scalar;

import io.github.qlivedev.model.QueryConfig;
import io.github.qlivedev.model.condition.CNode;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.DomainQLAware;
import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.language.Value;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class QueryConfigCoercing
    implements Coercing<QueryConfig, Map<String, Object>>, DomainQLAware
{
    final ConditionCoercing conditionCoercing = new ConditionCoercing();
    final FieldExpressionCoercing fieldExpressionCoercing = new FieldExpressionCoercing();


    /// Passed on to the coercings this one delegates to. They are instances of their own rather than the
    /// ones registered for their scalars, so nothing else hands them the QLiveDomain they need to serialize a
    /// condition -- which is what a query config carries.
    @Override
    public void setDomainQL(QLiveDomain domainQL)
    {
        conditionCoercing.setDomainQL(domainQL);
        fieldExpressionCoercing.setDomainQL(domainQL);
    }

    @Override
    public @Nullable QueryConfig parseLiteral(
        @NonNull Value<?> input,
        @NonNull CoercedVariables variables,
        @NonNull GraphQLContext graphQLContext,
        @NonNull Locale locale
    ) throws CoercingParseLiteralException
    {
        throw new CoercingParseLiteralException("Cannot parse query config literal");
    }


    @Override
    public @Nullable QueryConfig parseValue(
        @NonNull Object input,
        @NonNull GraphQLContext graphQLContext,
        @NonNull Locale locale
    ) throws CoercingParseValueException
    {
        if (input instanceof Map m)
        {
            final Object condition = m.get("condition");
            final QueryConfig queryConfig = new QueryConfig();
            queryConfig.setPageSize((Integer) m.get("pageSize"));
            queryConfig.setOffset((Integer) m.get("offset"));
            queryConfig.setCondition(
                condition == null ? null :
                conditionCoercing.parseValue(
                    condition, graphQLContext, locale
                )
            );
            List<CNode> converted = new ArrayList<>();
            final Object sortFields = m.get("sortFields");
            if (sortFields instanceof List l)
            {
                for (Object o : l)
                {
                    converted.add(
                        fieldExpressionCoercing.parseValue(o, graphQLContext, locale)
                    );
                }
            }
            queryConfig.setSortFields(
                converted
            );
            return queryConfig;
        }
        throw new CoercingParseValueException("Cannot parse query config value: " + input);
    }


    @Override
    public @Nullable Map<String, Object> serialize(
        @NonNull Object dataFetcherResult,
        @NonNull GraphQLContext graphQLContext,
        @NonNull Locale locale
    ) throws CoercingSerializeException
    {
        if (dataFetcherResult instanceof QueryConfig queryConfig)
        {
            Map<String, Object> result = new HashMap<>();

            result.put("offset", queryConfig.getOffset());
            result.put("pageSize", queryConfig.getPageSize());

            final CNode condition = queryConfig.getCondition();
            result.put("condition",
                condition == null ? null :
                conditionCoercing.serialize(
                    condition, graphQLContext, locale
                )
            );

            List<Object> serialized = new ArrayList<>();
            final List<CNode> sortFields = queryConfig.getSortFields();
            for (CNode sortField : sortFields)
            {
                serialized.add(
                    fieldExpressionCoercing.serialize(sortField, graphQLContext, locale)
                );
            }
            result.put("sortFields", serialized);

            return result;
        }

        throw new CoercingSerializeException("Could not convert " + dataFetcherResult + " to QueryConfig map");
    }
}
