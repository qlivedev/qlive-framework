package com.dataciders.qlive.runtime.meta;

import com.dataciders.qlive.model.condition.CNode;
import com.dataciders.qlive.runtime.scalar.ConditionCoercing;
import de.quinscape.domainql.DomainQL;
import graphql.GraphQLContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/// What a type's default query config says, as the partial config it is: the fields named here are the ones
/// the type has an opinion about, and everything else stays at the defaults
/// {@link com.dataciders.qlive.model.QueryConfig} describes.
///
/// This is the declaring end of {@link QueryConfigMeta}. What ends up in the meta data is the map
/// {@link #toMeta(DomainQL)} produces, which is the shape the client's QueryConfigDelta already is -- a
/// condition as FilterDSL JSON, sort fields as the names the client writes them as. Written as a builder
/// rather than as that map so that an application says what it means and finds out about a mistake here
/// instead of in a browser:
///
///     QueryConfigDelta.newDelta()
///         .pageSize(20)
///         .sortFields("name")
public final class QueryConfigDelta
{
    private Integer offset;

    private Integer pageSize;

    private List<String> sortFields;

    private CNode condition;


    private QueryConfigDelta()
    {
    }


    public static QueryConfigDelta newDelta()
    {
        return new QueryConfigDelta();
    }


    /// The row the first page starts at. Rarely what a *type* has an opinion about -- here because a delta
    /// that could not say it would be a different thing from the one the client has.
    public QueryConfigDelta offset(int offset)
    {
        this.offset = offset;
        return this;
    }


    /// How many rows a page holds. 0 means "all of them", which is what a config says when nothing sets it.
    public QueryConfigDelta pageSize(int pageSize)
    {
        this.pageSize = pageSize;
        return this;
    }


    /// The order rows come in, most significant first, as field names -- "name" ascending, "!name"
    /// descending. That is what the FieldExpression scalar reads and writes, so it is also how a sort field
    /// is written on the client.
    public QueryConfigDelta sortFields(String... sortFields)
    {
        this.sortFields = List.of(sortFields);
        return this;
    }


    /// A condition every query of the type carries, written in the server-side FilterDSL. Serialized into
    /// the JSON the client's FilterDSL is, which is what the query config scalar reads back.
    public QueryConfigDelta condition(CNode condition)
    {
        this.condition = condition;
        return this;
    }


    /// This delta as the meta data holds it, i.e. as the JSON the client's QueryConfigDelta is.
    ///
    /// @param domainQL  the domain, which is what knows how to write the values inside a condition
    public Map<String, Object> toMeta(DomainQL domainQL)
    {
        final Map<String, Object> meta = new LinkedHashMap<>();

        if (offset != null)
        {
            meta.put("offset", offset);
        }

        if (pageSize != null)
        {
            meta.put("pageSize", pageSize);
        }

        if (sortFields != null)
        {
            meta.put("sortFields", sortFields);
        }

        if (condition != null)
        {
            meta.put("condition", serializeCondition(domainQL, condition));
        }

        return meta;
    }


    /// A condition as the JSON the client's FilterDSL is.
    ///
    /// Through a coercing of its own rather than through the schema: a condition only ever travels inside a
    /// query config, so the condition scalar is not referenced by any type and never lands in the schema.
    /// Being DomainQLAware is what makes an instance of it usable anyway -- which is also how the query
    /// config's own coercing comes by one.
    private static Object serializeCondition(DomainQL domainQL, CNode condition)
    {
        final ConditionCoercing coercing = new ConditionCoercing();
        coercing.setDomainQL(domainQL);

        return coercing.serialize(condition, GraphQLContext.getDefault(), Locale.ROOT);
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "offset = " + offset
            + ", pageSize = " + pageSize
            + ", sortFields = " + sortFields
            + ", condition = " + condition
            ;
    }
}
