package io.github.qlivedev.qlivetest.runtime.config;

import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.meta.DomainMeta;
import io.github.qlivedev.graphql.meta.MetadataProvider;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;

import java.util.ArrayList;
import java.util.List;

/**
 * Exemplary application-level metadata provider.
 * <p>
 * Every MetadataProvider bean is picked up automatically (see GraphQLConfiguration#domain) and can write into the
 * DomainMeta the server embeds in the page. This one marks the types the application offers in a quick search,
 * writing on both levels the mechanism has: an addendum next to "types", "genericTypes" and "relations", and field
 * meta data on the field the search matches against.
 * <p>
 * The client-side counterpart is frontend/src/qlive-meta.d.ts, which declares the same two names to TypeScript.
 */
public class ExampleMetadataProvider
    implements MetadataProvider
{
    /**
     * Name of the field meta data property marking the quick search field of a type.
     */
    public final static String QUICK_SEARCH = "quickSearch";

    /**
     * Name of the addendum listing the types taking part in the quick search.
     */
    public final static String QUICK_SEARCH_TYPES = "quickSearchTypes";

    /**
     * Field a type has to have to take part in the quick search.
     */
    private final static String NAME_FIELD = "name";


    @Override
    public void provideMetaData(QLiveDomain domain, DomainMeta meta)
    {
        final List<String> quickSearchTypes = new ArrayList<>();

        for (GraphQLNamedType namedType : domain.getGraphQLSchema().getTypeMap().values())
        {
            if (!(namedType instanceof GraphQLObjectType))
            {
                continue;
            }

            final String typeName = namedType.getName();

            // the type meta data only exists for the types QLiveDomain knows a Java type for
            if (domain.getTypeRegistry().lookup(typeName) == null)
            {
                continue;
            }

            if (((GraphQLObjectType) namedType).getFieldDefinition(NAME_FIELD) == null)
            {
                continue;
            }

            meta.getTypeMeta(typeName).setFieldMeta(NAME_FIELD, QUICK_SEARCH, true);
            quickSearchTypes.add(typeName);
        }

        quickSearchTypes.sort(String::compareTo);

        meta.addAddendum(QUICK_SEARCH_TYPES, quickSearchTypes);
    }
}
