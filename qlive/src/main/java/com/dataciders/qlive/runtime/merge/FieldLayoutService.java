package com.dataciders.qlive.runtime.merge;

import java.math.BigInteger;
import java.util.Set;

/// The layouts field masks are written against and read back under.
///
/// There are two of them for any one mask: the layout the writer had, named by the version record, and the
/// layout the reader has. Where they are the same the mask reads directly; where they differ and the older
/// one is still stored, the names in it are what the mask is turned into; where the older one is unknown
/// there is no honest answer and the caller is told so.
public interface FieldLayoutService
{
    /// The layout of the given versioned type as the schema in front of us has it. This is what a mask
    /// being written is built with, and what a mask being read is compared against.
    ///
    /// @throws com.dataciders.qlive.runtime.QLiveException  if the type is not a versioned type of the
    ///                                                      domain
    FieldLayout current(String typeName);


    /// The fields the given mask names, read under the layout it was written with, and narrowed to the
    /// fields the type still has.
    ///
    /// Null where that layout is not stored, because the record predates the table or was written by
    /// something else. That is not an error: it means the same as a version record that has been pruned --
    /// assume every field changed -- and the caller is the one that knows what to assume instead.
    Set<String> fields(String layoutId, String typeName, BigInteger mask);


    /// Drops the layouts no version record names any more, and answers how many went.
    ///
    /// The layouts this deployment is using are kept whether a record names them or not: they are about to
    /// be named by the next merge, and a record naming a layout that is not there reads as "we cannot tell
    /// which fields changed" -- which would be a conflict on every field of a row nobody else touched.
    int pruneUnused();
}
