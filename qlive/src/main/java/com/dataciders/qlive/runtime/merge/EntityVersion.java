package com.dataciders.qlive.runtime.merge;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.Objects;

/// One recorded change to one row: which fields it touched, under which field layout, and the version it was
/// made against.
///
/// This is a row of `app_version` and it is also the whole of what a subscriber to a change needs -- the
/// type, the id, the new version and the mask of what moved. A push module would hand this to a broker; the
/// merge hands it to whoever listens for {@link EntityVersionsEvent}. The two are the same record on purpose.
///
/// {@link #getPrev()} is what makes the records of one row a chain, and the chain is best-effort: the record
/// it names may have been pruned, which reads as "we cannot tell which fields changed" rather than as an
/// error.
public final class EntityVersion
{
    private final String id;

    private final String entityType;

    private final String entityId;

    private final String prev;

    private final BigInteger fieldMask;

    private final String fieldLayout;

    private final String ownerId;

    private final Timestamp created;


    public EntityVersion(
        String id,
        String entityType,
        String entityId,
        String prev,
        BigInteger fieldMask,
        String fieldLayout,
        String ownerId,
        Timestamp created
    )
    {
        this.id = id;
        this.entityType = entityType;
        this.entityId = entityId;
        this.prev = prev;
        this.fieldMask = fieldMask;
        this.fieldLayout = fieldLayout;
        this.ownerId = ownerId;
        this.created = created;
    }


    /// The version this change produced, and what the row's version column holds while the row is in this
    /// state.
    public String getId()
    {
        return id;
    }


    /// GraphQL name of the type whose row changed.
    public String getEntityType()
    {
        return entityType;
    }


    public String getEntityId()
    {
        return entityId;
    }


    /// The version the change was made against, or null where the row had none -- a new row, or one whose
    /// version was never written by a merge.
    public String getPrev()
    {
        return prev;
    }


    /// Bit per field of the type, set for the fields this change touched, in the positions
    /// {@link #getFieldLayout()} assigns.
    public BigInteger getFieldMask()
    {
        return fieldMask;
    }


    /// Id of the {@link FieldLayout} the mask was written against.
    public String getFieldLayout()
    {
        return fieldLayout;
    }


    /// The user who made the change. What lets a subscriber tell somebody else's write from its own.
    public String getOwnerId()
    {
        return ownerId;
    }


    /// When the change was made, and what the cleanup goes by.
    public Timestamp getCreated()
    {
        return created;
    }


    @Override
    public boolean equals(Object o)
    {
        return o instanceof EntityVersion that && id.equals(that.id);
    }


    @Override
    public int hashCode()
    {
        return Objects.hashCode(id);
    }


    @Override
    public String toString()
    {
        return "EntityVersion " + id + " of " + entityType + " " + entityId + ", prev = " + prev +
            ", mask = " + fieldMask;
    }
}
