package io.github.qlivedev.runtime.meta;

import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.runtime.util.Util;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.OutputType;
import io.github.qlivedev.graphql.meta.DomainMeta;
import io.github.qlivedev.graphql.meta.MetadataProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
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
///             .forAllTypes()
///                 .pageSize(20)
///                 .maxPageSize(500)
///             .andForType(Foo.class)
///                 .sortFields("name")
///                 .maxPageSize(100)
///             .andForTypes(Bar.class, Baz.class)
///                 .pageSize(50)
///                 .build();
///     }
///
/// Which types a statement is about is said first -- {@link #forType(Class)}, {@link #forTypes(Class[])} or
/// {@link #forAllTypes()} -- and what it says about them follows on the configurer that comes back. Chain
/// the next statement with its `andFor...` twin and close the last one with
/// {@link QueryConfigTypeConfigurer#build()}, which hands the provider back for the bean to return.
///
/// Only the row types of the query documents the domain declares can be configured; anything else is
/// reported when the domain is built rather than written under a name nothing reads.
///
/// The delta and the maximum are separate statements about the same type: the delta says where a query
/// starts and anything may move it from there, the maximum says how far it can be moved.
///
/// Nothing keeps an application from writing {@link QueryConfigMeta#QUERY_CONFIG} or
/// {@link QueryConfigMeta#MAX_PAGE_SIZE} from a provider of its own -- this is the convenient way to say it,
/// not the only one. What reads them does not care who wrote them.
public class QueryConfigMetadataProvider
    implements MetadataProvider
{
    private final static Logger log = LoggerFactory.getLogger(QueryConfigMetadataProvider.class);

    /// What every row type gets that does not declare a delta of its own, or null where nothing said
    /// {@link #forAllTypes()}.
    private QueryConfigTypeConfigurer allTypesConfigurer;

    /// The maximum declared for all types, or null where none was. A box rather than a sentinel number:
    /// every int is a value {@link #validMax} either accepts or rejects, so none of them is free to mean
    /// "unset".
    private Integer allTypesMaxPageSize;

    /// Deltas declared by Java type, whose GraphQL name only the built domain knows.
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


    /// Declares what one type says, i.e. {@link #forTypes(Class[])} for the one type it usually is.
    ///
    /// A class that turns out not to be the row type of a query document is reported when the domain is
    /// built rather than written under a name nothing reads.
    ///
    /// @return the configurer for that type
    public QueryConfigTypeConfigurer forType(Class<?> javaType)
    {
        return forTypes(javaType);
    }


    /// Declares what every row type of a query document says, which is the way round an application wants
    /// where a page size is a house rule and the types departing from it are the exception.
    ///
    /// The delta is the fallback: a type that declares one of its own keeps it whole, and this is never
    /// merged into it. The maximum is not a fallback but a ceiling, so it is applied to every type that
    /// does not name a maximum of its own -- including the types that do declare a delta.
    ///
    /// Called more than once -- including through {@link QueryConfigTypeConfigurer#andForAllTypes()} --
    /// this goes on configuring the one all-types statement rather than starting a second.
    ///
    /// @return the configurer for all types
    public QueryConfigTypeConfigurer forAllTypes()
    {
        if (allTypesConfigurer == null)
        {
            this.allTypesConfigurer = new QueryConfigTypeConfigurer(
                this,
                max -> this.allTypesMaxPageSize = validMax(max, "all types")
            );
        }

        return allTypesConfigurer;
    }


    /// Declares what a number of types say, all of them the same thing.
    ///
    /// @param javaTypes  the types, at least one
    ///
    /// @return the configurer for those types
    public QueryConfigTypeConfigurer forTypes(Class<?>... javaTypes)
    {
        if (javaTypes == null || javaTypes.length == 0)
        {
            throw new QLiveException("No types given");
        }

        final QueryConfigTypeConfigurer configurer = new QueryConfigTypeConfigurer(
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
            // Two statements about one type are two opinions about it, and the second silently winning
            // would be the kind of thing an application finds out about in a browser. Say it once, or say
            // it with forAllTypes() and depart from it per type.
            if (byJavaType.putIfAbsent(cls, configurer) != null)
            {
                throw new QLiveException("Query config declared twice for " + cls.getSimpleName());
            }
        }

        return configurer;
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
    public void provideMetaData(QLiveDomain domain, DomainMeta meta)
    {
        final Set<Class<?>> queryDocumentRowTypes = Util.getQueryDocumentRowTypes(domain);
        if (allTypesConfigurer != null)
        {
            queryDocumentRowTypes.forEach(cls -> {
                byJavaType.putIfAbsent(cls, allTypesConfigurer);
                if (allTypesMaxPageSize != null)
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
                domain,
                meta,
                typeNameOf(domain, cls),
                configurer
            );
        }

        for (Map.Entry<Class<?>, Integer> e : maxByJavaType.entrySet())
        {
            final Class<?> cls = e.getKey();
            final Integer maxPageSize = e.getValue();
            
            writeMax(
                domain,
                meta,
                typeNameOf(domain, cls),
                maxPageSize
            );
        }
    }


    /// The name the domain exposes the given Java type as.
    private static String typeNameOf(QLiveDomain domain, Class<?> javaType)
    {
        final OutputType outputType = domain.getTypeRegistry().lookup(javaType);
        if (outputType == null)
        {
            throw new QLiveException(
                "Query config metadata declared for " + javaType + ", which the domain does not " +
                    "expose as a type. Only a type that is in the schema can carry metadata."
            );
        }

        return outputType.getName();
    }


    private static void write(QLiveDomain domain, DomainMeta meta, String typeName, QueryConfigTypeConfigurer delta)
    {
        requireType(domain, typeName);

        final Map<String, Object> written = delta.toMeta(domain);

        log.debug("Query config metadata of type {}: {}", typeName, written);

        meta.getTypeMeta(typeName).setMeta(QueryConfigMeta.QUERY_CONFIG, written);
    }


    private static void writeMax(QLiveDomain domain, DomainMeta meta, String typeName, int maxPageSize)
    {
        requireType(domain, typeName);

        log.debug("Maximum page size of type {}: {}", typeName, maxPageSize);

        meta.getTypeMeta(typeName).setMeta(QueryConfigMeta.MAX_PAGE_SIZE, maxPageSize);
    }


    private static void requireType(QLiveDomain domain, String typeName)
    {
        if (domain.getTypeRegistry().lookup(typeName) == null)
        {
            // The type metadata only exists for the types QLiveDomain knows a Java type for, so this would
            // otherwise be metadata written nowhere -- or, for a name that is no type at all, a failure
            // phrased as the framework's rather than as the application's.
            throw new QLiveException(
                "Query config metadata declared for type '" + typeName + "', which is no type of the domain."
            );
        }
    }
}
