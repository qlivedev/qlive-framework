package com.dataciders.qlive.runtime.meta;

import com.dataciders.qlive.runtime.QLiveException;
import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.OutputType;
import de.quinscape.domainql.meta.DomainQLMeta;
import de.quinscape.domainql.meta.MetadataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.Map;

/// Writes the query config deltas an application declares per type, i.e. the declaring end of
/// {@link QueryConfigMeta}.
///
/// Opt-in and empty by default: an application that wants type-level query config defaults registers this as
/// a MetadataProvider bean and says which types have them, and one that does not registers nothing and gets
/// the behaviour it had before there was any of this.
///
///     @Bean
///     public MetadataProvider queryConfigMetadata()
///     {
///         return QueryConfigMetadataProvider.newProvider()
///             .forType(Foo.class, QueryConfigDelta.newDelta().pageSize(20).sortFields("name"))
///             .forType(Bar.class, QueryConfigDelta.newDelta().pageSize(50));
///     }
///
/// Nothing keeps an application from writing {@link QueryConfigMeta#QUERY_CONFIG} from a provider of its own
/// -- this is the convenient way to say it, not the only one. What reads it does not care who wrote it.
public class QueryConfigMetadataProvider
    implements MetadataProvider
{
    private final static Logger log = LoggerFactory.getLogger(QueryConfigMetadataProvider.class);

    /// Deltas declared by GraphQL type name, in declaration order.
    private final Map<String, QueryConfigDelta> byTypeName = new LinkedHashMap<>();

    /// Deltas declared by Java type, whose GraphQL name only the built domain knows.
    private final Map<Class<?>, QueryConfigDelta> byJavaType = new LinkedHashMap<>();


    private QueryConfigMetadataProvider()
    {
    }


    public static QueryConfigMetadataProvider newProvider()
    {
        return new QueryConfigMetadataProvider();
    }


    /// Declares the delta of the type DomainQL exposes the given Java type as.
    ///
    /// The way to say it where the application has the class: the GraphQL name of a domain type is DomainQL's
    /// to decide, and a class that turns out not to be in the schema is reported rather than written under a
    /// name nothing reads.
    public QueryConfigMetadataProvider forType(Class<?> javaType, QueryConfigDelta delta)
    {
        if (byJavaType.put(javaType, delta) != null)
        {
            throw new QLiveException("Query config delta declared twice for " + javaType);
        }
        return this;
    }


    /// Declares the delta of the type of the given GraphQL name, for the types an application has no class
    /// at hand for.
    public QueryConfigMetadataProvider forType(String typeName, QueryConfigDelta delta)
    {
        if (byTypeName.put(typeName, delta) != null)
        {
            throw new QLiveException("Query config delta declared twice for type '" + typeName + "'");
        }
        return this;
    }


    @Override
    public void provideMetaData(DomainQL domainQL, DomainQLMeta meta)
    {
        for (Map.Entry<Class<?>, QueryConfigDelta> declared : byJavaType.entrySet())
        {
            final OutputType outputType = domainQL.getTypeRegistry().lookup(declared.getKey());
            if (outputType == null)
            {
                throw new QLiveException(
                    "Query config delta declared for " + declared.getKey() + ", which the domain does not " +
                        "expose as a type. Only a type that is in the schema can carry meta data."
                );
            }

            write(domainQL, meta, outputType.getName(), declared.getValue());
        }

        for (Map.Entry<String, QueryConfigDelta> declared : byTypeName.entrySet())
        {
            write(domainQL, meta, declared.getKey(), declared.getValue());
        }
    }


    private static void write(DomainQL domainQL, DomainQLMeta meta, String typeName, QueryConfigDelta delta)
    {
        if (domainQL.getTypeRegistry().lookup(typeName) == null)
        {
            // The type meta data only exists for the types DomainQL knows a Java type for, so this would
            // otherwise be a delta written nowhere -- or, for a name that is no type at all, a failure
            // phrased as DomainQL's rather than as the application's.
            throw new QLiveException(
                "Query config delta declared for type '" + typeName + "', which is no type of the domain."
            );
        }

        final Map<String, Object> written = delta.toMeta(domainQL);

        log.debug("Query config meta data of type {}: {}", typeName, written);

        meta.getTypeMeta(typeName).setMeta(QueryConfigMeta.QUERY_CONFIG, written);
    }
}
