package com.dataciders.qlive.runtime.meta;

import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.util.Util;
import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.OutputType;
import de.quinscape.domainql.meta.DomainQLMeta;
import de.quinscape.domainql.meta.MetadataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    /// Deltas declared by Java type, whose GraphQL name only the built domain knows.
    private QueryConfigTypeConfigurer allTypesConfigurer;

    private int allTypesMaxPageSize = -1;

    private final Map<Class<?>, QueryConfigTypeConfigurer> byJavaType = new LinkedHashMap<>();

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
    public QueryConfigTypeConfigurer forType(Class<?> javaType)
    {
        return forTypes(javaType);
    }


    /// Defines the following QueryConfigTypeConfigurer for all query document types that are not
    /// defined explicitly.
    ///
    /// @return a new query type configurer
    public QueryConfigTypeConfigurer forAllTypes()
    {
        if (allTypesConfigurer == null)
        {
            this.allTypesConfigurer = new QueryConfigTypeConfigurer(
                this,
                max -> {
                    this.allTypesMaxPageSize = max;
                }
            );
        }

        return allTypesConfigurer;
    }


    /// Configures a number of types with the same QueryConfigTypeConfigurer.
    ///
    /// @param javaTypes types
    ///
    /// @return a new query type configurer
    public QueryConfigTypeConfigurer forTypes(Class<?>... javaTypes)
    {
        if (javaTypes == null || javaTypes.length == 0)
        {
            throw new QLiveException("No types given");
        }

        final QueryConfigTypeConfigurer delta = new QueryConfigTypeConfigurer(
            this,
            maxPageSize -> {
                for (Class<?> cls : javaTypes)
                {
                    maxByJavaType.put(cls, validMax(maxPageSize, cls));
                }
            }
        );

        for (Class<?> cls : javaTypes)
        {
            byJavaType.put(cls, delta);
        }

        return delta;
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
        final Set<Class<?>> queryDocumentRowTypes = Util.getQueryDocumentRowTypes(domainQL);
        if (allTypesConfigurer != null)
        {
            queryDocumentRowTypes.forEach(cls -> {
                byJavaType.putIfAbsent(cls, allTypesConfigurer);
                if (allTypesMaxPageSize != -1)
                {
                    maxByJavaType.putIfAbsent(cls, allTypesMaxPageSize);
                }
            });
        }

        for (Map.Entry<Class<?>, QueryConfigTypeConfigurer> e : byJavaType.entrySet())
        {
            final Class<?> cls = e.getKey();
            final QueryConfigTypeConfigurer configurer = e.getValue();

            if (!queryDocumentRowTypes.contains(cls))
            {
                throw new QLiveException("Cannot configure: No query document type was declared for " + cls.getSimpleName());
            }

            write(
                domainQL,
                meta,
                typeNameOf(domainQL, cls),
                configurer
            );
        }

        for (Map.Entry<Class<?>, Integer> e : maxByJavaType.entrySet())
        {
            final Class<?> cls = e.getKey();
            final Integer maxPageSize = e.getValue();
            
            writeMax(
                domainQL,
                meta,
                typeNameOf(domainQL, cls),
                maxPageSize
            );
        }
    }


    /// The name the domain exposes the given Java type as.
    private static String typeNameOf(DomainQL domainQL, Class<?> javaType)
    {
        final OutputType outputType = domainQL.getTypeRegistry().lookup(javaType);
        if (outputType == null)
        {
            throw new QLiveException(
                "Query config metadata declared for " + javaType + ", which the domain does not " +
                    "expose as a type. Only a type that is in the schema can carry metadata."
            );
        }

        return outputType.getName();
    }


    private static void write(DomainQL domainQL, DomainQLMeta meta, String typeName, QueryConfigTypeConfigurer delta)
    {
        requireType(domainQL, typeName);

        final Map<String, Object> written = delta.toMeta(domainQL);

        log.debug("Query config metadata of type {}: {}", typeName, written);

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
            // The type metadata only exists for the types DomainQL knows a Java type for, so this would
            // otherwise be metadata written nowhere -- or, for a name that is no type at all, a failure
            // phrased as DomainQL's rather than as the application's.
            throw new QLiveException(
                "Query config metadata declared for type '" + typeName + "', which is no type of the domain."
            );
        }
    }
}
