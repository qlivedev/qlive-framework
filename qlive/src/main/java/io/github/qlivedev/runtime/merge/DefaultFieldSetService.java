package io.github.qlivedev.runtime.merge;

import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.runtime.meta.MergeMeta;
import io.github.qlivedev.graphql.QLiveDomain;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// Default {@link FieldSetService}, keeping the field sets in `app_field_set`.
///
/// The current field set of every versioned type is worked out once, at startup, which is also where a type
/// with more fields than a mask has bits is refused. Storing them is an upsert -- the id being the hash of
/// the field set, a row that is already there is the same field set -- so a rolling deployment writes both
/// field sets and neither node has to wait for the other.
///
/// Older field sets are read on demand and kept. That only happens on the conflict path, which is rare, and a
/// field set never changes under its id, so there is nothing to invalidate.
public class DefaultFieldSetService
    implements FieldSetService, InitializingBean
{
    private final static Logger log = LoggerFactory.getLogger(DefaultFieldSetService.class);

    private final static Table<?> TABLE = DSL.table(DSL.name(MergeTables.APP_FIELD_SET));

    private final static Field<String> ID = DSL.field(DSL.name("id"), String.class);

    private final static Field<String> ENTITY_TYPE = DSL.field(DSL.name("entity_type"), String.class);

    private final static Field<String> FIELDS = DSL.field(DSL.name("fields"), String.class);

    private final static Field<Timestamp> CREATED = DSL.field(DSL.name("created"), Timestamp.class);

    private final DSLContext dslContext;

    /// The field set of every versioned type as this deployment has it. Built in the constructor: the domain
    /// does not change after startup, and neither does what a mask written from here on means.
    private final Map<String, FieldSet> current = new LinkedHashMap<>();

    /// Every field set we have looked up, current ones included, by id. A field set is its hash, so a hit is
    /// final.
    private final Map<String, FieldSet> byId = new ConcurrentHashMap<>();


    public DefaultFieldSetService(QLiveDomain domain, DSLContext dslContext)
    {
        this.dslContext = dslContext;

        for (String typeName : MergeMeta.versionedTypes(domain))
        {
            final FieldSet fieldSet = FieldSet.of(domain, typeName);

            current.put(typeName, fieldSet);
            byId.put(fieldSet.getId(), fieldSet);
        }
    }


    /// Writes the current field sets, and fails the startup where one of them is already stored as a
    /// different list of fields.
    ///
    /// That collision cannot happen at the rate SHA-256 collides, and the check costs nothing anyway: the
    /// row has to be read to find out whether to write it. What it actually catches is a field set id
    /// computed from something other than the list that assigns the bit indices, which is a mistake with no
    /// symptoms otherwise -- masks would simply name the wrong fields.
    @Override
    public void afterPropertiesSet()
    {
        for (FieldSet fieldSet : current.values())
        {
            store(fieldSet);
        }

        log.debug("Stored field sets: {}", current.values());
    }


    @Override
    public FieldSet current(String typeName)
    {
        final FieldSet fieldSet = current.get(typeName);

        if (fieldSet == null)
        {
            throw new QLiveException(
                "No field set for '" + typeName + "'. Field sets exist for the versioned types of the " +
                    "domain, and only a versioned type has masks to read."
            );
        }

        return fieldSet;
    }


    @Override
    public Set<String> fields(String fieldSetId, String typeName, BigInteger mask)
    {
        final FieldSet fieldSet = lookup(fieldSetId, typeName);

        if (fieldSet == null)
        {
            return null;
        }

        final Set<String> named = fieldSet.fields(mask);

        // the fields the type no longer has go: a field that is gone cannot be in conflict, and this is the
        // whole of "permute the mask into today's positions" once the mask is names rather than bits
        named.retainAll(current(typeName).getFields());

        return named;
    }


    @Override
    public int pruneUnused(Timestamp storedBefore)
    {
        final List<String> keep = current.values().stream().map(FieldSet::getId).toList();

        final int dropped = dslContext.deleteFrom(TABLE)
            .where(CREATED.lessThan(storedBefore))
            .and(ID.notIn(keep))
            .andNotExists(
                dslContext.selectOne()
                    .from(DSL.table(DSL.name(MergeTables.APP_VERSION)))
                    .where(DSL.field(DSL.name("field_set"), String.class).eq(ID))
            )
            .execute();

        log.debug("Pruned {} field sets", dropped);

        return dropped;
    }


    /// The field set under the given id, from what we have or from the table, or null where it is not stored.
    private FieldSet lookup(String fieldSetId, String typeName)
    {
        if (fieldSetId == null)
        {
            return null;
        }

        final FieldSet known = byId.get(fieldSetId);

        if (known != null)
        {
            return known;
        }

        final Record stored = dslContext.select(ENTITY_TYPE, FIELDS)
            .from(TABLE)
            .where(ID.eq(fieldSetId))
            .fetchOne();

        if (stored == null)
        {
            // not cached as absent: another node of a rolling deployment may be about to write it
            log.debug("No field set {} of '{}' stored", fieldSetId, typeName);
            return null;
        }

        final FieldSet fieldSet = FieldSet.of(
            stored.get(ENTITY_TYPE),
            List.of(stored.get(FIELDS).split(FieldSet.SEPARATOR))
        );

        byId.put(fieldSetId, fieldSet);

        return fieldSet;
    }


    private void store(FieldSet fieldSet)
    {
        final String stored = dslContext.select(FIELDS)
            .from(TABLE)
            .where(ID.eq(fieldSet.getId()))
            .fetchOne(FIELDS);

        if (stored == null)
        {
            // DO NOTHING rather than a plain insert, so that two nodes starting at once are not a failure
            dslContext.insertInto(TABLE)
                .columns(ID, ENTITY_TYPE, FIELDS, CREATED)
                .values(
                    fieldSet.getId(),
                    fieldSet.getTypeName(),
                    fieldSet.getJoinedFields(),
                    new Timestamp(System.currentTimeMillis())
                )
                .onConflict(ID)
                .doNothing()
                .execute();
        }
        else if (!stored.equals(fieldSet.getJoinedFields()))
        {
            throw new QLiveException(
                "Field set " + fieldSet.getId() + " of '" + fieldSet.getTypeName() + "' is stored as [" +
                    stored + "] and computed as [" + fieldSet.getJoinedFields() + "]. The id is a hash of " +
                    "the field list, so the two cannot disagree unless the id is computed from something " +
                    "other than the list that assigns the bit indices."
            );
        }
    }
}
