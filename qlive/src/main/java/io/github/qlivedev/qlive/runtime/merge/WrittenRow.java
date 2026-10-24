package io.github.qlivedev.qlive.runtime.merge;

import io.github.qlivedev.qlive.api.generic.GenericScalar;

import java.util.List;
import java.util.Map;

/// One write a merge made to one row, with what a client holding the row needs to follow it: the version it
/// left and the one it was made against, and the values written.
///
/// The values are what the write sent, not what the row holds now: a write that went in over somebody else's
/// leaves their fields as they were, and those are not named here.
///
/// @param type     GraphQL name of the row's type
/// @param id       the row's id
/// @param version  the version the write left, `null` for an unversioned type and for a deletion
/// @param prev     the version the write was made against, `null` for a new row and where `version` is
/// @param deleted  `true` for a row the write deleted
/// @param fields   the scalar fields written, with their values, by field name
/// @param links    the many-to-many fields whose associations the write changed
public record WrittenRow(
    String type,
    String id,
    String version,
    String prev,
    boolean deleted,
    Map<String, GenericScalar> fields,
    List<String> links
)
{
    public WrittenRow
    {
        fields = Map.copyOf(fields);
        links = List.copyOf(links);
    }
}
