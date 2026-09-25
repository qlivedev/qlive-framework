package io.github.qlivedev.graphql;

import io.github.qlivedev.graphql.meta.DomainMeta;
import graphql.schema.GraphQLSchema;

/**
 * The domain of a QLive application: the GraphQL schema it exposes, what is known about the types in it, and the
 * metadata the schema carries.
 * <p>
 * This is what the framework hands an application. The schema is assembled once at startup by
 * {@link QLiveDomainBuilder}, and everything that runs afterwards -- the runtime itself, an application's
 * {@link io.github.qlivedev.graphql.meta.MetadataProvider} or {@link DomainQLAware} scalar, a service holding the
 * domain as a bean -- reads it through here.
 */
public interface QLiveDomain
{
    /**
     * Returns the GraphQL schema of the domain.
     *
     * @return GraphQL schema
     */
    GraphQLSchema getGraphQLSchema();


    /**
     * Returns what the domain knows about its types: the GraphQL type, the Java class behind it, and for the
     * application's tables the jOOQ table, its columns and the relations it takes part in.
     *
     * @return type registry
     */
    TypeRegistry getTypeRegistry();


    /**
     * Returns the schema metadata, both QLive's own and whatever the application's metadata providers contributed.
     *
     * @return metadata
     */
    DomainMeta getMetaData();
}
