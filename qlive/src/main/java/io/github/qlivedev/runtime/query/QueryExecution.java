package io.github.qlivedev.runtime.query;

import io.github.qlivedev.model.QueryConfig;
import io.github.qlivedev.model.QueryDocument;
import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.graphql.config.RelationModel;
import io.github.qlivedev.graphql.fetcher.FetcherContext;
import io.github.qlivedev.graphql.generic.DomainObject;
import io.github.qlivedev.util.JSONUtil;
import org.jooq.Condition;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Record1;
import org.jooq.Result;
import org.jooq.SelectJoinStep;
import org.jooq.Select;
import org.jooq.SelectLimitStep;
import org.jooq.TableField;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// Runs a query plan and turns what comes back into the document's rows.
///
/// One statement fetches the root type and everything joinable below it -- every to-one relation the query
/// selects -- and one more counts the rows the paging cut off. Each to-many relation gets a query of its
/// own, keyed by the parents already fetched, because joining it would multiply the rows and take both the
/// page and the count with it.
///
/// What the rows end up carrying is a fetcher context per object, which is what stops the schema's own
/// relation fetchers from going back to the database for something this already has. That is also why a
/// context is filled completely or not attached at all: a fetcher that finds a context asks it and nothing
/// else, so a relation missing from one would silently resolve to null.
public class QueryExecution
{
    private final static Logger log = LoggerFactory.getLogger(QueryExecution.class);

    private final DSLContext dslContext;

    /// Materialized objects per plan node, so that a to-many relation can find the parents it belongs to.
    private final Map<PlanNode, List<Object>> materialized = new IdentityHashMap<>();

    /// Where each plan node's columns sit in the records of the query that fetches it.
    private final Map<PlanNode, Slice> slices = new IdentityHashMap<>();


    public QueryExecution(DSLContext dslContext)
    {
        this.dslContext = dslContext;
    }


    /// The columns of one plan node within one result record.
    ///
    /// @param node         plan node
    /// @param fields       its columns, in the order they were added to the select list
    /// @param offset       where they start within the record
    /// @param keyIndexes   positions of the key columns within `fields`, which are what says whether a left
    ///                     joined row was found at all
    private record Slice(
        PlanNode node,
        Field<?>[] fields,
        int offset,
        int[] keyIndexes
    )
    {
    }


    @SuppressWarnings("unchecked")
    public <T> QueryDocument<T> execute(Class<T> type, QueryPlan plan)
    {
        final PlanNode root = plan.root();
        final QueryConfig config = plan.config();

        final Select<Record> query = mainQuery(plan);

        log.debug("Query document for {}: {}", type.getSimpleName(), query);

        final Result<Record> result = query.fetch();

        final List<T> rows = new ArrayList<>(result.size());
        for (Record record : result)
        {
            rows.add((T) materialize(root, record));
        }

        for (PlanNode toMany : root.toManyChildren())
        {
            fetch(toMany);
        }

        final QueryDocument<T> document = new QueryDocument<>(type);
        document.setConfig(config);
        document.setRows(rows);
        document.setRowCount(
            config.getPageSize() > 0 ? count(plan) : rows.size()
        );

        return document;
    }


    /// The statement that fetches the root type and every to-one relation below it.
    ///
    /// Built apart from being run, so that what it comes to can be looked at without a database behind it.
    Select<Record> mainQuery(QueryPlan plan)
    {
        final PlanNode root = plan.root();
        final QueryConfig config = plan.config();

        final List<Field<?>> selectFields = new ArrayList<>();
        slice(root, selectFields);

        final List<PlanNode> joined = root.joinedDescendants();
        for (PlanNode node : joined)
        {
            slice(node, selectFields);
        }

        SelectJoinStep<Record> from = dslContext.select(selectFields).from(root.getTable());
        for (PlanNode node : joined)
        {
            from = from.leftJoin(node.getTable()).on(node.joinCondition());
        }

        final SelectLimitStep<Record> ordered = from
            .where(condition(plan))
            .orderBy(plan.sortFields());

        final int pageSize = config.getPageSize();
        final int offset = config.getOffset();

        if (pageSize > 0)
        {
            return ordered.limit(offset, pageSize);
        }
        if (offset > 0)
        {
            return ordered.offset(offset);
        }
        return ordered;
    }


    private static Condition condition(QueryPlan plan)
    {
        return plan.condition() == null ? DSL.noCondition() : plan.condition();
    }


    /// Counts what the query would return without its page.
    ///
    /// The joins stay: they are all left joins, so they cannot change the count, and dropping the ones the
    /// condition does not use would mean working out which those are.
    private int count(QueryPlan plan)
    {
        final Select<Record1<Integer>> query = countQuery(plan);

        log.debug("Row count: {}", query);

        final Integer count = query.fetchOne(0, Integer.class);

        return count == null ? 0 : count;
    }


    /// The statement counting what the query would return without its page.
    ///
    /// It joins what the condition reads and nothing else. The joins it leaves out cannot change a count --
    /// they are all left joins on keys -- so what they would cost is work nobody reads.
    Select<Record1<Integer>> countQuery(QueryPlan plan)
    {
        SelectJoinStep<Record1<Integer>> from = dslContext.selectCount().from(plan.root().getTable());
        for (PlanNode node : plan.countJoins())
        {
            from = from.leftJoin(node.getTable()).on(node.joinCondition());
        }

        return from.where(condition(plan));
    }


    // -----------------------------------------------------------------------------------------------------
    // to-many relations
    // -----------------------------------------------------------------------------------------------------

    /// Fetches one to-many relation for the parents that are already there, and then whatever hangs below
    /// it. One query per relation, whatever the number of rows.
    private void fetch(PlanNode node)
    {
        final List<Object> parents = materialized.getOrDefault(node.getParent(), List.of());
        if (parents.isEmpty())
        {
            return;
        }

        final RelationModel relation = node.getRelation();
        final List<String> parentProperties = relation.getTargetFields();
        final List<? extends TableField<?, ?>> foreignKey = relation.getSourceDBFields();

        final Set<List<Object>> keys = new LinkedHashSet<>();
        for (Object parent : parents)
        {
            final List<Object> key = key(parent, parentProperties);
            if (key != null)
            {
                keys.add(key);
            }
        }

        if (keys.isEmpty())
        {
            for (Object parent : parents)
            {
                context(parent).setProperty(node.getFieldName(), List.of());
            }
            return;
        }

        final List<Field<?>> selectFields = new ArrayList<>();
        slice(node, selectFields);

        final List<PlanNode> joined = node.joinedDescendants();
        for (PlanNode descendant : joined)
        {
            slice(descendant, selectFields);
        }

        SelectJoinStep<Record> from = dslContext.select(selectFields).from(node.getTable());
        for (PlanNode descendant : joined)
        {
            from = from.leftJoin(descendant.getTable()).on(descendant.joinCondition());
        }

        final Select<Record> query = from
            .where(keyCondition(node, foreignKey, keys))
            .orderBy(node.getKeyFields());

        log.debug("Relation '{}' of {} parent(s): {}", node.getFieldName(), parents.size(), query);

        final Result<Record> result = query.fetch();

        final Map<List<Object>, List<Object>> byKey = new HashMap<>();
        for (Record record : result)
        {
            final Object child = materialize(node, record);
            if (child == null)
            {
                continue;
            }

            final List<Object> key = key(child, relation.getSourceFields());
            if (key != null)
            {
                byKey.computeIfAbsent(key, k -> new ArrayList<>()).add(child);
            }
        }

        for (Object parent : parents)
        {
            final List<Object> key = key(parent, parentProperties);
            context(parent).setProperty(
                node.getFieldName(),
                key == null ? List.of() : byKey.getOrDefault(key, List.of())
            );
        }

        for (PlanNode deeper : node.toManyChildren())
        {
            fetch(deeper);
        }
    }


    /// `WHERE fk IN (parent keys)`, or its long form for a composite key.
    @SuppressWarnings("unchecked")
    private Condition keyCondition(
        PlanNode node,
        List<? extends TableField<?, ?>> foreignKey,
        Set<List<Object>> keys
    )
    {
        if (foreignKey.size() == 1)
        {
            final Field<Object> field = (Field<Object>) column(node, foreignKey.get(0));

            final List<Object> values = new ArrayList<>(keys.size());
            for (List<Object> key : keys)
            {
                values.add(key.get(0));
            }
            return field.in(values);
        }

        final List<Condition> conditions = new ArrayList<>(keys.size());
        for (List<Object> key : keys)
        {
            final List<Condition> parts = new ArrayList<>(foreignKey.size());
            for (int i = 0; i < foreignKey.size(); i++)
            {
                parts.add(
                    ((Field<Object>) column(node, foreignKey.get(i))).eq(key.get(i))
                );
            }
            conditions.add(DSL.and(parts));
        }
        return DSL.or(conditions);
    }


    private Field<?> column(PlanNode node, Field<?> field)
    {
        final Field<?> column = node.getColumn(field.getName());
        if (column == null)
        {
            throw new QLiveException(
                "'" + node.getAlias() + "' does not select the key column '" + field.getName() + "'"
            );
        }
        return column;
    }


    /// The values of the given properties of one object, or `null` if any of them is null -- a key with a
    /// null in it matches nothing, and looking for it would only produce an empty list the long way round.
    private static List<Object> key(Object object, List<String> properties)
    {
        final List<Object> key = new ArrayList<>(properties.size());
        for (String property : properties)
        {
            final Object value = JSONUtil.DEFAULT_UTIL.getProperty(object, property);
            if (value == null)
            {
                return null;
            }
            key.add(value);
        }
        return key;
    }


    // -----------------------------------------------------------------------------------------------------
    // materialization
    // -----------------------------------------------------------------------------------------------------

    private void slice(PlanNode node, List<Field<?>> selectFields)
    {
        final List<Field<?>> columns = new ArrayList<>(node.getColumns());
        if (columns.isEmpty())
        {
            throw new QLiveException("'" + node.getAlias() + "' selects no columns at all");
        }

        final int offset = selectFields.size();
        selectFields.addAll(columns);

        // a table without a primary key has nothing better to go by than all of its columns being null
        final List<Field<?>> keyFields = node.getKeyFields().isEmpty() ? columns : node.getKeyFields();

        final int[] keyIndexes = new int[keyFields.size()];
        for (int i = 0; i < keyFields.size(); i++)
        {
            keyIndexes[i] = columns.indexOf(keyFields.get(i));
        }

        slices.put(node, new Slice(node, columns.toArray(new Field<?>[0]), offset, keyIndexes));
    }


    /// Builds one object and everything joined below it, and gives it the fetcher context that keeps
    /// QLiveDomain from fetching any of it again.
    private Object materialize(PlanNode node, Record record)
    {
        final Slice slice = slices.get(node);

        final Object[] values = new Object[slice.fields().length];
        for (int i = 0; i < values.length; i++)
        {
            values[i] = record.get(slice.offset() + i);
        }

        if (node.getParent() != null && absent(slice, values))
        {
            return null;
        }

        final Record own = dslContext.newRecord(slice.fields());
        own.fromArray(values);

        final Object pojo = own.into(node.getPojoType());

        materialized.computeIfAbsent(node, n -> new ArrayList<>()).add(pojo);

        if (!node.getChildren().isEmpty())
        {
            final FetcherContext fetcherContext = new FetcherContext();
            for (PlanNode child : node.getChildren())
            {
                if (child.isToMany())
                {
                    // filled in by the relation's own query, which needs this object to exist first.
                    // Left null rather than empty meanwhile: a fetcher context is answered as it stands,
                    // and a relation that never got fetched should not read as a relation without rows.
                    fetcherContext.setProperty(child.getFieldName(), null);
                }
                else
                {
                    fetcherContext.setProperty(
                        child.getFieldName(),
                        materialize(child, record)
                    );
                }
            }
            context(pojo, fetcherContext);
        }

        return pojo;
    }


    private static boolean absent(Slice slice, Object[] values)
    {
        for (int keyIndex : slice.keyIndexes())
        {
            if (values[keyIndex] != null)
            {
                return false;
            }
        }
        return true;
    }


    private static void context(Object pojo, FetcherContext fetcherContext)
    {
        if (!(pojo instanceof DomainObject domainObject))
        {
            throw new QLiveException(
                "Cannot prefetch relations of " + pojo.getClass().getName() + ": it is not a DomainObject"
            );
        }
        domainObject.provideFetcherContext(fetcherContext);
    }


    private static FetcherContext context(Object pojo)
    {
        if (!(pojo instanceof DomainObject domainObject))
        {
            throw new QLiveException(
                "Cannot prefetch relations of " + pojo.getClass().getName() + ": it is not a DomainObject"
            );
        }

        final FetcherContext fetcherContext = domainObject.lookupFetcherContext();
        if (fetcherContext == null)
        {
            throw new QLiveException("No fetcher context on " + pojo.getClass().getName());
        }
        return fetcherContext;
    }
}
