package io.github.qlivedev.graphql;

import io.github.qlivedev.graphql.annotation.GraphQLScalar;
import io.github.qlivedev.graphql.meta.DomainQLMeta;
import graphql.schema.GraphQLSchema;
import org.jooq.DSLContext;
import org.jooq.Record;
import org.jooq.Table;
import org.svenson.info.JSONPropertyInfo;
import org.svenson.info.JavaObjectPropertyInfo;

/**
 * The domain a configured {@link DomainQLBuilder} produced: the GraphQL schema, what is known about the types in
 * it, and the schema metadata.
 * <p>
 * Immutable, and holds nothing of the assembly that produced it -- see {@link SchemaAssembler} for that.
 */
public class DomainQL
    implements QLiveDomain
{
    private final GraphQLSchema graphQLSchema;

    private final TypeRegistry typeRegistry;

    private final DomainQLMeta metaData;


    DomainQL(GraphQLSchema graphQLSchema, TypeRegistry typeRegistry, DomainQLMeta metaData)
    {
        this.graphQLSchema = graphQLSchema;
        this.typeRegistry = typeRegistry;
        this.metaData = metaData;
    }


    @Override
    public GraphQLSchema getGraphQLSchema()
    {
        return graphQLSchema;
    }


    @Override
    public TypeRegistry getTypeRegistry()
    {
        return typeRegistry;
    }


    @Override
    public DomainQLMeta getMetaData()
    {
        return metaData;
    }


    /**
     * Creates a new builder to be configured. Call {@link DomainQLBuilder#build()} on the builder after
     * configuration to assemble the domain.
     *
     * @param dslContext JOOQ DSL context instance
     *
     * @return DomainQL builder
     */
    public static DomainQLBuilder newDomainQL(DSLContext dslContext)
    {
        return new DomainQLBuilder(dslContext);
    }


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
            throw new DomainQLException(e);
        }
    }


    /**
     * Tests if the user has imported the wrong of the same name accidentally by not importing the POJO class but
     * the Table or Record class.
     *
     * @param cls
     *
     * @return
     */
    static Class<?> ensurePojoType(Class<?> cls)
    {
        if (!isPojoType(cls))
        {
            throw new DomainQLTypeException(cls.getName() + " is not a simple POJO class. Have you referenced the " +
                "wrong class?");
        }

        final GraphQLScalar annotation = cls.getAnnotation(GraphQLScalar.class);
        if (annotation != null)
        {
            throw new DomainQLTypeException(cls.getName() + " must be declared as scalar (See DomainQLBuilder" +
                ".withAdditionalScalar)");
        }

        return cls;
    }


    private static boolean isPojoType(Class<?> cls)
    {
        return !Table.class.isAssignableFrom(cls) && !Record.class.isAssignableFrom(cls);
    }


    public static boolean isNormalProperty(JSONPropertyInfo info)
    {
        return !info.isReadOnly() && !Class.class.isAssignableFrom(info.getType()) && ((JavaObjectPropertyInfo) info).getGetterMethod() != null && !info
            .isIgnore();
    }
}
