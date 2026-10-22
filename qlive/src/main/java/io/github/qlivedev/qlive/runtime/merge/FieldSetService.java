package io.github.qlivedev.qlive.runtime.merge;

import java.math.BigInteger;
import java.sql.Timestamp;
import java.util.Set;

/// The field sets field masks are written against and read back under.
///
/// There are two of them for any one mask: the field set the writer had, named by the version record, and the
/// field set the reader has. Where they are the same the mask reads directly; where they differ and the older
/// one is still stored, the names in it are what the mask is turned into; where the older one is unknown
/// there is no honest answer and the caller is told so.
public interface FieldSetService
{
    /// The field set of the given versioned type as the schema in front of us has it. This is what a mask
    /// being written is built with, and what a mask being read is compared against.
    ///
    /// @throws io.github.qlivedev.qlive.runtime.QLiveException  if the type is not a versioned type of the
    ///                                                      domain
    FieldSet current(String typeName);


    /// The fields the given mask names, read under the field set it was written with, and narrowed to the
    /// fields the type still has.
    ///
    /// Null where that field set is not stored, because the record predates the table or was written by
    /// something else. That is not an error: it means the same as a version record that has been pruned --
    /// assume every field changed -- and the caller is the one that knows what to assume instead.
    Set<String> fields(String fieldSetId, String typeName, BigInteger mask);


    /// Drops the field sets stored before the given point that no version record names any more, and answers
    /// how many went.
    ///
    /// Two things are kept regardless. The field sets this deployment is using are about to be named by the
    /// next merge, and a record naming a field set that is not there reads as "we cannot tell which fields
    /// changed" -- a conflict on every field of a row nobody else touched.
    ///
    /// And so is anything stored recently, which is what the cutoff is for. During a rolling deployment the
    /// node coming up stores its field set before it has written a single record against it; to a node still
    /// on the old code that field set is neither current nor referenced, and without the cutoff the old node
    /// would sweep it away between the new one storing it and using it. The cutoff to pass is the one the
    /// version records are pruned by: a field set older than that whose records have all gone is genuinely
    /// finished with.
    int pruneUnused(Timestamp storedBefore);
}
