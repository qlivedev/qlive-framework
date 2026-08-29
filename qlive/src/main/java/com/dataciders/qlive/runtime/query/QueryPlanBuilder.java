package com.dataciders.qlive.runtime.query;

import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.GenericTypeReference;
import de.quinscape.domainql.config.RelationModel;
import de.quinscape.domainql.fetcher.BackReferenceFetcher;
import de.quinscape.domainql.fetcher.FieldFetcher;
import de.quinscape.domainql.fetcher.ReferenceFetcher;
import com.dataciders.qlive.model.QueryDocument;
import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.util.Util;
import graphql.schema.DataFetcher;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import graphql.schema.SelectedField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Optional;

/**
 * Builder class for QueryPlan instances.
 *
 * @param <T> Payload type
 */
public class QueryPlanBuilder<T>
{
    private final static Logger log = LoggerFactory.getLogger(QueryPlanBuilder.class);

    private final QueryExecutionContext ctx;

    private boolean selectByFilter = false;


    public QueryPlanBuilder(QueryExecutionContext ctx)
    {
        this.ctx = ctx;
    }


    public QueryDocument<T> execute()
    {
        return build()
            .execute();
    }


    public DocumentQuery<T> build()
    {
        try
        {
            final String domainTypeName = ctx.getType().getSimpleName();

            final GraphQLType rootType = GraphQLTypeUtil.unwrapNonNull(ctx.getEnv().getFieldType());

            if (!GraphQLTypeUtil.isObjectType(rootType))
            {
                throw new  IllegalStateException("Root type must be ObjectType");
            }

            final String rootTypeName = ((GraphQLObjectType) rootType).getName();
            validateRootType(rootTypeName, domainTypeName);

            final DocumentQuery<T> documentQuery = new DocumentQuery<>(ctx);


            // all subselections of rows/** as selection set
            final DataFetchingFieldSelectionSet rowsSelection = ctx.getEnv().getSelectionSet()
                .getFields("rows")
                .getFirst()
                .getSelectionSet();

            final String tableAlias = ctx.getShortName(domainTypeName);
            final QueryJoin rootJoin = new QueryJoin(
                ctx,
                ctx.getDomainQL().lookupType(domainTypeName).getTable(),
                ctx.getType(),
                tableAlias,
                ""
            );

            QueryExecution execution = new QueryExecution(
                rootJoin,
                null
            );
            documentQuery.addExecution(execution);

            collectJoins(documentQuery, rowsSelection, domainTypeName, rootJoin, execution, "");



            log.debug("DocumentQuery: {}", documentQuery);

            return documentQuery;

        }
        catch (Exception e)
        {
            log.error("Error building query plan", e);
            throw new QLiveException(e);
        }
    }


    private void collectJoins(
        DocumentQuery<T> documentQuery,
        DataFetchingFieldSelectionSet selectionSet,
        String typeName,
        QueryJoin join,
        QueryExecution execution,
        String location
    )
    {
        final DomainQL domainQL = ctx.getDomainQL();

        final List<SelectedField> fields = selectionSet.getImmediateFields();

        for (SelectedField field : fields)
        {
            final GraphQLObjectType objectType = field.getObjectTypes().get(0);

            if (!objectType.getName().equals(typeName))
            {
                throw new IllegalStateException("ObjectType " + objectType.getName() + " does not match type " + typeName);
            }

            final DataFetcher<?> dataFetcher = domainQL.getGraphQLSchema().getCodeRegistry().getDataFetcher(objectType, objectType.getFieldDefinition(field.getName()));
            final String fieldLocation = location.isEmpty() ? field.getName() : location + "." + field.getName();
            if (dataFetcher instanceof BackReferenceFetcher)
            {
                DataFetchingFieldSelectionSet subSelection = field.getSelectionSet();
                RelationModel relationModel = ((BackReferenceFetcher) dataFetcher).getRelationModel();


                // we're navigating the relation backwards
                final String domainTypeName = relationModel.getSourceType();

                String alias = ctx.getShortName(domainTypeName);

                final QueryJoin newJoin = new QueryJoin(
                    ctx,
                    ctx.getDomainQL().lookupType(domainTypeName).getTable(),
                    ctx.getType(),
                    alias,
                    fieldLocation,
                    join,
                    relationModel
                );

                // we need to join the independent queries in-memory, so we need to select the id fields of the target type
                relationModel.getTargetFields().forEach(
                    name -> execution.addField(join.getAlias(), name)
                );

                final QueryExecution queryExecution = new QueryExecution(
                    newJoin,
                    fieldLocation
                );

                relationModel.getSourceFields().forEach(
                    name -> queryExecution.addField(newJoin.getAlias(), name)
                );

                execution.addDependentQuery(queryExecution);
                documentQuery.addExecution(queryExecution);

                collectJoins(
                    documentQuery,
                    subSelection,
                    domainTypeName,
                    newJoin,
                    queryExecution,
                    // we reset the field location relative to new query execution
                    ""
                );
            }
            else if (dataFetcher instanceof FieldFetcher ff)
            {
                execution.addField(join.getAlias(), ff.getFieldName());
            }
            else if (dataFetcher instanceof ReferenceFetcher referenceFetcher)
            {
                final RelationModel relationModel = referenceFetcher.getRelationModel();
                final String alias = ctx.getShortName(execution, field.getName());
                QueryJoin queryJoin = new QueryJoin(
                    ctx,
                    relationModel.getTargetTable(),
                    relationModel.getTargetPojoClass(),
                    alias,
                    fieldLocation,
                    join,
                    relationModel
                );

                collectJoins(
                    documentQuery,
                    field.getSelectionSet(),
                    relationModel.getTargetType(),
                    queryJoin,
                    execution,
                    fieldLocation
                );
            }
        }
    }


    private void validateRootType(final String name, final String domainTypeName)
    {
        final Optional<GenericTypeReference> optGeneric = Util.findQueryDocumentType(ctx.getDomainQL(), name);

        if (!optGeneric.isPresent() || !optGeneric.get().getTypeParameters().getFirst().equals(domainTypeName))
        {
            throw new IllegalStateException("Document type '" + name + "' is not a QueryDocument with payload type '" + domainTypeName + "'");
        }
    }

    /**
     * <p>
     * If set to <code>true </code>, allow fields to be implicitly selected by referencing them in filter expressions
     * even though they are not in the selection set.
     * </p>
     * <p>
     * This might have security implications even though the relation model should generally account for such issues.
     * For
     * this reason, the default for this is <code>false</code>
     * </p>
     *
     * @return allow implicit field selection by filter expression (default: false)
     */
    public QueryPlanBuilder<T> selectByFilter(boolean selectByFilter)
    {
        this.selectByFilter = selectByFilter;
        return this;
    }


    public boolean isSelectByFilter()
    {
        return selectByFilter;
    }
}
