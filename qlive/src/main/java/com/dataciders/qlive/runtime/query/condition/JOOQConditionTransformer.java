package com.dataciders.qlive.runtime.query.condition;

import de.quinscape.domainql.DomainQL;
import com.dataciders.qlive.model.condition.Component;
import com.dataciders.qlive.model.condition.Condition;
import com.dataciders.qlive.model.condition.ConditionVisitor;
import com.dataciders.qlive.model.condition.Field;
import com.dataciders.qlive.model.condition.Operation;
import com.dataciders.qlive.model.condition.Value;
import com.dataciders.qlive.model.condition.Values;
import org.jooq.DSLContext;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class JOOQConditionTransformer
{
    private final DSLContext dslContext;
    private final DomainQL domainQL;

    private ConcurrentMap<String, AccessHolder> fieldAccessHolders = new ConcurrentHashMap<>();
    

    public JOOQConditionTransformer(DSLContext dslContext, DomainQL domainQL)
    {
        this.dslContext = dslContext;
        this.domainQL = domainQL;

    }

//    private Object invokeFieldMethod(
//        CachedFilterContextResolver resolver, FieldResolver fieldResolver,
//        FunctionNode condition
//    ) {
//        final String name = condition.getName();
//        final List<CNode> operands = condition.getOperands();
//
//        if (operands.isEmpty())
//        {
//            throw new QLiveException("Field condition has no operand");
//        }
//
//        final Object value = transformRecursive(
//            resolver,
//            fieldResolver,
//            operands.get(0),
//            null
//        );
//
//        // field reference is not part of this query execution, we ignore the whole condition
//        if (value == null)
//        {
//            return null;
//        }
//
//        if (!(value instanceof org.jooq.Field)) {
//            throw new QLiveException(
//                "Field Operation: First operand did not evaluate to field: " +
//                JSONUtil.DEFAULT_GENERATOR.forValue(condition)
//            );
//        }
//
//        org.jooq.Field<?> field = (org.jooq.Field<?>) value;
//
//        AccessHolder holder = new AccessHolder(name, operands.size() - 1);
//        final AccessHolder existing = fieldAccessHolders.putIfAbsent(name, holder);
//        if (existing != null)
//        {
//            holder = existing;
//        }
//        return holder.invoke(field, transformRestOfOperands(resolver, fieldResolver, operands, field));
//    }
//
//
//    private Object[] transformRestOfOperands(
//        CachedFilterContextResolver resolver,
//        FieldResolver fieldResolver,
//        List<Map<String, Object>> operands,
//        org.jooq.Field<?> conditionLeftSideField
//    )
//    {
//        Object[] array = new Object[operands.size() - 1];
//        for (int i = 1; i < operands.size(); i++)
//        {
//            array[i - 1] = transformRecursive(resolver, fieldResolver, operands.get(i), conditionLeftSideField);
//        }
//        return array;
//    }

    static class Visitor
        implements ConditionVisitor<Object>
    {
        @Override
        public Object visit(Condition condition, Object data)
        {
            return ConditionVisitor.super.visit(condition, data);
        }


        @Override
        public Object visit(Operation operation, Object data)
        {
            return ConditionVisitor.super.visit(operation, data);
        }


        @Override
        public Object visit(Component component, Object data)
        {
            return ConditionVisitor.super.visit(component, data);
        }


        @Override
        public Object visit(Field field, Object data)
        {
            return ConditionVisitor.super.visit(field, data);
        }


        @Override
        public Object visit(Value value, Object data)
        {
            return ConditionVisitor.super.visit(value, data);
        }


        @Override
        public Object visit(Values values, Object data)
        {
            return ConditionVisitor.super.visit(values, data);
        }
    }

}
