package com.dataciders.qlive.runtime.merge;

import com.dataciders.qlive.model.merge.EntityChange;
import com.dataciders.qlive.model.merge.EntityDeletion;
import com.dataciders.qlive.model.merge.FieldChange;
import com.dataciders.qlive.model.merge.MergeConfig;
import com.dataciders.qlive.model.merge.MergeConflict;
import com.dataciders.qlive.model.merge.MergeConflictField;
import com.dataciders.qlive.model.merge.MergeResult;
import com.dataciders.qlive.model.merge.MergeStatus;
import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.meta.MergeMeta;
import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.TableLookup;
import de.quinscape.domainql.generic.GenericScalar;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import org.jooq.DSLContext;
import org.jooq.DeleteConditionStep;
import org.jooq.Field;
import org.jooq.ForeignKey;
import org.jooq.Name;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.UpdateConditionStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.TransactionAspectSupport;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/// Default {@link MergeService} implementation, writing the application's JOOQ schema.
///
/// The optimistic lock is the whole of the concurrency control and it is one SQL condition: a row of a
/// versioned type is written `WHERE id = ? AND version = <the version it was read at>`. No row is locked,
/// nothing is paid when nobody else is editing, and a write that matches nothing is a write somebody else
/// got in front of.
///
/// One transaction per merge, at repeatable read, so that every statement and every read-back of a conflict
/// sees the same database. Two saves that genuinely overlap in time -- the other one committing while this
/// one runs -- come back as a serialization failure rather than as a conflict; the ordinary case this exists
/// for, where the other person saved and went home, is the one that reads as a conflict.
public class DefaultMergeService
    implements MergeService
{
    private final static Logger log = LoggerFactory.getLogger(DefaultMergeService.class);

    private final DomainQL domainQL;

    private final DSLContext dslContext;

    /// GraphQL type name per JOOQ table, so that a foreign key can be resolved to the type its target rows
    /// are. Built once: the domain does not change after startup.
    private final Map<Name, String> typeNamesByTable;


    public DefaultMergeService(DomainQL domainQL, DSLContext dslContext)
    {
        this.domainQL = domainQL;
        this.dslContext = dslContext;

        final Map<Name, String> byTable = new HashMap<>();
        for (Map.Entry<String, TableLookup> entry : domainQL.getJooqTables().entrySet())
        {
            byTable.put(entry.getValue().getTable().getQualifiedName(), entry.getKey());
        }
        this.typeNamesByTable = Map.copyOf(byTable);
    }


    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.REPEATABLE_READ)
    public MergeResult merge(List<EntityChange> changes, List<EntityDeletion> deletions, MergeConfig config)
    {
        final List<PreparedChange> prepared = new ArrayList<>();
        for (EntityChange change : nullSafe(changes))
        {
            prepared.add(prepare(change));
        }

        final List<MergeConflict> conflicts = new ArrayList<>();

        // A -- new rows before the rows whose foreign keys name them
        for (PreparedChange change : order(prepared))
        {
            // B and C -- write, and ask what stood in the way where nothing was written
            final MergeConflict conflict = write(change, config);
            if (conflict != null)
            {
                conflicts.add(conflict);
            }
        }

        // deletions after the changes, so that a row whose last reference this merge moves away can go in
        // the same merge that moved it
        for (EntityDeletion deletion : nullSafe(deletions))
        {
            final MergeConflict conflict = delete(deletion);
            if (conflict != null)
            {
                conflicts.add(conflict);
            }
        }

        // E -- commit, or roll back and hand the conflicts to whoever has to decide about them
        final MergeResult result = new MergeResult();
        if (conflicts.isEmpty())
        {
            result.setStatus(MergeStatus.DONE);
            result.setConflicts(List.of());
        }
        else
        {
            log.debug("Merge conflicts: {}", conflicts);

            TransactionAspectSupport.currentTransactionStatus().setRollbackOnly();
            result.setStatus(MergeStatus.CONFLICT);
            result.setConflicts(conflicts);
        }

        return result;
    }


    @Override
    public void ensureNotVersioned(Table<?> table)
    {
        final String typeName = typeNamesByTable.get(table.getQualifiedName());

        if (typeName != null && MergeMeta.isVersioned(domainQL, typeName))
        {
            throw new QLiveException(
                "Table " + table.getQualifiedName() + " backs the versioned type '" + typeName + "' and " +
                    "must be written through the merge service. A direct write leaves the version column " +
                    "saying the row is in a state it is not in, and the next merge against that version " +
                    "overwrites this write without noticing."
            );
        }
    }


    /// One change with everything the write needs already resolved: which table, which columns, and which of
    /// its fields could ever be named in a conflict.
    private static final class PreparedChange
    {
        private final EntityChange change;

        private final String typeName;

        private final Table<?> table;

        private final Field<?> idField;

        /// The version column, or null where the type carries none and is written last-write-wins.
        private final Field<?> versionField;

        /// Column per changed field, in the order the change named them.
        private final Map<Field<?>, Object> values = new LinkedHashMap<>();

        /// The changed fields a conflict may name, i.e. all of them but the ignored ones, by GraphQL name.
        private final Map<String, GenericScalar> conflictFields = new LinkedHashMap<>();

        /// Column per conflict field, for reading the stored value back off the row.
        private final Map<String, Field<?>> conflictColumns = new LinkedHashMap<>();


        private PreparedChange(
            EntityChange change,
            String typeName,
            Table<?> table,
            Field<?> idField,
            Field<?> versionField
        )
        {
            this.change = change;
            this.typeName = typeName;
            this.table = table;
            this.idField = idField;
            this.versionField = versionField;
        }
    }


    /// Resolves one change against the domain, and refuses everything that could only be a mistake: an
    /// unknown type, a field that names no column, a row of a versioned type with no base version to be
    /// checked against.
    private PreparedChange prepare(EntityChange change)
    {
        final String typeName = requireType(change.getType(), change);
        final Table<?> table = domainQL.lookupType(typeName).getTable();
        final Field<?> idField = idField(typeName, table);
        final Field<?> versionField = versionField(typeName);

        if (change.getId() == null || change.getId().isEmpty())
        {
            throw new QLiveException("No id in " + change + ". A change names the row it changes, and a " +
                "new row is named by the id the client generated for it.");
        }

        if (versionField != null && !change.isNew() && change.getVersion() == null)
        {
            throw new QLiveException(
                "No base version in " + change + ". '" + typeName + "' is versioned, so a change to one of " +
                    "its rows can only be written against the version the row was read at -- select '" +
                    MergeMeta.VERSION + "' in the query the row came from."
            );
        }

        final PreparedChange prepared = new PreparedChange(change, typeName, table, idField, versionField);
        final List<String> ignored = MergeMeta.ignoredFields(domainQL, typeName);

        for (FieldChange fieldChange : nullSafe(change.getChanges()))
        {
            final String name = fieldChange.getField();
            final Field<?> column = requireField(typeName, name, change);
            final GenericScalar scalar = fieldChange.getValue();
            final Object value = scalar == null ? null : scalar.getValue();

            if (column.equals(versionField))
            {
                throw new QLiveException(
                    "Field '" + name + "' of " + change + " is the version, which the merge writes and " +
                        "nobody else does."
                );
            }

            if (column.equals(idField))
            {
                if (!change.getId().equals(value))
                {
                    throw new QLiveException(
                        "Field '" + name + "' of " + change + " is the id and disagrees with the id of the " +
                            "change itself. A row does not change its identity."
                    );
                }
                continue;
            }

            prepared.values.put(column, value);

            if (!ignored.contains(name))
            {
                prepared.conflictFields.put(name, scalar);
                prepared.conflictColumns.put(name, column);
            }
        }

        return prepared;
    }


    /// A -- the changes in an order in which every foreign key has something to point at.
    ///
    /// The client generates the ids of new rows, so a change may name a row no other statement has created
    /// yet. What decides the order is only that: a change referring to a row this same merge creates goes
    /// after the change creating it, and everything else keeps the order it arrived in.
    private List<PreparedChange> order(List<PreparedChange> changes)
    {
        final Map<String, PreparedChange> newRows = new HashMap<>();
        for (PreparedChange change : changes)
        {
            if (change.change.isNew())
            {
                newRows.put(rowKey(change.typeName, change.change.getId()), change);
            }
        }

        if (newRows.isEmpty())
        {
            return changes;
        }

        final List<PreparedChange> ordered = new ArrayList<>(changes.size());
        final Set<PreparedChange> done = new LinkedHashSet<>();
        final Set<PreparedChange> onPath = new LinkedHashSet<>();

        for (PreparedChange change : changes)
        {
            insert(change, newRows, ordered, done, onPath);
        }

        return ordered;
    }


    private void insert(
        PreparedChange change,
        Map<String, PreparedChange> newRows,
        List<PreparedChange> ordered,
        Set<PreparedChange> done,
        Set<PreparedChange> onPath
    )
    {
        if (done.contains(change))
        {
            return;
        }

        if (!onPath.add(change))
        {
            throw new QLiveException(
                "New rows referring to each other in a circle: " +
                    onPath.stream().map(c -> c.typeName + " " + c.change.getId()).toList() +
                    ". One of them has to be written first, and none of them can be."
            );
        }

        for (PreparedChange referenced : referencedNewRows(change, newRows))
        {
            insert(referenced, newRows, ordered, done, onPath);
        }

        onPath.remove(change);
        done.add(change);
        ordered.add(change);
    }


    /// The new rows this change's foreign keys point at, itself excluded -- a row of a self-referencing type
    /// pointing at itself is written by one statement and needs no ordering.
    private List<PreparedChange> referencedNewRows(PreparedChange change, Map<String, PreparedChange> newRows)
    {
        final Map<Name, String> targets = foreignKeyTargets(change.table);
        if (targets.isEmpty())
        {
            return List.of();
        }

        final List<PreparedChange> referenced = new ArrayList<>();

        for (Map.Entry<Field<?>, Object> value : change.values.entrySet())
        {
            final String targetType = targets.get(value.getKey().getQualifiedName());

            if (targetType != null && value.getValue() instanceof String id)
            {
                final PreparedChange target = newRows.get(rowKey(targetType, id));

                if (target != null && target != change)
                {
                    referenced.add(target);
                }
            }
        }

        return referenced;
    }


    /// Type name per single-column foreign key of the given table. A composite key is left out: a change
    /// names a row by one id, so a reference this merge could satisfy is one column wide.
    private Map<Name, String> foreignKeyTargets(Table<?> table)
    {
        final Map<Name, String> targets = new HashMap<>();

        for (ForeignKey<?, ?> foreignKey : table.getReferences())
        {
            final List<? extends TableField<?, ?>> fields = foreignKey.getFields();
            final String targetType = typeNamesByTable.get(foreignKey.getKey().getTable().getQualifiedName());

            if (fields.size() == 1 && targetType != null)
            {
                targets.put(fields.get(0).getQualifiedName(), targetType);
            }
        }

        return targets;
    }


    /// B -- one INSERT or one UPDATE, and C -- what stood in the way where it wrote nothing.
    private MergeConflict write(PreparedChange change, MergeConfig config)
    {
        final Map<Field<?>, Object> values = new LinkedHashMap<>(change.values);

        if (change.versionField != null)
        {
            values.put(change.versionField, UUID.randomUUID().toString());
        }

        final int count;

        if (change.change.isNew())
        {
            values.put(change.idField, change.change.getId());

            // ON CONFLICT on the primary key alone, so that a row that is already there reads as a conflict
            // rather than as an aborted transaction -- and so that every other constraint the row breaks
            // still fails loudly, which is what it is for
            count = dslContext.insertInto(change.table)
                .set(values)
                .onConflict(change.idField)
                .doNothing()
                .execute();
        }
        else if (values.isEmpty())
        {
            // a change that changed nothing. Bumping the version for it would invalidate everybody else's
            // base for no write at all
            return null;
        }
        else
        {
            UpdateConditionStep<?> update = dslContext.update(change.table)
                .set(values)
                .where(any(change.idField).eq(change.change.getId()));

            if (change.versionField != null)
            {
                update = update.and(any(change.versionField).eq(change.change.getVersion()));
            }

            count = update.execute();
        }

        return count == 0 ? conflict(change, config) : null;
    }


    /// C -- the row as it stands now, and the fields of this change that clash with it.
    ///
    /// Every field the change touched is named, because without a record of what the other write touched
    /// there is no way to tell which of them it was. That is the conservative answer and the only one
    /// available here: a field named that did not really move costs the user a glance, a field not named
    /// that did would cost them the other person's work.
    private MergeConflict conflict(PreparedChange change, MergeConfig config)
    {
        final MergeConflict conflict = new MergeConflict();
        conflict.setType(change.typeName);
        conflict.setId(change.change.getId());
        conflict.setFields(List.of());

        final Record stored = read(change.table, change.idField, change.change.getId());

        if (stored == null)
        {
            conflict.setDeleted(true);
            return conflict;
        }

        if (change.versionField != null)
        {
            conflict.setVersion((String) stored.get(change.versionField));
        }

        final boolean withValues =
            config != null && config.isResolveConflicts() &&
                MergeMeta.resolvesConflicts(domainQL, change.typeName);

        final List<MergeConflictField> fields = new ArrayList<>();

        for (Map.Entry<String, GenericScalar> entry : change.conflictFields.entrySet())
        {
            final MergeConflictField field = new MergeConflictField();
            field.setField(entry.getKey());

            if (withValues)
            {
                field.setMine(entry.getValue());
                field.setStored(
                    storedValue(change.typeName, entry.getKey(), change.conflictColumns.get(entry.getKey()), stored)
                );
            }

            fields.add(field);
        }

        conflict.setFields(fields);

        return conflict;
    }


    /// The stored value of one field as a GenericScalar, or null where the field's GraphQL type is no scalar
    /// and there is therefore nothing to name it by.
    private GenericScalar storedValue(String typeName, String fieldName, Field<?> column, Record stored)
    {
        final GraphQLObjectType type = (GraphQLObjectType) domainQL.getGraphQLSchema().getType(typeName);
        final GraphQLType fieldType =
            GraphQLTypeUtil.unwrapNonNull(type.getFieldDefinition(fieldName).getType());

        return fieldType instanceof GraphQLScalarType scalar
            ? new GenericScalar(scalar.getName(), stored.get(column))
            : null;
    }


    /// One DELETE under the same optimistic lock, and the same reading back where it removed nothing.
    private MergeConflict delete(EntityDeletion deletion)
    {
        final String typeName = requireType(deletion.getType(), deletion);
        final Table<?> table = domainQL.lookupType(typeName).getTable();
        final Field<?> idField = idField(typeName, table);
        final Field<?> versionField = versionField(typeName);

        if (deletion.getId() == null || deletion.getId().isEmpty())
        {
            throw new QLiveException("No id in " + deletion + ". A deletion names the row it removes.");
        }

        if (versionField != null && deletion.getVersion() == null)
        {
            throw new QLiveException(
                "No version in " + deletion + ". '" + typeName + "' is versioned, so one of its rows can " +
                    "only be removed as the row that was read -- select '" + MergeMeta.VERSION + "' in the " +
                    "query the row came from."
            );
        }

        DeleteConditionStep<?> delete = dslContext.deleteFrom(table)
            .where(any(idField).eq(deletion.getId()));

        if (versionField != null)
        {
            delete = delete.and(any(versionField).eq(deletion.getVersion()));
        }

        if (delete.execute() != 0)
        {
            return null;
        }

        final MergeConflict conflict = new MergeConflict();
        conflict.setType(typeName);
        conflict.setId(deletion.getId());

        // a deletion touches no fields, so there is nothing to choose between either way
        conflict.setFields(List.of());

        final Record stored = read(table, idField, deletion.getId());

        if (stored == null)
        {
            conflict.setDeleted(true);
        }
        else if (versionField != null)
        {
            conflict.setVersion((String) stored.get(versionField));
        }

        return conflict;
    }


    private Record read(Table<?> table, Field<?> idField, String id)
    {
        return dslContext.selectFrom(table)
            .where(any(idField).eq(id))
            .fetchOne();
    }


    private String requireType(String typeName, Object of)
    {
        if (typeName == null || !domainQL.getJooqTables().containsKey(typeName))
        {
            throw new QLiveException(
                "No type '" + typeName + "' in " + of + ". The merge writes the tables the domain exposes, " +
                    "under the names it exposes them under."
            );
        }

        return typeName;
    }


    private Field<?> requireField(String typeName, String fieldName, Object of)
    {
        final Field<?> field = fieldName == null ? null : domainQL.lookupField(typeName, fieldName);

        if (field == null)
        {
            throw new QLiveException(
                "Field '" + fieldName + "' of " + of + " is no column of '" + typeName + "'. A change " +
                    "writes columns; a relation is changed by writing the foreign key it is fetched by."
            );
        }

        return field;
    }


    /// The single-column primary key of the type's table. A merge names a row by one id -- it is what a
    /// change carries, what a version record would name and what the client generates for a new row -- so a
    /// composite key is not a case with a worse answer, it is one with none.
    private static Field<?> idField(String typeName, Table<?> table)
    {
        final UniqueKey<?> primaryKey = table.getPrimaryKey();

        if (primaryKey == null || primaryKey.getFields().size() != 1)
        {
            throw new QLiveException(
                "Type '" + typeName + "' has no single-column primary key and cannot be written through the " +
                    "merge, which names every row by one id."
            );
        }

        return primaryKey.getFields().get(0);
    }


    /// The version column of the type, or null where the type carries none. Nothing declares this: having
    /// the field is what taking part means.
    private Field<?> versionField(String typeName)
    {
        return MergeMeta.isVersioned(domainQL, typeName)
            ? domainQL.lookupField(typeName, MergeMeta.VERSION)
            : null;
    }


    private static String rowKey(String typeName, String id)
    {
        return typeName + "/" + id;
    }


    @SuppressWarnings("unchecked")
    private static Field<Object> any(Field<?> field)
    {
        return (Field<Object>) field;
    }


    private static <T> List<T> nullSafe(List<T> list)
    {
        return list == null ? List.of() : list;
    }
}
