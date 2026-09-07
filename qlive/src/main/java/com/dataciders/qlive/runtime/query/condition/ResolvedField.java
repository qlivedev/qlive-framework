package com.dataciders.qlive.runtime.query.condition;

import org.jooq.Field;

import java.util.List;

/// What a FilterDSL field path resolves to.
///
/// @param field     database field the path names, on whichever alias the resolver reaches it through
/// @param scopes    to-many relations crossed on the way, root-most first. Empty for the ordinary case of a
///                  path that only follows to-one relations, which are plain joins.
public record ResolvedField(
    Field<?> field,
    List<ExistsScope> scopes
)
{
    public ResolvedField(Field<?> field)
    {
        this(field, List.of());
    }
}
