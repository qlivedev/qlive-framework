package com.dataciders.qlive.runtime.scalar;

import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.schema.DomainQLAware;
import com.dataciders.qlive.model.condition.CNode;
import com.dataciders.qlive.model.condition.Component;
import com.dataciders.qlive.model.condition.Condition;
import com.dataciders.qlive.model.condition.Field;
import com.dataciders.qlive.model.condition.Operation;
import com.dataciders.qlive.model.condition.Value;
import com.dataciders.qlive.model.condition.Values;
import com.dataciders.qlive.runtime.QLiveException;
import de.quinscape.spring.jsview.util.JSONUtil;
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
    implements graphql.schema.Coercing<CNode, Map<String, Object>>, DomainQLAware
{


    private final static Logger log = LoggerFactory.getLogger(ConditionCoercing.class);

    private DomainQL domainQL;


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
                final Object converted;
                converted = graphQLType.getCoercing().parseValue(value, graphQLContext, locale);

                serialized.add(converted);
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
                return FilterDSL.value(input.get("value"), (String) input.get("scalarType"));
            case "Values":
                return FilterDSL.values((List<Object>) input.get("values"), (String) input.get("scalarType"));
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


    protected GraphQLScalarType getScalarType(String scalarTypeName)
    {
        final GraphQLType type = domainQL.getGraphQLSchema().getType(scalarTypeName);

        if (!(type instanceof GraphQLScalarType))
        {
            throw new IllegalStateException("Type '" + scalarTypeName + "' is not a scalar type: " + type);
        }
        return (GraphQLScalarType) type;
    }


    @Override
    public void setDomainQL(DomainQL domainQL)
    {
        this.domainQL = domainQL;
    }
}

