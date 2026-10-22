package io.github.qlivedev.qlive.graphql;

import io.github.qlivedev.qlive.graphql.logic.Action;
import io.github.qlivedev.qlive.graphql.meta.DomainMeta;
import graphql.schema.GraphQLSchema;

import java.util.List;

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

    private final DomainMeta metaData;

    private final List<Action> actions;


    QLiveDomainImpl(GraphQLSchema graphQLSchema, TypeRegistry typeRegistry, DomainMeta metaData, List<Action> actions)
    {
        this.actions = List.copyOf(actions);
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
    public DomainMeta getMetaData()
    {
        return metaData;
    }


    @Override
    public List<Action> getActions()
    {
        return actions;
    }
}
