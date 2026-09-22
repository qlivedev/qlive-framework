package io.github.qlivedev.runtime.merge;

import io.github.qlivedev.model.merge.EntityChange;
import io.github.qlivedev.model.merge.EntityDeletion;
import io.github.qlivedev.model.merge.MergeConfig;
import io.github.qlivedev.model.merge.MergeResult;
import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLMutation;
import jakarta.validation.constraints.NotNull;

import java.util.List;

/// The framework's write mutation, and the only one an application needs in order to store anything.
///
/// The four input types below are the framework's, not the application's forty. A change travels as field
/// names and `GenericScalar` values, which QLiveDomain coerces to whatever Java type the field actually has, so
/// there is no `BarInput`, no `BazInput`, and no mutation per operation to keep in step with them.
///
/// Declared as a bean in QLiveConfiguration rather than found: `@GraphQLLogic` is meta-annotated
/// `@Component`, but an application's component scan covers the application's own packages and never the
/// framework's. What picks this up is the same `getBeansWithAnnotation()` call the application's QLiveDomain
/// configuration already makes.
@GraphQLLogic
public class MergeLogic
{
    private final MergeService mergeService;


    public MergeLogic(MergeService mergeService)
    {
        this.mergeService = mergeService;
    }


    /// Stores a working set: every change and every deletion in one transaction, or none of them.
    ///
    /// @param changes      rows to insert or update
    /// @param deletions    rows to remove
    /// @param mergeConfig  what the caller can do about a conflict
    ///
    /// @return whether it landed, and what stood in the way if it did not
    @GraphQLMutation
    public @NotNull MergeResult mergeWorkingSet(
        @NotNull List<EntityChange> changes,
        @NotNull List<EntityDeletion> deletions,
        @NotNull MergeConfig mergeConfig
    )
    {
        return mergeService.merge(changes, deletions, mergeConfig);
    }
}
