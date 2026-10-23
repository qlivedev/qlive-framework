package io.github.qlivedev.qlive.runtime.merge;

import org.jooq.Field;

/// Writes rows of the domain's types from Java code -- an action, a job, a service -- in the caller's
/// transaction, through the same merge the client's working sets go through.
///
/// Every call requires a running transaction and joins it, and writes at once, so that later reads in the same
/// transaction see it. The caller orders its writes: a row before the rows pointing at it. Rows are the POJOs the
/// domain exposes its types as, the ones an action's subjects arrive as. After a write, a row's POJO carries the
/// version it was written at, so the next write to it holds to that.
///
/// A write that meets somebody else's throws {@link MergeConflictException}, which rolls the caller's transaction
/// back. Version records are written as for the client's merge, for whoever the security context holds, and push
/// events go out once the transaction commits.
public interface EntityWriter
{
    /// Inserts a new row with every field of the POJO that is set. A POJO without an id gets a generated one.
    ///
    /// @param row  POJO of a table-backed domain type
    void insert(Object row);


    /// Updates the given fields of a row, holding to the version its POJO carries, with the values the POJO has.
    ///
    /// A POJO can't tell which of its fields changed, so the update names them.
    ///
    /// @param row      POJO of a table-backed domain type
    /// @param fields   columns of its table to write
    void update(Object row, Field<?>... fields);


    /// Deletes a row, holding to the version its POJO carries.
    ///
    /// @param row  POJO of a table-backed domain type
    void delete(Object row);


    /// Associates a row with others through one of its many-to-many fields.
    ///
    /// @param row      POJO of a table-backed domain type
    /// @param field    name of the many-to-many field
    /// @param others   POJOs of the rows to associate
    void link(Object row, String field, Object... others);


    /// Takes associations through one of a row's many-to-many fields away.
    ///
    /// @param row      POJO of a table-backed domain type
    /// @param field    name of the many-to-many field
    /// @param others   POJOs of the rows no longer associated
    void unlink(Object row, String field, Object... others);
}
