package com.dataciders.qlive.runtime.merge;

import com.dataciders.qlive.model.merge.EntityChange;
import com.dataciders.qlive.model.merge.EntityDeletion;
import com.dataciders.qlive.model.merge.MergeConfig;
import com.dataciders.qlive.model.merge.MergeResult;
import org.jooq.Table;

import java.util.List;

/// Stores a working set: any number of rows, of any number of types, in one transaction.
///
/// The write side of what {@link com.dataciders.qlive.runtime.query.QueryDocumentService} is on the read
/// side. One generic path rather than a mutation and an input type per operation, and the same reason it
/// works: a change travels as field names and `GenericScalar` values, and the domain is what says which
/// column and which Java type each of those is.
///
/// {@link DefaultMergeService} is the implementation writing the application's JOOQ schema; an application
/// needing something else registers its own bean.
public interface MergeService
{
    /// Writes every change and every deletion, or none of them.
    ///
    /// A row of a versioned type is written under an optimistic lock: the statement carries the version the
    /// row was read at, and a row somebody else has written since matches nothing. That is a conflict, and a
    /// conflict rolls the whole merge back and comes back as data -- nothing here throws on account of one,
    /// and nothing here is half-written afterwards.
    ///
    /// @param changes      rows to insert or update
    /// @param deletions    rows to remove, applied after the changes
    /// @param config       what the caller can do about a conflict
    ///
    /// @return what came of it, never null
    MergeResult merge(List<EntityChange> changes, List<EntityDeletion> deletions, MergeConfig config);


    /// Raises unless the given table is one nothing versions, i.e. one that may be written directly.
    ///
    /// The guard for the service that has its own reason to write a table with JOOQ. A versioned row written
    /// past the merge keeps the version it had, so the next merge against it succeeds on a base that is no
    /// longer the truth -- and silently overwrites the direct write. Calling this turns that into a failure
    /// where the direct write is, which is the only place anybody can still do something about it.
    ///
    /// @param table    JOOQ table about to be written directly
    void ensureNotVersioned(Table<?> table);
}
