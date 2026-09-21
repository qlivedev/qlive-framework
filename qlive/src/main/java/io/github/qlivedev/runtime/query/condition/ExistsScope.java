package io.github.qlivedev.runtime.query.condition;

import org.jooq.Condition;

/// A to-many relation crossed by a filter path, and the correlated `EXISTS` that has to be built around any
/// condition referring through it.
///
/// A to-many relation is never joined into the query it belongs to -- that would multiply rows and break
/// both paging and the row count -- so a condition on `bazLinks.baz.name` cannot read a joined column. It
/// becomes "there is a link row for which ...", and this is the thing that knows how to say that: the
/// planner owns the tables and the aliases, the transformer only knows that some conditions need wrapping.
public interface ExistsScope
{
    /// Distance from the root of the query plan. Scopes are applied deepest first, so that a scope nested
    /// inside another ends up inside the other's subquery, where the alias it correlates to is in scope.
    int depth();


    /// Wraps the given condition in this scope's correlated `EXISTS`.
    Condition wrap(Condition inner);
}
