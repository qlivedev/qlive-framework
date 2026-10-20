package io.github.qlivedev.runtime.meta;

import io.github.qlivedev.graphql.OutputType;
import io.github.qlivedev.graphql.PojoTypes;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.meta.DomainMeta;
import io.github.qlivedev.graphql.meta.MetadataProvider;
import io.github.qlivedev.runtime.QLiveException;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/// Declares the catalog types of an application, i.e. the declaring end of {@link CatalogMeta}.
///
///     @Bean
///     public MetadataProvider catalogMetadata()
///     {
///         return CatalogMetadataProvider.newProvider()
///             .catalog(FooType.class, Country.class);
///     }
///
/// Opt-in and empty by default: no type is a catalog unless declared one. A declared type has to be backed
/// by a table, since its rows are read from it at startup; anything else is reported when the domain is
/// built.
public class CatalogMetadataProvider
    implements MetadataProvider
{
    private final List<Class<?>> javaTypes = new ArrayList<>();

    private final List<String> typeNames = new ArrayList<>();


    private CatalogMetadataProvider()
    {
    }


    public static CatalogMetadataProvider newProvider()
    {
        return new CatalogMetadataProvider();
    }


    /// Declares the types QLiveDomain exposes the given Java types as catalog types.
    public CatalogMetadataProvider catalog(Class<?>... javaTypes)
    {
        for (Class<?> javaType : javaTypes)
        {
            PojoTypes.ensurePojoType(javaType);
            this.javaTypes.add(javaType);
        }
        return this;
    }


    /// Declares the types of the given GraphQL names catalog types, for the types an application has no
    /// class at hand for.
    public CatalogMetadataProvider catalog(String... typeNames)
    {
        this.typeNames.addAll(List.of(typeNames));
        return this;
    }


    @Override
    public void provideMetaData(QLiveDomain domain, DomainMeta meta)
    {
        final Set<String> names = new LinkedHashSet<>();
        for (Class<?> javaType : javaTypes)
        {
            final OutputType outputType = domain.getTypeRegistry().lookup(javaType);
            if (outputType == null)
            {
                throw new QLiveException(
                    "Catalog type declared for " + javaType + ", which the domain does not expose as a type."
                );
            }
            declaredOnce(names, outputType.getName());
        }
        typeNames.forEach(typeName -> declaredOnce(names, typeName));

        for (String typeName : names)
        {
            if (domain.getTypeRegistry().lookupType(typeName) == null)
            {
                throw new QLiveException(
                    "Catalog type declared for '" + typeName + "', which is no table-backed type of the " +
                        "domain. A catalog's rows are read from its table at startup."
                );
            }
            meta.getTypeMeta(typeName).setMeta(CatalogMeta.CATALOG, true);
        }
    }


    private static void declaredOnce(Set<String> names, String typeName)
    {
        if (!names.add(typeName))
        {
            throw new QLiveException("Catalog type '" + typeName + "' declared twice");
        }
    }
}
