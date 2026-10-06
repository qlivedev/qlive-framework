package io.github.qlivedev.graphql;

import io.github.qlivedev.graphql.annotation.GraphQLScalar;
import org.jooq.Record;
import org.jooq.Table;
import org.svenson.info.JSONPropertyInfo;
import org.svenson.info.JavaObjectPropertyInfo;

/**
 * What the assembly needs to know about the Java classes it turns into GraphQL types.
 * <p>
 * A domain type is a POJO -- the jOOQ generator writes one per table beside the {@code Table} and
 * {@code Record} classes of the same name, and the schema is built from the POJO. These are the rules for
 * telling one from its neighbours and for deciding which of its properties become fields.
 */
public final class PojoTypes
{
    private PojoTypes()
    {
        // no instances
    }


    /**
     * Finds the POJO the jOOQ generator wrote for the given table, which is the class the schema exposes the
     * table's rows as.
     *
     * @param table     jOOQ table
     *
     * @return POJO class
     */
    public static Class<?> findPojoTypeOf(Table<?> table)
    {
        try
        {
            final String typeName = table.getClass().getSimpleName();
            return Class.forName(
                // following jooq code generator conventions
                table.getClass().getPackage().getName() + ".pojos." + typeName
            );
        }
        catch (ClassNotFoundException e)
        {
            throw new QLiveDomainException(e);
        }
    }


    /**
     * Checks a class the application hands in as a domain type. The jOOQ generator writes a {@code Table}, a
     * {@code Record} and a POJO of the same simple name, so importing the wrong one is an easy mistake whose
     * consequences otherwise show up far from where it was made. A class annotated as a
     * {@link GraphQLScalar} is no object type either.
     * <p>
     * Every API that accepts a POJO class from the application calls this where it accepts it.
     *
     * @param cls   class given as a domain type
     *
     * @return the class
     *
     * @throws QLiveDomainTypeException if the class is a jOOQ {@code Table} or {@code Record}, or a scalar
     */
    public static Class<?> ensurePojoType(Class<?> cls)
    {
        if (!isPojoType(cls))
        {
            throw new QLiveDomainTypeException(cls.getName() + " is not a simple POJO class. Have you referenced the " +
                "wrong class?");
        }

        final GraphQLScalar annotation = cls.getAnnotation(GraphQLScalar.class);
        if (annotation != null)
        {
            throw new QLiveDomainTypeException(cls.getName() + " must be declared as scalar (See QLiveDomainBuilder" +
                ".withAdditionalScalar)");
        }

        return cls;
    }


    private static boolean isPojoType(Class<?> cls)
    {
        return !Table.class.isAssignableFrom(cls) && !Record.class.isAssignableFrom(cls);
    }


    /**
     * Whether the given property becomes a field of the type it belongs to. Read-only properties, the
     * {@code Class} every POJO exposes, properties with no getter and those marked to be ignored do not.
     *
     * @param info      property
     *
     * @return true if the property is exposed as a field
     */
    public static boolean isNormalProperty(JSONPropertyInfo info)
    {
        return !info.isReadOnly() && !Class.class.isAssignableFrom(info.getType()) && ((JavaObjectPropertyInfo) info).getGetterMethod() != null && !info
            .isIgnore();
    }
}
