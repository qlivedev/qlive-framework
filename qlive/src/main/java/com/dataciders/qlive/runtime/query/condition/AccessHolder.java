package com.dataciders.qlive.runtime.query.condition;

import com.esotericsoftware.reflectasm.MethodAccess;
import com.dataciders.qlive.runtime.QLiveException;
import org.jooq.Field;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Collection;

public class AccessHolder
{

    private final static MethodAccess fieldAccess = MethodAccess.get(Field.class);

    private final String name;

    private final int numArgs;

    private volatile Integer methodIndex;


    public AccessHolder(String name, int numArgs)
    {

        this.name = name;
        this.numArgs = numArgs;
    }


    public Object invoke(Field field, Object... operands)
    {
        if (methodIndex == null)
        {
            synchronized (this)
            {
                if (methodIndex == null)
                {
                    methodIndex = findMethodIndex();
                }
            }
        }
        return fieldAccess.invoke(field, methodIndex, operands);
    }


    private int findMethodIndex()
    {
        for (Method method : Field.class.getMethods())
        {
            if (method.getName().equals(name))
            {
                final Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length == numArgs && Arrays.stream(parameterTypes)
                    .allMatch(t -> Field.class.isAssignableFrom(t) || t.equals(Collection.class)))
                {
                    return fieldAccess.getIndex(name, method.getParameterTypes());
                }
            }
        }
        throw new QLiveException(
            "Could not find method with name '" + name + "' and " + numArgs + " Field " +"parameters"
        );
    }
}
