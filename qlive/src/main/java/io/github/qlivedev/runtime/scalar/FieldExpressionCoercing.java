package io.github.qlivedev.runtime.scalar;

import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.schema.DomainQLAware;
import io.github.qlivedev.model.condition.CNode;
import io.github.qlivedev.model.condition.Field;
import io.github.qlivedev.model.condition.Operation;
import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;
import java.util.Map;

public final class FieldExpressionCoercing
    implements Coercing<CNode, Object>, DomainQLAware
{
    private final static Logger log = LoggerFactory.getLogger(FieldExpressionCoercing.class);


    private DomainQL domainQL;


    public FieldExpressionCoercing()
    {
    }


    @Override
    public Object serialize(Object input, GraphQLContext graphQLContext, Locale locale) throws CoercingSerializeException
    {
        if (input instanceof String)
        {
            return input;
        }

        if (!(input instanceof CNode node))
        {
            throw new CoercingParseValueException(input + " is not a CNode");
        }

        if (node instanceof Field field)
        {
            return field.getName();
        }
        else if (node instanceof Operation operation)
        {
            final String name = operation.getName();

            final CNode operand = operation.getOperands().get(0);

            if (operand instanceof Field operandField)
            {
                if (name.equals("asc"))
                {
                    return operandField.getName();
                }
                else if (name.equals("desc"))
                {
                    return "!" + operandField.getName();
                }
            }

        }


        try
        {
            final GraphQLScalarType type = (GraphQLScalarType) domainQL.getGraphQLSchema().getType("Condition");
            return type.getCoercing().serialize(input, graphQLContext, locale);
        }
        catch (RuntimeException e)
        {
            throw new CoercingParseValueException(e);
        }
    }


    @Override
    public CNode parseValue(@NonNull Object input, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingParseValueException
    {
        if (input instanceof String)
        {
            return FilterDSL.fieldExpression((String) input);
        }

        if (!(input instanceof Map))
        {
            throw new CoercingParseValueException(
                "Cannot coerce " + input + " to FieldExpressionScalar, must be nested map structure (See " +
                    FilterDSL.class.getName() +
                    ") or string expression"
            );
        }

        try
        {
            final GraphQLScalarType type = (GraphQLScalarType) domainQL.getGraphQLSchema().getType("Condition");
            return (CNode) type.getCoercing().parseValue(input, graphQLContext, locale);
        }
        catch (RuntimeException e)
        {
            throw new CoercingParseValueException(e);
        }
    }


    @Override
    public CNode parseLiteral(graphql.language.Value input, @NonNull CoercedVariables variables, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingParseLiteralException
    {
        // XXX: is this possible?
        throw new CoercingParseLiteralException("Cannot coerce FieldExpressionScalar from literal");
    }


    @Override
    public void setDomainQL(DomainQL domainQL)
    {
        this.domainQL = domainQL;
    }


}

