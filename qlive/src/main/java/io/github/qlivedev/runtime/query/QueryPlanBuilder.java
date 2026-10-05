package io.github.qlivedev.runtime.query;

import io.github.qlivedev.model.QueryConfig;
import io.github.qlivedev.model.condition.CNode;
import io.github.qlivedev.model.condition.Operation;
import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.runtime.query.condition.ConditionTransformer;
import io.github.qlivedev.runtime.query.condition.ExistsScope;
import io.github.qlivedev.runtime.query.condition.FieldResolver;
import io.github.qlivedev.runtime.query.condition.ResolvedField;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.TableLookup;
import io.github.qlivedev.graphql.TypeRegistry;
import io.github.qlivedev.graphql.config.ManyToManyField;
import io.github.qlivedev.graphql.config.RelationModel;
import io.github.qlivedev.graphql.config.TargetField;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/// Works out what one query document query has to do, before any SQL is built.
///
/// The plan comes from the GraphQL selection: what the query selects is what gets queried, and in the
/// strict mode that is also the whole of what it can be filtered and sorted by. Since the selection is
/// static source text that the frontend build has already analyzed, that makes the query document itself
/// the boundary -- a config posted by a browser varies the `WHERE`, the `ORDER BY` and the page, and can
/// reach nothing the document does not name.
public class QueryPlanBuilder
{
    private final static Logger log = LoggerFactory.getLogger(QueryPlanBuilder.class);

    /// Field of the document type carrying the payload. Everything else on it -- `type`, `config`,
    /// `rowCount` -- is about the document, not about the rows, and contributes nothing to the query.
    private final static String ROWS = "rows";

    /// Longest identifier Postgres keeps; longer ones are silently truncated, which would turn two aliases
    /// into one without anybody noticing.
    private final static int MAX_ALIAS_LENGTH = 63;

    private final TypeRegistry types;


    public QueryPlanBuilder(QLiveDomain domain)
    {
        this.types = domain.getTypeRegistry();
    }


    /// Plans one query document query.
    ///
    /// @param type             the document's row type
    /// @param env              data fetching environment of the GraphQL query selecting the document
    /// @param config           query config as the client sent it
    /// @param selectByFilter   whether a filter or sort path may name a field the query does not select
    public QueryPlan build(
        Class<?> type,
        DataFetchingEnvironment env,
        QueryConfig config,
        boolean selectByFilter
    )
    {
        final String domainType = type.getSimpleName();
        final TableLookup lookup = types.lookupType(domainType);

        if (lookup == null)
        {
            throw new QLiveException(
                "No type '" + domainType + "' in the domain. A query document queries a table the domain " +
                    "exposes, under the name it exposes it under."
            );
        }

        final Set<String> aliases = new HashSet<>();

        final PlanNode root = new PlanNode(
            null,
            null,
            domainType,
            lookup.getPojoType(),
            lookup.getTable(),
            uniqueAlias(snakeCase(domainType), aliases),
            null,
            false,
            false
        );

        for (SelectedField field : env.getSelectionSet().getImmediateFields())
        {
            if (ROWS.equals(field.getName()) && field.getSelectionSet() != null)
            {
                walk(root, field.getSelectionSet(), aliases);
            }
        }

        final PathResolver resolver = new PathResolver(root, selectByFilter, aliases);

        final ConditionTransformer transformer = new ConditionTransformer(resolver);

        final Condition condition = transformer.transform(config.getCondition());

        // taken before the sort fields are resolved, which is the point at which the touched nodes are the
        // condition's and nothing else
        final List<PlanNode> countJoins = countJoins(root, resolver.touched);

        final List<SortField<?>> sortFields = new ArrayList<>();
        sort(root, config, transformer, sortFields);

        final QueryConfig effective = new QueryConfig();
        effective.setCondition(config.getCondition());
        effective.setOffset(config.getOffset());
        effective.setPageSize(config.getPageSize());
        effective.setSortFields(
            config.getSortFields() != null ? new ArrayList<>(config.getSortFields()) : new ArrayList<>()
        );

        final QueryPlan plan = new QueryPlan(root, condition, sortFields, countJoins, effective);

        log.debug("Query plan for {}: {}", domainType, plan);

        return plan;
    }


    /// The joins the row count needs, which are the ones its condition reads through and no others.
    ///
    /// Every join is a left join on a key, so none of them can change a count and leaving them all in
    /// would be correct -- it would just be work nobody reads. What a node needs is its ancestors, so that
    /// each join has the alias its ON clause names.
    private static List<PlanNode> countJoins(PlanNode root, Set<PlanNode> touched)
    {
        final Set<PlanNode> needed = Collections.newSetFromMap(new IdentityHashMap<>());

        for (PlanNode node : touched)
        {
            for (PlanNode current = node; current.getParent() != null; current = current.getParent())
            {
                needed.add(current);
            }
        }

        // in the order they were planned, so that a join comes after the one it is joined to
        return root.joinedDescendants().stream().filter(needed::contains).toList();
    }


    /// Transforms the config's sort fields, or defaults them to the primary key.
    ///
    /// The default is resolved directly against the root instead of going through the resolver, because
    /// the primary key is one of the columns the plan selects for its own purposes and a client never had
    /// to ask for it.
    ///
    /// A named sort is completed to a total order (see {@link #completion(Table, Set)}), because offset
    /// paging over ties can show a row on two pages or on none. The root is completed at the end. Before
    /// that, each run of sort fields naming columns of one related row is completed toward that row's
    /// identity, right after the run: sorting by `owner.name` alone would interleave the rows of two
    /// owners sharing a name, and `owner.name, owner.id` keeps each owner's rows together.
    ///
    /// The default and the completion go into the statement only. The config that goes back has the sort
    /// of the config the plan was built from -- whatever assembled that, type defaults and interceptors
    /// included, is reflected, and this is not. Both follow from that sort the same way every time, so
    /// echoing the config gets the same order again, and a client displaying the sort never sees one
    /// nobody chose.
    private void sort(
        PlanNode root,
        QueryConfig config,
        ConditionTransformer transformer,
        List<SortField<?>> sortFields
    )
    {
        if (config.getSortFields() != null && !config.getSortFields().isEmpty())
        {
            final Set<String> rootColumns = new HashSet<>();

            PlanNode runNode = null;
            final Set<String> runColumns = new HashSet<>();

            for (CNode node : config.getSortFields())
            {
                // transformed first: that resolves the path, joining what it crosses where it may
                final SortField<?> sortField = transformer.sortField(node);

                final SortedColumn sorted = sortedColumn(root, node);
                final PlanNode related = sorted != null && sorted.node() != root ? sorted.node() : null;
                if (related != runNode)
                {
                    complete(runNode, runColumns, sortFields);
                    runNode = related;
                    runColumns.clear();
                }

                sortFields.add(sortField);
                if (related != null)
                {
                    runColumns.add(sorted.column());
                }
                else if (sorted != null)
                {
                    rootColumns.add(sorted.column());
                }
            }
            complete(runNode, runColumns, sortFields);

            complete(root, rootColumns, sortFields);
            return;
        }

        if (root.getKeyFields().isEmpty())
        {
            throw new QLiveException(
                "Cannot sort '" + root.getDomainType() + "' by default: it has no primary key. Queries " +
                    "against it have to name their sort fields, or paging over them cannot be stable."
            );
        }

        for (Field<?> keyField : root.getKeyFields())
        {
            sortFields.add(keyField.asc());
        }
    }


    /// A column a sort field names directly: a field path, bare or in an `asc` or `desc`, together with the
    /// plan node whose row it belongs to. `null` for an expression, which orders by something else even
    /// where it mentions a column, and cannot cover a key.
    private record SortedColumn(PlanNode node, String column)
    {
    }


    private SortedColumn sortedColumn(PlanNode root, CNode node)
    {
        CNode fieldNode = node;
        if (node instanceof Operation operation &&
            ("asc".equals(operation.getName()) || "desc".equals(operation.getName())) &&
            operation.getOperands() != null && operation.getOperands().size() == 1)
        {
            fieldNode = operation.getOperands().get(0);
        }

        if (!(fieldNode instanceof io.github.qlivedev.model.condition.Field fieldRef))
        {
            return null;
        }

        final String[] parts = fieldRef.getName().split("\\.", -1);
        PlanNode target = root;
        for (int i = 0; i < parts.length - 1 && target != null; i++)
        {
            target = target.getChild(parts[i]);
        }
        if (target == null)
        {
            return null;
        }

        final Field<?> column = types.lookupField(target.getDomainType(), parts[parts.length - 1]);
        return column != null ? new SortedColumn(target, column.getName()) : null;
    }


    /// Appends what a sort over the given columns of a node's rows needs to be total over those rows, as
    /// {@link #completion(Table, Set)} says, ascending. Nothing for no node.
    private void complete(PlanNode node, Set<String> columns, List<SortField<?>> sortFields)
    {
        if (node == null)
        {
            return;
        }

        final Table<?> table = types.lookupType(node.getDomainType()).getTable();
        for (Field<?> field : completion(table, columns))
        {
            sortFields.add(node.addColumn(field, false).asc());
        }
    }


    /// The columns a sort over the given columns of a table needs appended to be a total order, in the
    /// order to append them, ascending. Empty where it already is one, or where the table has nothing to
    /// complete it with.
    ///
    /// What makes rows distinct is a unique key whose columns are all NOT NULL: the database lets any
    /// number of rows hold NULL in a unique constraint, so a nullable one leaves ties. Only constraints
    /// count, as jOOQ's generated metadata carries them -- a unique index that is not a constraint is not in
    /// it. With such keys:
    ///
    /// - a sort covering all columns of one is total already
    /// - one covering part of one or more gets the missing columns of the key missing the fewest, in
    ///   constraint order, the primary key winning a tie
    /// - one covering none gets the primary key, or the shortest key where the table has none
    ///
    /// @param table            table the rows come from, unaliased
    /// @param sortedColumns    names of the columns the sort names directly
    static List<Field<?>> completion(Table<?> table, Set<String> sortedColumns)
    {
        final List<UniqueKey<?>> keys = new ArrayList<>();
        final UniqueKey<?> primaryKey = table.getPrimaryKey();
        if (primaryKey != null && isNotNull(primaryKey))
        {
            keys.add(primaryKey);
        }
        for (UniqueKey<?> key : table.getUniqueKeys())
        {
            if (isNotNull(key))
            {
                keys.add(key);
            }
        }

        if (keys.isEmpty())
        {
            return List.of();
        }

        UniqueKey<?> best = null;
        int bestMissing = Integer.MAX_VALUE;
        for (UniqueKey<?> key : keys)
        {
            final int missing = missing(key, sortedColumns).size();
            if (missing == 0)
            {
                return List.of();
            }

            // the primary key comes first, so a strict comparison lets it win a tie
            if (missing < key.getFields().size() && missing < bestMissing)
            {
                best = key;
                bestMissing = missing;
            }
        }

        if (best == null)
        {
            best = keys.get(0) == primaryKey ? primaryKey : shortest(keys);
        }

        return missing(best, sortedColumns);
    }


    private static boolean isNotNull(UniqueKey<?> key)
    {
        for (Field<?> field : key.getFields())
        {
            if (field.getDataType().nullable())
            {
                return false;
            }
        }
        return true;
    }


    private static List<Field<?>> missing(UniqueKey<?> key, Set<String> sortedColumns)
    {
        final List<Field<?>> missing = new ArrayList<>();
        for (Field<?> field : key.getFields())
        {
            if (!sortedColumns.contains(field.getName()))
            {
                missing.add(field);
            }
        }
        return missing;
    }


    private static UniqueKey<?> shortest(List<UniqueKey<?>> keys)
    {
        UniqueKey<?> shortest = keys.get(0);
        for (UniqueKey<?> key : keys)
        {
            if (key.getFields().size() < shortest.getFields().size())
            {
                shortest = key;
            }
        }
        return shortest;
    }


    // -----------------------------------------------------------------------------------------------------
    // selection
    // -----------------------------------------------------------------------------------------------------

    private void walk(PlanNode node, DataFetchingFieldSelectionSet selectionSet, Set<String> aliases)
    {
        for (SelectedField selected : selectionSet.getImmediateFields())
        {
            final DataFetchingFieldSelectionSet sub = selected.getSelectionSet();

            if (sub != null && !sub.getImmediateFields().isEmpty())
            {
                // the same relation selected twice under different aliases is one node with the union of
                // the columns -- both selections are served the same object
                PlanNode child = node.getChild(selected.getName());
                if (child == null)
                {
                    child = relation(node, selected.getName(), aliases);
                }
                walk(child, sub, aliases);
            }
            else
            {
                column(node, selected.getName());
            }
        }
    }


    /// Selects one field of the GraphQL selection, if it is a column.
    ///
    /// A field that is not one is left where it is. A handwritten type replacing a generated one can add
    /// fields the table has no column for, and QLiveDomain fetches those from the object itself -- there is
    /// nothing for this to select and nothing to fail over, and GraphQL has already established that the
    /// field exists on the type. What such a field computes from, it computes from the columns the query
    /// selected, which is the query's business rather than the planner's.
    ///
    /// A filter path is the other case and stays strict: {@link PathResolver} needs a column, because
    /// there is no way to put a Java property into a `WHERE` clause.
    private void column(PlanNode node, String property)
    {
        final Field<?> field = types.lookupField(node.getDomainType(), property);
        if (field != null)
        {
            node.addColumn(field, true);
        }
    }


    /// Adds the relation reached by the given field name to the plan.
    private PlanNode relation(PlanNode parent, String fieldName, Set<String> aliases)
    {
        final String domainType = parent.getDomainType();

        final RelationModel forward = types.lookupRelation(domainType, fieldName);
        if (forward != null)
        {
            return add(
                parent,
                fieldName,
                forward,
                forward.getTargetType(),
                forward.getTargetPojoClass(),
                forward.getTargetTable(),
                false,
                false,
                aliases
            );
        }

        final RelationModel backward = types.lookupBackReference(domainType, fieldName);
        if (backward != null)
        {
            return add(
                parent,
                fieldName,
                backward,
                backward.getSourceType(),
                backward.getSourcePojoClass(),
                backward.getSourceTable(),
                true,
                backward.getTargetField() == TargetField.MANY,
                aliases
            );
        }

        final ManyToManyField manyToMany = types.lookupManyToMany(domainType, fieldName);
        if (manyToMany != null)
        {
            return addManyToMany(parent, fieldName, manyToMany, aliases);
        }

        throw new QLiveException(
            "Type '" + domainType + "' has no relation '" + fieldName + "'"
        );
    }


    /// Adds the far end of a many-to-many to the plan, which a statement of its own fetches through the link
    /// table, like any to-many relation.
    private PlanNode addManyToMany(
        PlanNode parent,
        String fieldName,
        ManyToManyField manyToMany,
        Set<String> aliases
    )
    {
        final String candidate = parent.getParent() == null
            ? snakeCase(fieldName)
            : parent.getAlias() + "_" + snakeCase(fieldName);

        final String alias = uniqueAlias(candidate, aliases);

        final PlanNode child = new PlanNode(
            parent,
            fieldName,
            manyToMany,
            alias,
            uniqueAlias(alias + "_link", aliases)
        );

        // what the rows are collected by on the parent's side, selected whether or not anybody asked for it
        parent.addColumn(manyToMany.own().getKeyColumn(), false);

        parent.addChild(child);

        return child;
    }


    private PlanNode add(
        PlanNode parent,
        String fieldName,
        RelationModel relation,
        String domainType,
        Class<?> pojoType,
        Table<?> table,
        boolean backReference,
        boolean toMany,
        Set<String> aliases
    )
    {
        final String candidate = parent.getParent() == null
            ? snakeCase(fieldName)
            : parent.getAlias() + "_" + snakeCase(fieldName);

        final PlanNode child = new PlanNode(
            parent,
            fieldName,
            domainType,
            pojoType,
            table,
            uniqueAlias(candidate, aliases),
            relation,
            backReference,
            toMany
        );

        if (toMany)
        {
            // a to-many relation is fetched by its own query and stitched back onto its parents, so both
            // ends of the foreign key have to come back from their respective queries whether or not
            // anybody selected them
            for (TableField<?, ?> field : relation.getSourceDBFields())
            {
                child.addColumn(field, false);
            }
            for (TableField<?, ?> field : relation.getTargetDBFields())
            {
                parent.addColumn(field, false);
            }
        }

        parent.addChild(child);

        return child;
    }


    // -----------------------------------------------------------------------------------------------------
    // filter and sort paths
    // -----------------------------------------------------------------------------------------------------

    /// Resolves FilterDSL paths against the plan tree.
    ///
    /// In the strict mode a path can only name what the query selects, and the tree is therefore entirely
    /// determined by the GraphQL selection. With `selectByFilter` the path extends the tree instead:
    /// relations it crosses are joined, and the field it ends at is selected.
    private final class PathResolver
        implements FieldResolver
    {
        private final PlanNode root;

        private final boolean extend;

        private final Set<String> aliases;

        /// Nodes the resolved paths reach in the query they are resolved for. A path crossing a to-many
        /// relation is answered by a subquery of its own, so what it touches out here ends at the relation
        /// above it.
        private final Set<PlanNode> touched = Collections.newSetFromMap(new IdentityHashMap<>());


        private PathResolver(PlanNode root, boolean extend, Set<String> aliases)
        {
            this.root = root;
            this.extend = extend;
            this.aliases = aliases;
        }


        @Override
        public ResolvedField resolve(String path)
        {
            final String[] parts = path.split("\\.", -1);

            final List<ExistsScope> scopes = new ArrayList<>();

            PlanNode node = root;
            PlanNode boundary = null;
            for (int i = 0; i < parts.length - 1; i++)
            {
                PlanNode child = node.getChild(parts[i]);
                if (child == null)
                {
                    if (!extend)
                    {
                        throw new QLiveException(
                            "Filter path '" + path + "' follows the relation '" + parts[i] + "' of type '" +
                                node.getDomainType() + "', which the query does not select. Either select " +
                                "it, or allow the query to select by filter."
                        );
                    }
                    child = relation(node, parts[i], aliases);
                }

                if (child.isToMany())
                {
                    scopes.add(child);
                    if (boundary == null)
                    {
                        boundary = child;
                    }
                }
                node = child;
            }

            final String property = parts[parts.length - 1];

            final Field<?> field = types.lookupField(node.getDomainType(), property);
            if (field == null)
            {
                throw new QLiveException(
                    "Filter path '" + path + "': type '" + node.getDomainType() + "' has no database " +
                        "field '" + property + "'"
                );
            }

            if (!extend && !node.isSelected(field.getName()))
            {
                throw new QLiveException(
                    "Filter path '" + path + "' names the field '" + property + "' of type '" +
                        node.getDomainType() + "', which the query does not select. Either select it, or " +
                        "allow the query to select by filter."
                );
            }

            touched.add(boundary == null ? node : boundary.getParent());

            return new ResolvedField(node.addColumn(field, false), scopes);
        }
    }


    // -----------------------------------------------------------------------------------------------------
    // names
    // -----------------------------------------------------------------------------------------------------

    /// Converts an identifier to the database's own spelling of it: `fooId` to `foo_id`, `FooType` to
    /// `foo_type`.
    static String snakeCase(String name)
    {
        return name
            .replaceAll("([a-z0-9])([A-Z])", "$1_$2")
            .replaceAll("([A-Z]+)([A-Z][a-z])", "$1_$2")
            .toLowerCase(Locale.ROOT);
    }


    /// Keeps an alias inside the dialect's identifier limit and unique within the query, counting up where
    /// truncation or two different paths spelling the same name would otherwise collide.
    static String uniqueAlias(String candidate, Set<String> used)
    {
        String alias = truncate(candidate, MAX_ALIAS_LENGTH);

        int counter = 1;
        while (!used.add(alias))
        {
            final String suffix = "_" + (++counter);
            alias = truncate(candidate, MAX_ALIAS_LENGTH - suffix.length()) + suffix;
        }

        return alias;
    }


    private static String truncate(String name, int length)
    {
        return name.length() <= length ? name : name.substring(0, length);
    }
}
