package com.dataciders.qlive.runtime.util;

import com.dataciders.qlive.model.condition.CNode;
import com.dataciders.qlive.model.condition.Component;
import com.dataciders.qlive.model.condition.Field;
import com.dataciders.qlive.model.condition.FunctionNode;
import com.dataciders.qlive.model.condition.Operation;
import com.dataciders.qlive.model.condition.Value;
import com.dataciders.qlive.model.condition.Values;
import com.dataciders.qlive.runtime.scalar.ComputedValue;
import de.quinscape.spring.jsview.util.JSONUtil;

import java.util.List;
import java.util.stream.Collectors;

public class FilterDSLDecompiler
{
    private FilterDSLDecompiler()
    {

    }

    public static String decompile(CNode node)
    {
        return decompile(node, null, false, 0);
    }

    public static String decompile(CNode node, CNode match, boolean invert, int level)
    {
        if (node == null)
        {
            return indent(level) + "null";
        }

        final String markerL = match == node ? "/*>>*/ " : "";
        final String markerR = match == node ? " /*<<*/" : "";

        int nextLevel = level >= 0 ? level + 1 : level;

        if (node instanceof Value v)
        {
            Object value = v.getValue();

            if (value instanceof ComputedValue cv)
            {
                if (cv.getName().equals("now"))
                {
                    return indent(level) + markerL + "now()" + markerR;
                }
                else if (cv.getName().equals("today"))
                {
                    return indent(level) + markerL + "today()" + markerR;
                }
            }
        }

        if (node instanceof FunctionNode fn)
        {
            final String name = fn.getName();
            final List<CNode> operands = fn.getOperands();
            if (invert && !isTopLevelCondition(name))
            {
                StringBuilder sb = new StringBuilder();
                sb.append(decompile(operands.get(0), match, true, level));
                sb.append('.');
                sb.append(markerL);
                sb.append(name);
                sb.append((level>= 0 && operands.size()> 1 ? "(\n" : "("));
                sb.append(
                    operands.subList(1, operands.size())
                        .stream().map(
                            operand -> decompile(operand, match, true, nextLevel)
                        )
                        .collect(
                            Collectors.joining(level >= 0 && operands.size() > 1 ? ",\n" : ",")
                        )
                );
                sb.append(level >= 0 && operands.size() > 0 ? "\n" : "");
                sb.append(indent(level));
                sb.append(")");
                sb.append(markerR);

                return sb.toString();
            }
            StringBuilder sb = new StringBuilder();
            sb.append(indent(level));
            sb.append(markerL);
            sb.append(name);
            sb.append((level>= 0 && operands.size()> 1 ? "(\n" : "("));
            sb.append(
                operands.stream().map(
                        operand -> decompile(operand, match, false, nextLevel)
                    )
                    .collect(
                        Collectors.joining(level >= 0 && operands.size() > 1 ? ",\n" : ",")
                    )

            );
            sb.append(markerR);
            return sb.toString();
        }
        else if (node instanceof Value v)
        {
            String scalarType = v.getScalarType();
            Object value = v.getValue();
            if (value != null && hasSimplifiedValue(scalarType))
            {
                return indent(level) + markerL + "value(" + convert(value, scalarType) + ")" + markerR;
            }
            return indent(level) + markerL + "value(" + JSONUtil.DEFAULT_GENERATOR.forValue(value) + ", " + JSONUtil.DEFAULT_GENERATOR.forValue(
                scalarType) + ")" + markerR;
        }
        else if (node instanceof Values v)
        {
            String scalarType = v.getScalarType();
            Object values = v.getValues();
            return indent(level) + markerL + "value(" + JSONUtil.DEFAULT_GENERATOR.forValue(scalarType) + ", " + JSONUtil.DEFAULT_GENERATOR.forValue(
                values) + ")" + markerR;
        }
        else if (node instanceof Component component)
        {
            return indent(level) + markerL + "component(" + JSONUtil.DEFAULT_GENERATOR.forValue(component.getId()) + ", " + decompile(component.getCondition(), match, true, nextLevel) + ")" + markerR;
        }
        else if (node instanceof Field field)
        {
            return indent(level) + markerL + "field(" + JSONUtil.DEFAULT_GENERATOR.forValue(field.getName()) + ")" + markerR;
        }
        else
        {
            throw new UnsupportedOperationException("Unhandled node type: " + node);
        }
    }


    private static String convert(Object value, String scalarType)
    {
        if (scalarType.equals("Timestamp") || scalarType.equals("Date"))
        {
            return "DateTime.fromISO(" + JSONUtil.DEFAULT_GENERATOR.forValue(value) + ")";
        }
        return JSONUtil.DEFAULT_GENERATOR.forValue(value);
    }


    private static boolean hasSimplifiedValue(String scalarType)
    {
        return scalarType.equals("Boolean") || scalarType.equals("Int") || scalarType.equals("String") || scalarType.equals("Timestamp");
    }


    private static boolean isTopLevelCondition(String name)
    {
        return name.equals("or") || name.equals("and") || name.equals("not");
    }


    private static String indent(int level)
    {
        return "    ".repeat(level);
    }


    public static String getFilterExpression(CNode node)
    {
        if (node instanceof Field field)
        {
            return JSONUtil.DEFAULT_GENERATOR.forValue(field.getName());
        }
        else if (node instanceof Operation operation)
        {
            if (operation.getName().equals("desc"))
            {
                final CNode kid = operation.getOperands().get(0);
                if (kid instanceof Field kf)
                {
                    return "!" + kf.getName();
                }
            }
        }
        return decompile(node);
    }
}
