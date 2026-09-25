package io.github.qlivedev.graphql;

import io.github.qlivedev.graphql.meta.DomainQLMeta;
import graphql.schema.GraphQLSchema;

/**
 * The domain a configured {@link QLiveDomainBuilder} produced: the GraphQL schema, what is known about the types in
 * it, and the schema metadata.
 * <p>
 * Immutable, and holds nothing of the assembly that produced it -- see {@link SchemaAssembler} for that.
 */
public class QLiveDomainImpl
    implements QLiveDomain
{
    private final GraphQLSchema graphQLSchema;

    private final TypeRegistry typeRegistry;

    private final DomainQLMeta metaData;


    QLiveDomainImpl(GraphQLSchema graphQLSchema, TypeRegistry typeRegistry, DomainQLMeta metaData)
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
}
