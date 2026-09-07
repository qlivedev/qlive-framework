package com.dataciders.qlive.runtime.query;

import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.query.condition.ExistsScope;
import de.quinscape.domainql.config.RelationModel;
import org.jooq.Condition;
import org.jooq.Field;
import org.jooq.Record1;
import org.jooq.SelectJoinStep;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/// One domain type within a query plan: the root type of the document, or a relation reached from it.
///
/// A node owns its table alias, so everything that names a column of it -- the select list, the join
/// condition, a filter path -- goes through the same aliased table and cannot accidentally refer to another
/// occurrence of the same table elsewhere in the query.
public class PlanNode
    implements ExistsScope
{
    private final PlanNode parent;

    /// Relation field name this node is reached by on its parent, `null` for the root.
    private final String fieldName;

    private final String domainType;

    private final Class<?> pojoType;

    private final Table<?> table;

    private final String alias;

    private final RelationModel relation;

    /// Whether the parent is the relation's target, i.e. this node is reached by following a foreign key
    /// backwards. Decides which side of the relation each of its field lists describes.
    private final boolean backReference;

    private final boolean toMany;

    private final int depth;

    /// Columns to select, by database column name.
    private final Map<String, Field<?>> columns = new LinkedHashMap<>();

    /// Of those, the ones the GraphQL query actually selected -- as opposed to the keys the plan adds for
    /// its own bookkeeping. Only that subset satisfies a filter path in the strict mode.
    private final Set<String> selected = new LinkedHashSet<>();

    private final Map<String, PlanNode> children = new LinkedHashMap<>();

    /// The primary key on this node's alias. Selected whether the query asked for it or not: it is what
    /// says whether a left joined row exists at all, and what a to-many relation below this node is
    /// collected by.
    private final List<Field<?>> keyFields = new ArrayList<>();


    PlanNode(
        PlanNode parent,
        String fieldName,
        String domainType,
        Class<?> pojoType,
        Table<?> table,
        String alias,
        RelationModel relation,
        boolean backReference,
        boolean toMany
    )
    {
        this.parent = parent;
        this.fieldName = fieldName;
        this.domainType = domainType;
        this.pojoType = pojoType;
        this.table = table.as(alias);
        this.alias = alias;
        this.relation = relation;
        this.backReference = backReference;
        this.toMany = toMany;
        this.depth = parent == null ? 0 : parent.depth + 1;

        final UniqueKey<?> primaryKey = table.getPrimaryKey();
        if (primaryKey != null)
        {
            for (Field<?> field : primaryKey.getFields())
            {
                keyFields.add(addColumn(field, false));
            }
        }
    }


    /// The primary key columns on this node's alias, empty for a table that has none.
    public List<Field<?>> getKeyFields()
    {
        return keyFields;
    }


    public PlanNode getParent()
    {
        return parent;
    }


    /// Name of the relation field this node is reached by, which is also the name the fetcher context has
    /// to hold it under.
    public String getFieldName()
    {
        return fieldName;
    }


    public String getDomainType()
    {
        return domainType;
    }


    public Class<?> getPojoType()
    {
        return pojoType;
    }


    /// The aliased table.
    public Table<?> getTable()
    {
        return table;
    }


    public String getAlias()
    {
        return alias;
    }


    public RelationModel getRelation()
    {
        return relation;
    }


    public boolean isBackReference()
    {
        return backReference;
    }


    public boolean isToMany()
    {
        return toMany;
    }


    @Override
    public int depth()
    {
        return depth;
    }


    public Collection<Field<?>> getColumns()
    {
        return columns.values();
    }


    public Field<?> getColumn(String columnName)
    {
        return columns.get(columnName);
    }


    public boolean isSelected(String columnName)
    {
        return selected.contains(columnName);
    }


    /// Adds a column to this node's select list.
    ///
    /// @param field            column of the unaliased table
    /// @param fromSelection    whether the GraphQL query asked for it, as opposed to the plan needing it
    ///
    /// @return the column on this node's alias
    Field<?> addColumn(Field<?> field, boolean fromSelection)
    {
        final String name = field.getName();
        if (fromSelection)
        {
            selected.add(name);
        }
        return columns.computeIfAbsent(name, n -> aliased(table, field));
    }


    public Collection<PlanNode> getChildren()
    {
        return children.values();
    }


    public PlanNode getChild(String fieldName)
    {
        return children.get(fieldName);
    }


    void addChild(PlanNode child)
    {
        children.put(child.getFieldName(), child);
    }


    /// The to-one relations reachable from this node without crossing a to-many relation, in pre-order.
    ///
    /// These are exactly the nodes that can be joined into one query with this one: a to-many relation
    /// would multiply rows, so it ends the walk and is fetched separately.
    public List<PlanNode> joinedDescendants()
    {
        final List<PlanNode> descendants = new ArrayList<>();
        collectJoined(this, descendants);
        return descendants;
    }


    private static void collectJoined(PlanNode node, List<PlanNode> descendants)
    {
        for (PlanNode child : node.getChildren())
        {
            if (child.isToMany())
            {
                continue;
            }
            descendants.add(child);
            collectJoined(child, descendants);
        }
    }


    /// The to-many relations below this node that are not below another to-many relation, i.e. the ones
    /// the query this node belongs to has to spawn a follow-up query for.
    public List<PlanNode> toManyChildren()
    {
        final List<PlanNode> toManyChildren = new ArrayList<>();
        collectToMany(this, toManyChildren);
        return toManyChildren;
    }


    private static void collectToMany(PlanNode node, List<PlanNode> found)
    {
        for (PlanNode child : node.getChildren())
        {
            if (child.isToMany())
            {
                found.add(child);
            }
            else
            {
                collectToMany(child, found);
            }
        }
    }


    /// The condition tying this node to its parent, on both aliases.
    public Condition joinCondition()
    {
        if (relation == null)
        {
            throw new QLiveException("The root of a query plan has no join condition");
        }

        final List<? extends TableField<?, ?>> sourceDBFields = relation.getSourceDBFields();
        final List<? extends TableField<?, ?>> targetDBFields = relation.getTargetDBFields();

        // the relation's source side is the one holding the foreign key, which is this node when we
        // followed that key backwards and the parent when we followed it forwards
        final Table<?> sourceTable = backReference ? table : parent.table;
        final Table<?> targetTable = backReference ? parent.table : table;

        final List<Condition> conditions = new ArrayList<>(sourceDBFields.size());
        for (int i = 0; i < sourceDBFields.size(); i++)
        {
            conditions.add(
                equal(
                    aliased(sourceTable, sourceDBFields.get(i)),
                    aliased(targetTable, targetDBFields.get(i))
                )
            );
        }
        return DSL.and(conditions);
    }


    /// Builds the correlated `EXISTS` for a filter path that reaches through this to-many relation.
    ///
    /// The subquery selects from this node's table and joins everything below it that can be joined, which
    /// is a superset of what the condition needs and costs nothing: they are all to-one left joins, so none
    /// of them changes what the subquery finds.
    @Override
    public Condition wrap(Condition inner)
    {
        if (!toMany)
        {
            throw new QLiveException("'" + alias + "' is not a to-many relation");
        }

        SelectJoinStep<Record1<Integer>> select = DSL.select(DSL.inline(1)).from(table);
        for (PlanNode descendant : joinedDescendants())
        {
            select = select.leftJoin(descendant.getTable()).on(descendant.joinCondition());
        }

        return DSL.exists(
            select.where(joinCondition(), inner)
        );
    }


    @SuppressWarnings("unchecked")
    private static Condition equal(Field<?> left, Field<?> right)
    {
        return ((Field<Object>) left).eq((Field<Object>) right);
    }


    /// Looks a column up on an aliased table.
    static Field<?> aliased(Table<?> table, Field<?> field)
    {
        final Field<?> aliasedField = table.field(field);
        if (aliasedField == null)
        {
            throw new QLiveException("Table '" + table.getName() + "' has no field '" + field.getName() + "'");
        }
        return aliasedField;
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "alias = '" + alias + '\''
            + ", domainType = '" + domainType + '\''
            + ", toMany = " + toMany
            + ", columns = " + columns.keySet()
            ;
    }
}
