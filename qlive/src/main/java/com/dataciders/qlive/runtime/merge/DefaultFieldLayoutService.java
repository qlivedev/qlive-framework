package com.dataciders.qlive.runtime.merge;

import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.meta.MergeMeta;
import de.quinscape.domainql.DomainQL;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/// Default {@link FieldLayoutService}, keeping the layouts in `app_field_layout`.
///
/// The current layout of every versioned type is worked out once, at startup, which is also where a type
/// with more fields than a mask has bits is refused. Storing them is an upsert -- the id being the hash of
/// the layout, a row that is already there is the same layout -- so a rolling deployment writes both
/// layouts and neither node has to wait for the other.
///
/// Older layouts are read on demand and kept. That only happens on the conflict path, which is rare, and a
/// layout never changes under its id, so there is nothing to invalidate.
public class DefaultFieldLayoutService
    implements FieldLayoutService, InitializingBean
{
    private final static Logger log = LoggerFactory.getLogger(DefaultFieldLayoutService.class);

    private final static Table<?> TABLE = DSL.table(DSL.name(MergeTables.APP_FIELD_LAYOUT));

    private final static Field<String> ID = DSL.field(DSL.name("id"), String.class);

    private final static Field<String> ENTITY_TYPE = DSL.field(DSL.name("entity_type"), String.class);

    private final static Field<String> FIELDS = DSL.field(DSL.name("fields"), String.class);

    private final DSLContext dslContext;

    /// The layout of every versioned type as this deployment has it. Built in the constructor: the domain
    /// does not change after startup, and neither does what a mask written from here on means.
    private final Map<String, FieldLayout> current = new LinkedHashMap<>();

    /// Every layout we have looked up, current ones included, by id. A layout is its hash, so a hit is
    /// final.
    private final Map<String, FieldLayout> byId = new ConcurrentHashMap<>();


    public DefaultFieldLayoutService(DomainQL domainQL, DSLContext dslContext)
    {
        this.dslContext = dslContext;

        for (String typeName : MergeMeta.versionedTypes(domainQL))
        {
            final FieldLayout layout = FieldLayout.of(domainQL, typeName);

            current.put(typeName, layout);
            byId.put(layout.getId(), layout);
        }
    }


    /// Writes the current layouts, and fails the startup where one of them is already stored as a different
    /// list of fields.
    ///
    /// That collision cannot happen at the rate SHA-256 collides, and the check costs nothing anyway: the
    /// row has to be read to find out whether to write it. What it actually catches is a layout id computed
    /// from something other than the list that assigns the bit indices, which is a mistake with no
    /// symptoms otherwise -- masks would simply name the wrong fields.
    @Override
    public void afterPropertiesSet()
    {
        for (FieldLayout layout : current.values())
        {
            store(layout);
        }

        log.debug("Stored field layouts: {}", current.values());
    }


    @Override
    public FieldLayout current(String typeName)
    {
        final FieldLayout layout = current.get(typeName);

        if (layout == null)
        {
            throw new QLiveException(
                "No field layout for '" + typeName + "'. Layouts exist for the versioned types of the " +
                    "domain, and only a versioned type has masks to read."
            );
        }

        return layout;
    }


    @Override
    public Set<String> fields(String layoutId, String typeName, BigInteger mask)
    {
        final FieldLayout layout = lookup(layoutId, typeName);

        if (layout == null)
        {
            return null;
        }

        final Set<String> named = layout.fields(mask);

        // the fields the type no longer has go: a field that is gone cannot be in conflict, and this is the
        // whole of "permute the mask into today's positions" once the mask is names rather than bits
        named.retainAll(current(typeName).getFields());

        return named;
    }


    @Override
    public int pruneUnused()
    {
        final List<String> keep = current.values().stream().map(FieldLayout::getId).toList();

        final int dropped = dslContext.deleteFrom(TABLE)
            .where(
                ID.notIn(
                    dslContext.selectDistinct(DSL.field(DSL.name("field_layout"), String.class))
                        .from(DSL.table(DSL.name(MergeTables.APP_VERSION)))
                )
            )
            .and(ID.notIn(keep))
            .execute();

        log.debug("Pruned {} field layouts", dropped);

        return dropped;
    }


    /// The layout under the given id, from what we have or from the table, or null where it is not stored.
    private FieldLayout lookup(String layoutId, String typeName)
    {
        if (layoutId == null)
        {
            return null;
        }

        final FieldLayout known = byId.get(layoutId);

        if (known != null)
        {
            return known;
        }

        final Record stored = dslContext.select(ENTITY_TYPE, FIELDS)
            .from(TABLE)
            .where(ID.eq(layoutId))
            .fetchOne();

        if (stored == null)
        {
            // not cached as absent: another node of a rolling deployment may be about to write it
            log.debug("No field layout {} of '{}' stored", layoutId, typeName);
            return null;
        }

        final FieldLayout layout = FieldLayout.of(
            stored.get(ENTITY_TYPE),
            List.of(stored.get(FIELDS).split(FieldLayout.SEPARATOR))
        );

        byId.put(layoutId, layout);

        return layout;
    }


    private void store(FieldLayout layout)
    {
        final String stored = dslContext.select(FIELDS)
            .from(TABLE)
            .where(ID.eq(layout.getId()))
            .fetchOne(FIELDS);

        if (stored == null)
        {
            // DO NOTHING rather than a plain insert, so that two nodes starting at once are not a failure
            dslContext.insertInto(TABLE)
                .columns(ID, ENTITY_TYPE, FIELDS)
                .values(layout.getId(), layout.getTypeName(), layout.getJoinedFields())
                .onConflict(ID)
                .doNothing()
                .execute();
        }
        else if (!stored.equals(layout.getJoinedFields()))
        {
            throw new QLiveException(
                "Field layout " + layout.getId() + " of '" + layout.getTypeName() + "' is stored as [" +
                    stored + "] and computed as [" + layout.getJoinedFields() + "]. The id is a hash of " +
                    "the field list, so the two cannot disagree unless the id is computed from something " +
                    "other than the list that assigns the bit indices."
            );
        }
    }
}
