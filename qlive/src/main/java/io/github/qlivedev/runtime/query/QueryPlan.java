package io.github.qlivedev.runtime.query;

import io.github.qlivedev.model.QueryConfig;
import org.jooq.Condition;
import org.jooq.SortField;

import java.util.List;

/// Everything one query document needs to be executed, worked out from the GraphQL selection and the query
/// config before a single statement is built.
///
/// @param root         root of the plan tree, the document's own type
/// @param condition    transformed filter condition, `null` when the config constrains nothing
/// @param sortFields   transformed sort fields, never empty
/// @param countJoins   the joins the row count needs, which are the ones its condition reads through
/// @param config       the config as it was actually applied, which is what goes back to the client
public record QueryPlan(
    PlanNode root,
    Condition condition,
    List<SortField<?>> sortFields,
    List<PlanNode> countJoins,
    QueryConfig config
)
{
}
