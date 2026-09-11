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

/// Writes what an application declares per type about querying it -- the query config delta and the maximum
/// page size -- i.e. the declaring end of {@link QueryConfigMeta}.
///
/// Opt-in and empty by default: an application that wants any of this registers this as a MetadataProvider
/// bean and says which types it declares what for, and one that does not registers nothing and gets the
/// behavior it had before there was any of this.
///
///     @Bean
///     public MetadataProvider queryConfigMetadata()
///     {
///         return QueryConfigMetadataProvider.newProvider()
///             .forType(Foo.class, QueryConfigDelta.newDelta().pageSize(20).sortFields("name"))
///             .forType(Bar.class, QueryConfigDelta.newDelta().pageSize(50))
///             .maxPageSize(Foo.class, 100);
///     }
///
/// The two are separate statements about the same type, which is why they are separate calls: the delta says
/// where a query starts and anything may move it from there, the maximum says how far it can be moved.
///
/// Nothing keeps an application from writing {@link QueryConfigMeta#QUERY_CONFIG} or
/// {@link QueryConfigMeta#MAX_PAGE_SIZE} from a provider of its own -- this is the convenient way to say it,
/// not the only one. What reads them does not care who wrote them.
public class QueryConfigMetadataProvider
    implements MetadataProvider
{
    private final static Logger log = LoggerFactory.getLogger(QueryConfigMetadataProvider.class);

    /// Deltas declared by GraphQL type name, in declaration order.
    private final Map<String, QueryConfigDelta> byTypeName = new LinkedHashMap<>();

    /// Deltas declared by Java type, whose GraphQL name only the built domain knows.
    private final Map<Class<?>, QueryConfigDelta> byJavaType = new LinkedHashMap<>();

    /// Maximum page sizes declared by GraphQL type name, in declaration order.
    private final Map<String, Integer> maxByTypeName = new LinkedHashMap<>();

    /// Maximum page sizes declared by Java type, whose GraphQL name only the built domain knows.
    private final Map<Class<?>, Integer> maxByJavaType = new LinkedHashMap<>();


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


    /// Declares the maximum page size of the type DomainQL exposes the given Java type as, i.e. the largest
    /// page any query of those rows comes back with, whoever asks. A config asking for more -- or for all of
    /// them, which is what a page size of 0 asks for -- is held to this and says so in the config it returns.
    ///
    /// @param maxPageSize  largest allowed page, greater than 0
    public QueryConfigMetadataProvider maxPageSize(Class<?> javaType, int maxPageSize)
    {
        if (maxByJavaType.put(javaType, validMax(maxPageSize, javaType)) != null)
        {
            throw new QLiveException("Maximum page size declared twice for " + javaType);
        }
        return this;
    }


    /// Declares the maximum page size of the type of the given GraphQL name, for the types an application
    /// has no class at hand for.
    ///
    /// @param maxPageSize  largest allowed page, greater than 0
    public QueryConfigMetadataProvider maxPageSize(String typeName, int maxPageSize)
    {
        if (maxByTypeName.put(typeName, validMax(maxPageSize, typeName)) != null)
        {
            throw new QLiveException("Maximum page size declared twice for type '" + typeName + "'");
        }
        return this;
    }


    /// The given maximum, if it is one. A maximum of 0 is the one number that cannot be meant: it is how a
    /// query config asks for every row, so declaring it would read as "at most all of them", which is what
    /// declaring nothing already says.
    private static int validMax(int maxPageSize, Object type)
    {
        if (maxPageSize <= 0)
        {
            throw new QLiveException(
                "Maximum page size of " + maxPageSize + " declared for " + type + ". A maximum page size " +
                    "limits a page, so it has to be greater than 0 -- declare none to leave queries " +
                    "unlimited."
            );
        }
        return maxPageSize;
    }


    @Override
    public void provideMetaData(DomainQL domainQL, DomainQLMeta meta)
    {
        for (Map.Entry<Class<?>, QueryConfigDelta> declared : byJavaType.entrySet())
        {
            write(domainQL, meta, typeNameOf(domainQL, declared.getKey()), declared.getValue());
        }

        for (Map.Entry<String, QueryConfigDelta> declared : byTypeName.entrySet())
        {
            write(domainQL, meta, declared.getKey(), declared.getValue());
        }

        for (Map.Entry<Class<?>, Integer> declared : maxByJavaType.entrySet())
        {
            writeMax(domainQL, meta, typeNameOf(domainQL, declared.getKey()), declared.getValue());
        }

        for (Map.Entry<String, Integer> declared : maxByTypeName.entrySet())
        {
            writeMax(domainQL, meta, declared.getKey(), declared.getValue());
        }
    }


    /// The name the domain exposes the given Java type as.
    private static String typeNameOf(DomainQL domainQL, Class<?> javaType)
    {
        final OutputType outputType = domainQL.getTypeRegistry().lookup(javaType);
        if (outputType == null)
        {
            throw new QLiveException(
                "Query config meta data declared for " + javaType + ", which the domain does not " +
                    "expose as a type. Only a type that is in the schema can carry meta data."
            );
        }

        return outputType.getName();
    }


    private static void write(DomainQL domainQL, DomainQLMeta meta, String typeName, QueryConfigDelta delta)
    {
        requireType(domainQL, typeName);

        final Map<String, Object> written = delta.toMeta(domainQL);

        log.debug("Query config meta data of type {}: {}", typeName, written);

        meta.getTypeMeta(typeName).setMeta(QueryConfigMeta.QUERY_CONFIG, written);
    }


    private static void writeMax(DomainQL domainQL, DomainQLMeta meta, String typeName, int maxPageSize)
    {
        requireType(domainQL, typeName);

        log.debug("Maximum page size of type {}: {}", typeName, maxPageSize);

        meta.getTypeMeta(typeName).setMeta(QueryConfigMeta.MAX_PAGE_SIZE, maxPageSize);
    }


    private static void requireType(DomainQL domainQL, String typeName)
    {
        if (domainQL.getTypeRegistry().lookup(typeName) == null)
        {
            // The type meta data only exists for the types DomainQL knows a Java type for, so this would
            // otherwise be meta data written nowhere -- or, for a name that is no type at all, a failure
            // phrased as DomainQL's rather than as the application's.
            throw new QLiveException(
                "Query config meta data declared for type '" + typeName + "', which is no type of the domain."
            );
        }
    }
}
