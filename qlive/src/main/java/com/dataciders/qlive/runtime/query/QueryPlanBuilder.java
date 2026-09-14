package com.dataciders.qlive.runtime.query;

import com.dataciders.qlive.model.QueryConfig;
import com.dataciders.qlive.model.condition.CNode;
import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.query.condition.ConditionTransformer;
import com.dataciders.qlive.runtime.query.condition.ExistsScope;
import com.dataciders.qlive.runtime.query.condition.FieldResolver;
import com.dataciders.qlive.runtime.query.condition.ResolvedField;
import com.dataciders.qlive.runtime.scalar.FilterDSL;
import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.TableLookup;
import de.quinscape.domainql.config.RelationModel;
import de.quinscape.domainql.config.TargetField;
import de.quinscape.spring.jsview.util.JSONUtil;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.DataFetchingFieldSelectionSet;
import graphql.schema.SelectedField;
import jakarta.persistence.Column;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.SortField;
import org.jooq.Table;
import org.jooq.TableField;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.svenson.info.JSONPropertyInfo;

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

    private final DomainQL domainQL;


    public QueryPlanBuilder(DomainQL domainQL)
    {
        this.domainQL = domainQL;
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
        final TableLookup lookup = domainQL.lookupType(domainType);

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

        final List<CNode> sortNodes = new ArrayList<>();
        final List<SortField<?>> sortFields = new ArrayList<>();
        sort(root, config, transformer, sortNodes, sortFields);

        final QueryConfig effective = new QueryConfig();
        effective.setCondition(config.getCondition());
        effective.setOffset(config.getOffset());
        effective.setPageSize(config.getPageSize());
        effective.setSortFields(sortNodes);

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
    /// The default is written back into the effective config rather than only into the statement: the
    /// client echoes that config into its next update(), so a document that came back sorted says by what.
    /// It is resolved directly against the root instead of going through the resolver, because the primary
    /// key is one of the columns the plan selects for its own purposes and a client never had to ask for
    /// it.
    private void sort(
        PlanNode root,
        QueryConfig config,
        ConditionTransformer transformer,
        List<CNode> sortNodes,
        List<SortField<?>> sortFields
    )
    {
        if (config.getSortFields() != null && !config.getSortFields().isEmpty())
        {
            for (CNode node : config.getSortFields())
            {
                sortNodes.add(node);
                sortFields.add(transformer.sortField(node));
            }
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
            sortNodes.add(FilterDSL.field(propertyOf(root.getPojoType(), keyField.getName())));
            sortFields.add(keyField.asc());
        }
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
    /// fields the table has no column for, and DomainQL fetches those from the object itself -- there is
    /// nothing for this to select and nothing to fail over, and GraphQL has already established that the
    /// field exists on the type. What such a field computes from, it computes from the columns the query
    /// selected, which is the query's business rather than the planner's.
    ///
    /// A filter path is the other case and stays strict: {@link PathResolver} needs a column, because
    /// there is no way to put a Java property into a `WHERE` clause.
    private void column(PlanNode node, String property)
    {
        final Field<?> field = domainQL.lookupField(node.getDomainType(), property);
        if (field != null)
        {
            node.addColumn(field, true);
        }
    }


    /// Adds the relation reached by the given field name to the plan.
    private PlanNode relation(PlanNode parent, String fieldName, Set<String> aliases)
    {
        final String domainType = parent.getDomainType();

        for (RelationModel relation : domainQL.getRelationModels())
        {
            if (domainType.equals(relation.getSourceType()) && fieldName.equals(relation.getLeftSideObjectName()))
            {
                return add(
                    parent,
                    fieldName,
                    relation,
                    relation.getTargetType(),
                    relation.getTargetPojoClass(),
                    relation.getTargetTable(),
                    false,
                    false,
                    aliases
                );
            }

            if (domainType.equals(relation.getTargetType()) && fieldName.equals(relation.getRightSideObjectName()))
            {
                return add(
                    parent,
                    fieldName,
                    relation,
                    relation.getSourceType(),
                    relation.getSourcePojoClass(),
                    relation.getSourceTable(),
                    true,
                    relation.getTargetField() == TargetField.MANY,
                    aliases
                );
            }
        }

        throw new QLiveException(
            "Type '" + domainType + "' has no relation '" + fieldName + "'"
        );
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

            final Field<?> field = domainQL.lookupField(node.getDomainType(), property);
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

    /// The POJO property a column belongs to, which is the name the FilterDSL and the GraphQL schema use
    /// for it. Read from the same JPA annotations DomainQL builds its own field lookup from.
    static String propertyOf(Class<?> pojoType, String columnName)
    {
        for (JSONPropertyInfo info : JSONUtil.getClassInfo(pojoType).getPropertyInfos())
        {
            final Column column = JSONUtil.findAnnotation(info, Column.class);
            if (column != null && column.name().equals(columnName))
            {
                return info.getJsonName();
            }
        }

        throw new QLiveException(
            "Type '" + pojoType.getSimpleName() + "' has no property for column '" + columnName + "'"
        );
    }


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
