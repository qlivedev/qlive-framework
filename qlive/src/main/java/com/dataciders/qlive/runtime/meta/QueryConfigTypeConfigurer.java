package com.dataciders.qlive.runtime.meta;

import com.dataciders.qlive.model.condition.CNode;
import com.dataciders.qlive.runtime.scalar.ConditionCoercing;
import de.quinscape.domainql.DomainQL;
import graphql.GraphQLContext;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/// What the types one statement of a {@link QueryConfigMetadataProvider} is about say about being queried:
/// the default query config as the partial config it is -- the fields named here are the ones the types
/// have an opinion about, and everything else stays at the defaults
/// {@link com.dataciders.qlive.model.QueryConfig} describes -- plus the maximum page size, which is the
/// other kind of statement and goes elsewhere in the meta data.
///
/// Never constructed directly: {@link QueryConfigMetadataProvider#forType(Class)},
/// {@link QueryConfigMetadataProvider#forTypes(Class[])} and
/// {@link QueryConfigMetadataProvider#forAllTypes()} say which types are meant and hand one of these back.
///
///     QueryConfigMetadataProvider.newProvider()
///         .forType(Foo.class)
///             .pageSize(20)
///             .sortFields("name")
///             .maxPageSize(100)
///             .build()
///
/// {@link #build()} ends the chain and returns the provider; the `andFor...` methods end one statement and
/// begin the next.
///
/// This is the declaring end of {@link QueryConfigMeta}. What ends up in the meta data is the map
/// {@link #toMeta(DomainQL)} produces, which is the shape the client's QueryConfigDelta already is -- a
/// condition as FilterDSL JSON, sort fields as the names the client writes them as. Written as a builder
/// rather than as that map so that an application says what it means and finds out about a mistake here
/// instead of in a browser.
public final class QueryConfigTypeConfigurer
{
    private Integer offset;

    private Integer pageSize;

    private List<String> sortFields;

    private CNode condition;

    private final QueryConfigMetadataProvider queryConfigMetadataProvider;

    private final Consumer<Integer> maxPageSizeConsumer;

    QueryConfigTypeConfigurer(QueryConfigMetadataProvider queryConfigMetadataProvider, Consumer<Integer> maxPageSizeConsumer)
    {
        this.queryConfigMetadataProvider = queryConfigMetadataProvider;
        this.maxPageSizeConsumer = maxPageSizeConsumer;
    }



    /// The row the first page starts at. Rarely what a *type* has an opinion about -- here because a delta
    /// that could not say it would be a different thing from the one the client has.
    public QueryConfigTypeConfigurer offset(int offset)
    {
        this.offset = offset;
        return this;
    }


    /// How many rows a page holds. 0 means "all of them", which is what a config says when nothing sets it.
    public QueryConfigTypeConfigurer pageSize(int pageSize)
    {
        this.pageSize = pageSize;
        return this;
    }


    /// The order rows come in, most significant first, as field names -- "name" ascending, "!name"
    /// descending. That is what the FieldExpression scalar reads and writes, so it is also how a sort field
    /// is written on the client.
    public QueryConfigTypeConfigurer sortFields(String... sortFields)
    {
        this.sortFields = List.of(sortFields);
        return this;
    }


    /// A condition every query of the type carries, written in the server-side FilterDSL. Serialized into
    /// the JSON the client's FilterDSL is, which is what the query config scalar reads back.
    public QueryConfigTypeConfigurer condition(CNode condition)
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

    /// The largest page any query of these rows comes back with, whoever asks. Not part of the delta: a
    /// delta says where a query starts and anything may move it from there, this says how far it can be
    /// moved.
    ///
    /// @param maxPageSize  largest allowed page, greater than 0 -- declare none to leave queries unlimited
    public QueryConfigTypeConfigurer maxPageSize(int maxPageSize)
    {
        this.maxPageSizeConsumer.accept(maxPageSize);
        return this;
    }


    /// Ends this statement and begins one about the given type.
    public QueryConfigTypeConfigurer andForType(Class<?> javaType)
    {
        return this.queryConfigMetadataProvider.forType(javaType);
    }


    /// Ends this statement and begins one about the given types.
    public QueryConfigTypeConfigurer andForTypes(Class<?>... javaTypes)
    {
        return this.queryConfigMetadataProvider.forTypes(javaTypes);
    }


    /// Ends this statement and begins -- or goes on with -- the one about all types.
    public QueryConfigTypeConfigurer andForAllTypes()
    {
        return this.queryConfigMetadataProvider.forAllTypes();
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


    /// Ends the chain, for the bean that has to return the provider itself.
    public QueryConfigMetadataProvider build()
    {
        return this.queryConfigMetadataProvider;
    }
}
