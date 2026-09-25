package io.github.qlivedev.runtime.scalar;

import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.QLiveDomainAware;
import io.github.qlivedev.model.condition.CNode;
import io.github.qlivedev.model.condition.Component;
import io.github.qlivedev.model.condition.Condition;
import io.github.qlivedev.model.condition.Field;
import io.github.qlivedev.model.condition.Operation;
import io.github.qlivedev.model.condition.Value;
import io.github.qlivedev.model.condition.Values;
import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.util.JSONUtil;
import graphql.GraphQLContext;
import graphql.execution.CoercedVariables;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.svenson.JSON;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConditionCoercing
    implements graphql.schema.Coercing<CNode, Map<String, Object>>, QLiveDomainAware
{


    private final static Logger log = LoggerFactory.getLogger(ConditionCoercing.class);

    private QLiveDomain domain;


    public ConditionCoercing()
    {

    }


    @Override
    public Map<String, Object> serialize(@NonNull Object result, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingSerializeException
    {
        if (!(result instanceof CNode))
        {
            throw new IllegalArgumentException(result + " is not a ConditionScalar");
        }

        try
        {
            return serializeCNode(
                ((CNode) result),
                graphQLContext,
                locale
            );
        }
        catch(RuntimeException e)
        {
            throw new CoercingParseValueException(e);
        }
    }

    @Override
    public @Nullable CNode parseValue(@NonNull Object input, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingParseValueException
    {
        if (!(input instanceof Map))
        {
            throw new CoercingParseValueException(
                "Cannot coerce " + input + " to ConditionScalar, must be nested map structure (See " +
                FilterDSL.class.getName() +
                ")"
            );
        }

        try
        {
            final CNode node = parseCNode(
                (Map<String, Object>) input,
                graphQLContext,
                locale
            );

            return node;
        }
        catch(RuntimeException e)
        {
            throw new CoercingParseValueException(e);
        }
    }


    private Map<String, Object> serializeCNode(CNode input,
        @NonNull GraphQLContext graphQLContext,
        @NonNull Locale locale
    )
    {
        if (input == null)
        {
            return null;
        }
        
        final String type = input.getType();
        final JSON gen = JSONUtil.DEFAULT_GENERATOR;
        if (type == null)
        {
            throw new IllegalStateException("Condition node has no type: " + gen.forValue(input));
        }
        Map<String,Object> output;

        if (input instanceof Field field)
        {
            output = new HashMap<>();
            output.put("name", field.getName());
        }
        else  if (input instanceof Value v)
        {
            final String scalarTypeName = v.getScalarType();
            final GraphQLScalarType graphQLType = getScalarType(scalarTypeName);

            output = new HashMap<>();
            final Object serialized = graphQLType.getCoercing().serialize(v.getValue(), graphQLContext, locale);
            output.put("value", serialized);
            output.put("scalarType", scalarTypeName);
        }
        else  if (input instanceof Values v)
        {

            final String scalarTypeName = v.getScalarType();
            final Collection<?> values = v.getValues();
            final GraphQLScalarType graphQLType = getScalarType(scalarTypeName);

            List<Object> serialized = new ArrayList<>(values.size());

            for (Object value : values)
            {
                serialized.add(
                    graphQLType.getCoercing().serialize(value, graphQLContext, locale)
                );
            }

            output = new HashMap<>();
            output.put("values", serialized);
            output.put("scalarType", scalarTypeName);
        }
        else  if (input instanceof Condition condition)
        {
            List<Map<String, Object>> operands = serializeOperands(condition.getOperands(), graphQLContext, locale);
            output = new HashMap<>();
            output.put("name", condition.getName());
            output.put("operands", operands);
        }
        else  if (input instanceof Component component)
        {
            final String id = component.getId();

            Map<String, Object> serialized = serializeCNode(component.getCondition(), graphQLContext, locale);
            output = new HashMap<>();
            output.put("id", id);
            output.put("condition", serialized);
        }
        else  if (input instanceof Operation operation)
        {
            List<Map<String, Object>> operands = serializeOperands(operation.getOperands(), graphQLContext, locale);
            output = new HashMap<>();
            output.put("name", operation.getName());
            output.put("operands", operands);
        }
        else
        {
            throw new QLiveException("Invalid node type: " + type);
        }

        // what parseCNode reads back to know which node it is looking at. Without it the config a query
        // document returns cannot be sent back in, which is exactly what the client's update() does with it
        output.put("type", type);

        if (log.isDebugEnabled())
        {
            log.debug("Serialized {} to {}", gen.dumpObjectFormatted(input), gen.dumpObjectFormatted(output));
        }
        return output;
    }


    private List<Map<String, Object>> serializeOperands(List<CNode> list, GraphQLContext graphQLContext, Locale locale)
    {
        List<Map<String, Object>> operands;
        if (list != null)
        {
            operands = new ArrayList<>(list.size());
            for (CNode operand : list)
            {
                operands.add(
                    serializeCNode(operand, graphQLContext, locale)
                );
            }
        }
        else
        {
            operands = null;
        }
        return operands;
    }


    private CNode parseCNode(Map<String,Object> input,
        @NonNull GraphQLContext graphQLContext,
        @NonNull Locale locale
    )
    {
        if (input == null)
        {
            return null;
        }

        final String type = (String) input.get("type");
        final JSON gen = JSONUtil.DEFAULT_GENERATOR;
        if (type == null)
        {
            throw new IllegalStateException("Condition node has no type: " + gen.forValue(input));
        }
        CNode output;

        switch (type)
        {
            case "Condition":
            {

                Condition condition = new Condition();
                condition.setName((String) input.get("name"));
                final List<Map<String, Object>> operands = (List<Map<String, Object>>) input.get("operands");

                final List<CNode> parsed = parseOperands(operands, graphQLContext, locale);
                condition.setOperands(parsed);
                output = condition;
                break;
            }
            case "Operation":
            {
                Operation operation = new Operation();
                operation.setName((String) input.get("name"));
                final List<Map<String, Object>> operands = (List<Map<String, Object>>) input.get("operands");

                final List<CNode> parsed = parseOperands(operands, graphQLContext, locale);
                operation.setOperands(parsed);
                output = operation;
                break;
            }

            case "Field":
                return FilterDSL.field((String) input.get("name"));
            case "Value":
            {
                final String scalarTypeName = (String) input.get("scalarType");
                return FilterDSL.value(
                    parseScalar(scalarTypeName, input.get("value"), graphQLContext, locale),
                    scalarTypeName
                );
            }
            case "Values":
            {
                final String scalarTypeName = (String) input.get("scalarType");

                final List<Object> values = (List<Object>) input.get("values");
                final List<Object> parsed;
                if (values == null)
                {
                    parsed = null;
                }
                else
                {
                    parsed = new ArrayList<>(values.size());
                    for (Object value : values)
                    {
                        parsed.add(parseScalar(scalarTypeName, value, graphQLContext, locale));
                    }
                }

                return FilterDSL.values(parsed, scalarTypeName);
            }
            case "Component":
                CNode parsed = parseCNode((Map<String,Object>) input.get("condition"), graphQLContext, locale);
                return FilterDSL.component((String) input.get("id"), parsed);
            default:
                throw new QLiveException("Invalid node type: " + type);
        }


        if (log.isDebugEnabled())
        {
            log.debug("Parsed {} to {}", gen.dumpObjectFormatted(input), gen.dumpObjectFormatted(output));
        }
        return output;
    }


    /// Re-reads every value of an already-built condition through the scalar that owns it.
    ///
    /// {@link #parseValue} does this on the way in, because a condition reaching GraphQL is a nested map
    /// and building the nodes is the same pass as reading the values. A condition that did not come through
    /// GraphQL has no such pass: a `Subscribe` frame is parsed by Svenson, which builds the node classes
    /// faithfully and leaves what is inside them as whatever JSON had -- a timestamp is a `String` and a
    /// `BigDecimal` may be one too. Compiling that against a Java payload then compares a string to an
    /// instant.
    ///
    /// A new tree rather than the one it was given: a node off the wire belongs to the message it arrived
    /// in, and a caller holding that message afterwards should find it as it came.
    ///
    /// @param node             condition to coerce, or null
    /// @param graphQLContext   context the scalars' coercings are given
    /// @param locale           locale the scalars' coercings are given
    ///
    /// @return the condition with every value read as the type its node names, or null for null
    public CNode coerceValues(CNode node, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale)
    {
        if (node == null)
        {
            return null;
        }

        return switch (node)
        {
            case Field field -> FilterDSL.field(field.getName());

            case Value v -> FilterDSL.value(
                parseScalar(v.getScalarType(), v.getValue(), graphQLContext, locale),
                v.getScalarType()
            );

            case Values v ->
            {
                final Collection<?> values = v.getValues();

                if (values == null)
                {
                    yield FilterDSL.values(null, v.getScalarType());
                }

                final List<Object> parsed = new ArrayList<>(values.size());

                for (Object value : values)
                {
                    parsed.add(parseScalar(v.getScalarType(), value, graphQLContext, locale));
                }

                yield FilterDSL.values(parsed, v.getScalarType());
            }

            case Component component -> FilterDSL.component(
                component.getId(),
                coerceValues(component.getCondition(), graphQLContext, locale)
            );

            case Condition condition ->
            {
                final Condition coerced = new Condition();
                coerced.setName(condition.getName());
                coerced.setOperands(coerceOperands(condition.getOperands(), graphQLContext, locale));
                yield coerced;
            }

            case Operation operation ->
            {
                final Operation coerced = new Operation();
                coerced.setName(operation.getName());
                coerced.setOperands(coerceOperands(operation.getOperands(), graphQLContext, locale));
                yield coerced;
            }

            default -> throw new QLiveException("Invalid node type: " + node.getType());
        };
    }


    private List<CNode> coerceOperands(List<CNode> operands, GraphQLContext graphQLContext, Locale locale)
    {
        if (operands == null)
        {
            return null;
        }

        final List<CNode> coerced = new ArrayList<>(operands.size());

        for (CNode operand : operands)
        {
            coerced.add(coerceValues(operand, graphQLContext, locale));
        }

        return coerced;
    }


    private List<CNode> parseOperands(List<Map<String,Object>> list, GraphQLContext graphQLContext, Locale locale)
    {
        List<CNode> output;
        if (list != null)
        {
            output = new ArrayList<>(list.size());
            for (Map<String,Object> operand : list)
            {
                output.add(
                    parseCNode(operand, graphQLContext, locale)
                );
            }
        }
        else
        {
            output = null;
        }
        return output;
    }



    @Override
    public CNode parseLiteral(graphql.language.Value input, @NonNull CoercedVariables variables, @NonNull GraphQLContext graphQLContext, @NonNull Locale locale) throws CoercingParseLiteralException
    {
        // XXX: is this possible?
        throw new CoercingParseLiteralException("Cannot coerce ConditionScalar from literal");
    }


    /// Converts one embedded value with the coercing QLiveDomain has registered for the scalar type the node
    /// names.
    ///
    /// A condition arrives as JSON, where a timestamp is a string and a BigDecimal may be one too. Reading
    /// those is the business of the scalar that owns them, and being QLiveDomainAware is what lets this ask.
    /// Every value in the hierarchy goes through here, however deeply the parse recursed to reach it, so
    /// what comes out of a parse is a condition whose values are Java objects throughout.
    private Object parseScalar(
        String scalarTypeName,
        Object value,
        @NonNull GraphQLContext graphQLContext,
        @NonNull Locale locale
    )
    {
        if (value == null)
        {
            return null;
        }

        if (scalarTypeName == null)
        {
            throw new QLiveException("Condition value " + value + " has no scalar type");
        }

        return getScalarType(scalarTypeName).getCoercing().parseValue(value, graphQLContext, locale);
    }


    protected GraphQLScalarType getScalarType(String scalarTypeName)
    {
        if (domain == null)
        {
            throw new IllegalStateException(
                "No QLiveDomain set on this " + getClass().getSimpleName() + ". It is QLiveDomainAware, which " +
                    "means it has to be the instance registered for its scalar, or be given the QLiveDomain " +
                    "by whoever holds it."
            );
        }

        final GraphQLType type = domain.getGraphQLSchema().getType(scalarTypeName);

        if (!(type instanceof GraphQLScalarType))
        {
            throw new IllegalStateException("Type '" + scalarTypeName + "' is not a scalar type: " + type);
        }
        return (GraphQLScalarType) type;
    }


    @Override
    public void setDomain(QLiveDomain domain)
    {
        this.domain = domain;
    }
}

