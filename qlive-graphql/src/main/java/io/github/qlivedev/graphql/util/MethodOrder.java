package io.github.qlivedev.graphql.util;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Comparator;

/// The public methods of a class in a stable order.
///
/// `Class.getMethods()` lists them in an order the JVM leaves unspecified, and which differs from one start of
/// the application to the next. The schema takes queries, mutations and fields with arguments from there, so
/// read straight from it they come out reordered in every introspection result, recorded fixture and generated
/// file after a restart.
public final class MethodOrder
{
    private final static Comparator<Method> BY_SIGNATURE = Comparator
        .comparing(Method::getName)
        .thenComparing(m -> Arrays.toString(m.getParameterTypes()))
        .thenComparing(m -> m.getDeclaringClass().getName());

    private MethodOrder()
    {
        // no instances
    }


    /// The public methods of the given class, as `getMethods()` returns them, sorted by name, then by parameter
    /// types, then by declaring class.
    ///
    /// @param cls  class
    public static Method[] publicMethods(Class<?> cls)
    {
        final Method[] methods = cls.getMethods();
        Arrays.sort(methods, BY_SIGNATURE);
        return methods;
    }
}
