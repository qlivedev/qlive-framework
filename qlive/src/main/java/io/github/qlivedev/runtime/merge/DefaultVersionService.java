package io.github.qlivedev.runtime.merge;

import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.InsertValuesStepN;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.impl.DSL;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.List;

/// Default {@link VersionService}, keeping the records in `app_version`.
///
/// Reads go through the {@link VersionHolder} first. What is behind it is one indexed lookup by primary key,
/// so the holder is a saving rather than a necessity, and a record it has not got is simply read.
public class DefaultVersionService
    implements VersionService
{
    private final static Logger log = LoggerFactory.getLogger(DefaultVersionService.class);

    private final static Table<?> TABLE = DSL.table(DSL.name(MergeTables.APP_VERSION));

    private final static Field<String> ID = DSL.field(DSL.name("id"), String.class);

    private final static Field<String> ENTITY_TYPE = DSL.field(DSL.name("entity_type"), String.class);

    private final static Field<String> ENTITY_ID = DSL.field(DSL.name("entity_id"), String.class);

    private final static Field<String> PREV = DSL.field(DSL.name("prev"), String.class);

    private final static Field<BigInteger> FIELD_MASK = DSL.field(DSL.name("field_mask"), BigInteger.class);

    private final static Field<String> FIELD_LAYOUT = DSL.field(DSL.name("field_layout"), String.class);

    private final static Field<String> OWNER_ID = DSL.field(DSL.name("owner_id"), String.class);

    private final static Field<Timestamp> CREATED = DSL.field(DSL.name("created"), Timestamp.class);

    private final DSLContext dslContext;

    private final VersionHolder versionHolder;

    private final ApplicationEventPublisher eventPublisher;


    public DefaultVersionService(
        DSLContext dslContext,
        VersionHolder versionHolder,
        ApplicationEventPublisher eventPublisher
    )
    {
        this.dslContext = dslContext;
        this.versionHolder = versionHolder;
        this.eventPublisher = eventPublisher;
    }


    @Override
    public EntityVersion get(String versionId)
    {
        if (versionId == null)
        {
            return null;
        }

        final EntityVersion held = versionHolder.get(versionId);

        if (held != null)
        {
            return held;
        }

        final Record stored = dslContext.select(
                ID, ENTITY_TYPE, ENTITY_ID, PREV, FIELD_MASK, FIELD_LAYOUT, OWNER_ID, CREATED
            )
            .from(TABLE)
            .where(ID.eq(versionId))
            .fetchOne();

        return stored == null ? null : new EntityVersion(
            stored.get(ID),
            stored.get(ENTITY_TYPE),
            stored.get(ENTITY_ID),
            stored.get(PREV),
            stored.get(FIELD_MASK),
            stored.get(FIELD_LAYOUT),
            stored.get(OWNER_ID),
            stored.get(CREATED)
        );
    }


    @Override
    public void write(List<EntityVersion> versions)
    {
        if (versions.isEmpty())
        {
            return;
        }

        // the untyped column list rather than the eight-argument one, which insists on a typed table
        InsertValuesStepN<?> insert = dslContext.insertInto(TABLE)
            .columns(List.of(ID, ENTITY_TYPE, ENTITY_ID, PREV, FIELD_MASK, FIELD_LAYOUT, OWNER_ID, CREATED));

        for (EntityVersion version : versions)
        {
            insert = insert.values(
                version.getId(),
                version.getEntityType(),
                version.getEntityId(),
                version.getPrev(),
                version.getFieldMask(),
                version.getFieldLayout(),
                version.getOwnerId(),
                version.getCreated()
            );
        }

        insert.execute();

        log.debug("Wrote version records: {}", versions);

        eventPublisher.publishEvent(new EntityVersionsEvent(this, versions));
    }


    @Override
    public int prune(Timestamp before)
    {
        final int dropped = dslContext.deleteFrom(TABLE)
            .where(CREATED.lessThan(before))
            .execute();

        log.debug("Pruned {} version records", dropped);

        return dropped;
    }
}
