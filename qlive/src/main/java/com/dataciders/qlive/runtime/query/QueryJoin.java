package com.dataciders.qlive.runtime.query;

import de.quinscape.domainql.config.RelationModel;
import org.jooq.Table;

import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates information about one usage of a table within an interactive query.
 */
final class QueryJoin
{
    private final QueryExecutionContext ctx;

    private final Class<?> pojoType;

    private final Table<?> table;

    private final String alias;

    private final RelationModel relationModel;

    private final String location;

    private QueryJoin parentJoin;

    private List<QueryJoin> joins;

    public QueryJoin(
        QueryExecutionContext ctx,
        Table<?> table,
        Class<?> pojoType,
        String alias,
        String location,
        QueryJoin parentJoin,
        RelationModel relationModel
    )
    {
        this.ctx = ctx;
        this.pojoType = pojoType;
        this.table = table;
        this.alias = alias;
        this.location = location;

        this.parentJoin =  parentJoin;
        if  (parentJoin != null)
        {
            parentJoin.addJoin(this);
        }
        
        this.relationModel = relationModel;
        this.joins = new ArrayList<>();
    }


    void addJoin(QueryJoin queryJoin)
    {
        joins.add(queryJoin);
    }

    public List<QueryJoin> getJoins()
    {
        return joins;
    }


    /**
     * Creates a pseudo join entry for the root type
     *
     * @param table JOOQ table of the root type
     * @param alias alias within the current query
     */
    public QueryJoin(
        QueryExecutionContext ctx,
        Table<?> table,
        Class<?> pojoType,
        String alias,
        String location
    )
    {
        this(
            ctx,
            table,
            pojoType,
            alias,
            location,
            null,
            null
        );
    }


    /**
     * Current alias of the source/referencing table.
     *
     * @return source alias
     */
    public String getSourceTableAlias()
    {
        return parentJoin.getAlias();
    }


    /**
     * JOOQ table for the referenced / target side table.
     *
     * @return target JOOQ table
     */
    public Table<?> getTable()
    {
        return table;
    }

    /**
     * Returns the POJO type for the referenced / target domain type.
     *
     * @return POJO type
     */
    public Class<?> getPojoType()
    {
        return pojoType;
    }


    /**
     * Current alias for the table involved.
     *
     * @return alias
     */
    public String getAlias()
    {
        return alias;
    }

    public RelationModel getRelationModel()
    {
        return relationModel;
    }


    public QueryJoin getParentJoin()
    {
        return parentJoin;
    }


    public QueryExecutionContext getQueryExecutionContext()
    {
        return ctx;
    }


    public String getLocation()
    {
        return location;
    }


    public boolean isBackReference()
    {
        return relationModel != null && relationModel.getSourceType().equals(pojoType.getSimpleName());
    }

    @Override
    public String toString()
    {
        return super.toString() + ": "
            + ", table = " + table
            + ", alias = '" + alias + '\''
            + ", joins = " + joins
            ;
    }
}
