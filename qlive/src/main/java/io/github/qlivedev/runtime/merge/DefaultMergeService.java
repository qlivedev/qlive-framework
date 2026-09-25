package io.github.qlivedev.runtime.merge;

import io.github.qlivedev.model.merge.EntityChange;
import io.github.qlivedev.model.merge.EntityDeletion;
import io.github.qlivedev.model.merge.FieldChange;
import io.github.qlivedev.model.merge.MergeConfig;
import io.github.qlivedev.model.merge.MergeConflict;
import io.github.qlivedev.model.merge.MergeConflictField;
import io.github.qlivedev.model.merge.MergeResult;
import io.github.qlivedev.model.merge.MergeStatus;
import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.runtime.auth.AppAuthentication;
import io.github.qlivedev.runtime.meta.MergeMeta;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.TableLookup;
import io.github.qlivedev.graphql.TypeRegistry;
import io.github.qlivedev.graphql.generic.GenericScalar;
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

import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collections;
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
///
/// A write that matched nothing is not yet a conflict. Every write records which fields it touched, so the
/// chain of records between the version we read and the version that is there now says which fields the
/// other writes touched -- and where that set does not meet ours, both edits belong in the row and the write
/// is simply made again against what they left behind. That is the common case and the reason for all of it;
/// a real conflict is the one where the two sets meet.
public class DefaultMergeService
    implements MergeService
{
    private final static Logger log = LoggerFactory.getLogger(DefaultMergeService.class);

    /// SQL state of a row refused for being a duplicate. Standard, so no dialect has to be asked.
    private final static String UNIQUE_VIOLATION = "23505";

    private final QLiveDomain domain;

    private final TypeRegistry types;

    private final DSLContext dslContext;

    private final FieldLayoutService fieldLayouts;

    private final VersionService versions;

    /// GraphQL type name per JOOQ table, so that a foreign key can be resolved to the type its target rows
    /// are. Built once: the domain does not change after startup.
    private final Map<Name, String> typeNamesByTable;


    /// A chain longer than this is not a chain anybody can usefully diff, and walking it would be the merge
    /// paying for somebody else's write storm. Reading past it is refused the same way an unknown layout is:
    /// assume every field changed.
    private final static int MAX_CHAIN_LENGTH = 1000;


    public DefaultMergeService(
        QLiveDomain domain,
        DSLContext dslContext,
        FieldLayoutService fieldLayouts,
        VersionService versions
    )
    {
        this.domain = domain;
        this.types = domain.getTypeRegistry();
        this.dslContext = dslContext;
        this.fieldLayouts = fieldLayouts;
        this.versions = versions;

        final Map<Name, String> byTable = new HashMap<>();
        for (Map.Entry<String, TableLookup> entry : this.types.getJooqTables().entrySet())
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
        final List<EntityVersion> written = new ArrayList<>();

        boolean aborted = false;

        // A -- new rows before the rows whose foreign keys name them
        for (PreparedChange change : order(prepared))
        {
            final MergeConflict conflict;

            try
            {
                // B and C -- write, and ask what stood in the way where nothing was written
                conflict = write(change, config, written);
            }
            catch (RuntimeException e)
            {
                if (!uniqueViolation(e))
                {
                    throw e;
                }

                // The database has aborted the transaction, so nothing more can be written and nothing can
                // be read back. This conflict is the whole answer.
                conflicts.add(duplicate(change));
                aborted = true;
                break;
            }

            if (conflict != null)
            {
                conflicts.add(conflict);
            }
        }

        // deletions after the changes, so that a row whose last reference this merge clears can go in
        // the same merge that cleared it
        for (EntityDeletion deletion : aborted ? List.<EntityDeletion>of() : nullSafe(deletions))
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
            // the records go in with the rows they describe, so that a row and the account of how it got
            // there commit together or not at all
            versions.write(written);

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

        if (typeName != null && MergeMeta.isVersioned(domain, typeName))
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
        final Table<?> table = types.lookupType(typeName).getTable();
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
        final List<String> ignored = MergeMeta.ignoredFields(domain, typeName);

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
    private MergeConflict write(PreparedChange change, MergeConfig config, List<EntityVersion> written)
    {
        if (!change.change.isNew() && change.values.isEmpty())
        {
            // a change that changed nothing. Bumping the version for it would invalidate everybody else's
            // base for no write at all
            return null;
        }

        final String newVersion = change.versionField == null ? null : UUID.randomUUID().toString();

        final Map<Field<?>, Object> values = new LinkedHashMap<>(change.values);

        if (newVersion != null)
        {
            values.put(change.versionField, newVersion);
        }

        if (change.change.isNew())
        {
            values.put(change.idField, change.change.getId());

            // ON CONFLICT on the primary key alone, so that a row that is already there reads as a conflict
            // rather than as an aborted transaction -- and so that every other constraint the row breaks
            // still fails loudly, which is what it is for
            final int count = dslContext.insertInto(change.table)
                .set(values)
                .onConflict(change.idField)
                .doNothing()
                .execute();

            if (count != 0)
            {
                record(change, newVersion, null, written);
                return null;
            }
        }
        else if (update(change, values, change.change.getVersion()) != 0)
        {
            record(change, newVersion, change.change.getVersion(), written);
            return null;
        }

        return resolve(change, config, values, newVersion, written);
    }


    /// One UPDATE of the change's row, held to the given version where the type carries one.
    private int update(PreparedChange change, Map<Field<?>, Object> values, String base)
    {
        UpdateConditionStep<?> update = dslContext.update(change.table)
            .set(values)
            .where(any(change.idField).eq(change.change.getId()));

        if (change.versionField != null)
        {
            update = update.and(any(change.versionField).eq(base));
        }

        return update.execute();
    }


    /// C -- nothing was written, so somebody else was here. What they touched decides whether that is a
    /// conflict at all.
    ///
    /// Where their fields and ours do not meet, both edits belong in the row and the only thing wrong with
    /// the write was the base it named. It is made again against the version they left behind, and the
    /// record of it names that version as its predecessor, so the chain stays a chain.
    private MergeConflict resolve(
        PreparedChange change,
        MergeConfig config,
        Map<Field<?>, Object> values,
        String newVersion,
        List<EntityVersion> written
    )
    {
        final Record stored = read(change.table, change.idField, change.change.getId());

        if (stored == null)
        {
            // nothing to merge into and nothing to choose between
            final MergeConflict conflict = new MergeConflict();
            conflict.setType(change.typeName);
            conflict.setId(change.change.getId());
            conflict.setDeleted(true);
            conflict.setFields(List.of());

            return conflict;
        }

        final String storedVersion =
            change.versionField == null ? null : (String) stored.get(change.versionField);

        final Set<String> theirs = theirFields(change, storedVersion);

        final boolean disjoint = theirs != null && Collections.disjoint(theirs, change.conflictFields.keySet());

        if (disjoint && !change.change.isNew() && MergeMeta.isAutoMerge(domain, change.typeName) &&
            update(change, values, storedVersion) != 0)
        {
            log.debug(
                "Merged {} {} over a change to {}", change.typeName, change.change.getId(), theirs
            );

            record(change, newVersion, storedVersion, written);
            return null;
        }

        return conflict(change, config, stored, storedVersion, theirs);
    }


    /// The fields the writes between our base version and the version that is there now touched, or null
    /// where that cannot be told and every field has to be assumed.
    ///
    /// The walk goes backwards, each record naming the version it was made against, until it meets the
    /// version we read. It gives up rather than guesses in every case where it cannot get there: a record
    /// that was pruned, a chain that runs out before our base -- which is what a version written by
    /// something other than this merge looks like -- a field layout that is no longer stored, and a chain
    /// too long to be worth walking.
    private Set<String> theirFields(PreparedChange change, String storedVersion)
    {
        final String base = change.change.getVersion();

        if (change.versionField == null || base == null)
        {
            return null;
        }

        final Set<String> names = new LinkedHashSet<>();

        String at = storedVersion;

        for (int hops = 0; at != null && !at.equals(base); hops++)
        {
            if (hops == MAX_CHAIN_LENGTH)
            {
                log.warn(
                    "More than {} versions of {} {} since {}",
                    MAX_CHAIN_LENGTH, change.typeName, change.change.getId(), base
                );
                return null;
            }

            final EntityVersion record = versions.get(at);

            if (record == null)
            {
                return null;
            }

            final Set<String> fields = fieldLayouts.fields(
                record.getFieldLayout(), change.typeName, record.getFieldMask()
            );

            if (fields == null)
            {
                return null;
            }

            names.addAll(fields);
            at = record.getPrev();
        }

        return at == null ? null : names;
    }


    /// The record of one write, held back until the whole merge succeeds.
    ///
    /// An unversioned type records nothing. It has no column to hold a version, so nothing would ever name
    /// the record and nothing could read it back.
    private void record(PreparedChange change, String newVersion, String prev, List<EntityVersion> written)
    {
        if (newVersion == null)
        {
            return;
        }

        final FieldLayout layout = fieldLayouts.current(change.typeName);

        written.add(
            new EntityVersion(
                newVersion,
                change.typeName,
                change.change.getId(),
                prev,
                layout.mask(change.conflictFields.keySet()),
                layout.getId(),
                AppAuthentication.current().getId(),
                new Timestamp(System.currentTimeMillis())
            )
        );
    }


    /// The row as it stands now and the fields to say something about: the ones both writes touched, which
    /// are the decision, and the ones only theirs touched, which are attached to be seen.
    ///
    /// Where there is no telling what the other write touched, every field this change touched is named.
    /// That is the conservative answer and the only one available: a field named that did not really move
    /// costs the user a glance, a field not named that did would cost them the other person's work.
    private MergeConflict conflict(
        PreparedChange change,
        MergeConfig config,
        Record stored,
        String storedVersion,
        Set<String> theirs
    )
    {
        final MergeConflict conflict = new MergeConflict();
        conflict.setType(change.typeName);
        conflict.setId(change.change.getId());
        conflict.setStoredVersion(storedVersion);

        final boolean withValues =
            config != null && config.isConflictValues() &&
                MergeMeta.resolvesConflicts(domain, change.typeName);

        final List<MergeConflictField> fields = new ArrayList<>();

        for (Map.Entry<String, GenericScalar> entry : change.conflictFields.entrySet())
        {
            if (theirs != null && !theirs.contains(entry.getKey()))
            {
                continue;
            }

            final MergeConflictField field = new MergeConflictField();
            field.setField(entry.getKey());

            if (withValues)
            {
                field.setMine(entry.getValue());
                field.setStored(
                    storedValue(
                        change.typeName, entry.getKey(), change.conflictColumns.get(entry.getKey()), stored
                    )
                );
            }

            fields.add(field);
        }

        if (theirs != null)
        {
            for (String name : theirs)
            {
                final Field<?> column =
                    change.conflictFields.containsKey(name) ? null : types.lookupField(change.typeName, name);

                if (column == null)
                {
                    continue;
                }

                final MergeConflictField field = new MergeConflictField();
                field.setField(name);
                field.setInformational(true);

                if (withValues)
                {
                    field.setStored(storedValue(change.typeName, name, column, stored));
                }

                fields.add(field);
            }
        }

        conflict.setFields(fields);

        return conflict;
    }


    /// The stored value of one field as a GenericScalar, or null where the field's GraphQL type is no scalar
    /// and there is therefore nothing to name it by.
    private GenericScalar storedValue(String typeName, String fieldName, Field<?> column, Record stored)
    {
        final GraphQLObjectType type = (GraphQLObjectType) domain.getGraphQLSchema().getType(typeName);
        final GraphQLType fieldType =
            GraphQLTypeUtil.unwrapNonNull(type.getFieldDefinition(fieldName).getType());

        return fieldType instanceof GraphQLScalarType scalar
            ? new GenericScalar(scalar.getName(), stored.get(column))
            : null;
    }


    /// One DELETE under the same optimistic lock, and the same reading back where it removed nothing.
    ///
    /// No version record, and there could be none that anything would read: the record would describe a row
    /// that is gone, and nothing is left to name it. A deletion is also the one write that never merges over
    /// a concurrent change -- removing a row somebody has just edited is exactly the decision a user has to
    /// be asked about -- so it names no fields either way.
    /// The conflict a row that broke a unique constraint comes back as.
    ///
    /// No fields and no stored version: the transaction is aborted, so there is nothing left to read, and
    /// there would be nothing to choose between anyway. The row in the way is somebody else's and this one
    /// was never written.
    ///
    /// Which row is in the way is not said either, and cannot be from here -- the constraint names columns,
    /// not a row. What the client does with that is its own: a link insert that broke the constraint on the
    /// pair means the association it wanted exists, and the array it came out of is where that is shown.
    private MergeConflict duplicate(PreparedChange change)
    {
        final MergeConflict conflict = new MergeConflict();
        conflict.setType(change.change.getType());
        conflict.setId(change.change.getId());
        conflict.setFields(List.of());

        return conflict;
    }


    /// Whether the given exception is a row refused for being a duplicate of one that is already there.
    ///
    /// By SQL state and through the whole cause chain rather than by exception type: JOOQ throws its own
    /// DataAccessException where nothing installed Spring's translator and the application sees a
    /// DuplicateKeyException where something did, and an application is free to do either.
    private static boolean uniqueViolation(Throwable e)
    {
        for (Throwable cause = e; cause != null; cause = cause == cause.getCause() ? null : cause.getCause())
        {
            if (cause instanceof SQLException sql && UNIQUE_VIOLATION.equals(sql.getSQLState()))
            {
                return true;
            }
        }

        return false;
    }


    private MergeConflict delete(EntityDeletion deletion)
    {
        final String typeName = requireType(deletion.getType(), deletion);
        final Table<?> table = types.lookupType(typeName).getTable();
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
            conflict.setStoredVersion((String) stored.get(versionField));
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
        if (typeName == null || !types.getJooqTables().containsKey(typeName))
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
        final Field<?> field = fieldName == null ? null : types.lookupField(typeName, fieldName);

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
        return MergeMeta.isVersioned(domain, typeName)
            ? types.lookupField(typeName, MergeMeta.VERSION)
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
