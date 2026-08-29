package com.dataciders.qlive.runtime.scalar;

import com.dataciders.qlive.model.condition.CNode;
import com.dataciders.qlive.model.condition.Component;
import com.dataciders.qlive.model.condition.Condition;
import com.dataciders.qlive.model.condition.Field;
import com.dataciders.qlive.model.condition.Value;
import com.dataciders.qlive.model.condition.ValueNode;
import com.dataciders.qlive.model.condition.Values;
import com.dataciders.qlive.runtime.util.FilterDSLDecompiler;
import graphql.schema.CoercingParseValueException;

import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * DSL builder for conditions and field expressions.
 */
public class FilterDSL
{
    final static Pattern ORDER_BY_RE = Pattern.compile("^!?[a-zA-Z_][a-zA-Z0-9_]*(\\.[a-zA-Z_][a-zA-Z0-9_]*)*$");


    private FilterDSL()
    {
        // no instances
        FilterDSL.and(
            FilterDSL.field("name").plus(field("num")).eq(value("foo12"))
        );
    }


    public static Field field(String name)
    {
        return new Field(name);
    }


    public static Value value(Object v)
    {
        return value(v, getDefaultType(v));
    }


    private static String getDefaultType(Object v)
    {
        return switch (v)
        {
            case Boolean b -> "Boolean";
            case Byte b -> "Byte";
            case Integer i -> "Int";
            case Long l -> "BigInt";
            case String s -> "String";
            case Condition condition -> "Condition";
            case ValueNode valueNode -> "FilterExpression";
            case Date date -> "Date";
            case Timestamp timestamp -> "Timestamp";
            case null, default -> throw new IllegalArgumentException("Cannot determine scalar type: " + v);
        };
    }


    public static Value value(Object v, String scalarType)
    {
        final Value value = new Value();
        value.setScalarType(scalarType);
        value.setValue(v);

        return value;
    }


    public static Values values(List<Object> v, String scalarType)
    {
        final Values value = new Values();
        value.setScalarType(scalarType);
        value.setValues(v);

        return value;
    }


    public static Condition and(Condition... conditions)
    {
        return logicOp("and", conditions);
    }


    public static Condition or(Condition... conditions)
    {
        return logicOp("or", conditions);
    }


    public static Condition not(Condition condition)
    {
        final ArrayList<CNode> operands = new ArrayList<>();
        operands.add(condition);

        final Condition conditionOut = new Condition();
        conditionOut.setName("not");
        conditionOut.setOperands(operands);
        return conditionOut;
    }


    private static Condition logicOp(String name, Condition... conditions)
    {
        List<CNode> conditionList = new ArrayList<>();
        for (Condition condition : conditions)
        {
            if (condition != null)
            {
                conditionList.add(condition);
            }
        }

        if (conditionList.isEmpty())
        {
            return null;
        }
        else if (conditionList.size() == 1)
        {
            return (Condition) conditionList.get(0);
        }
        else
        {
            final Condition condition = new Condition();
            condition.setName(name);
            condition.setOperands(conditionList);
            return condition;
        }
    }


    public static CNode component(String id, CNode condition)
    {
        final Component component = new Component();
        component.setId(id);
        component.setCondition(condition);
        return component;
    }


    public static CNode fieldExpression(String fieldExpr)
    {
        if (!ORDER_BY_RE.matcher(fieldExpr).matches())
        {
            throw new CoercingParseValueException("Invalid field expression: " + fieldExpr);
        }

        if (fieldExpr.startsWith("!"))
        {
            return FilterDSL.field(
                fieldExpr.substring(1)
            ).desc();
        }
        else
        {
            return FilterDSL.field(
                fieldExpr
            );
        }
    }

    public String decompile(CNode node)
    {
        return FilterDSLDecompiler.decompile(node);
    }
}
