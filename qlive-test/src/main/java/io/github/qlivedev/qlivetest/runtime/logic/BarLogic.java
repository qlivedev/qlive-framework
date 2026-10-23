package io.github.qlivedev.qlivetest.runtime.logic;

import io.github.qlivedev.qlive.api.annotation.Input;
import io.github.qlivedev.qlive.api.annotation.QLiveAction;
import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.Rules;
import io.github.qlivedev.qlive.runtime.action.Rule;
import io.github.qlivedev.qlive.runtime.action.RuleContext;
import io.github.qlivedev.qlive.runtime.action.RuleFactory;
import io.github.qlivedev.qlive.runtime.merge.EntityWriter;
import io.github.qlivedev.qlivetest.domain.tables.pojos.Bar;
import jakarta.validation.constraints.NotNull;

import java.sql.Timestamp;
import java.util.List;

import static io.github.qlivedev.qlive.runtime.scalar.FilterDSL.value;
import static io.github.qlivedev.qlivetest.domain.Tables.BAR;

/// Example actions on bars.
@QLiveLogic
public class BarLogic
{
    private final EntityWriter entityWriter;


    public BarLogic(EntityWriter entityWriter)
    {
        this.entityWriter = entityWriter;
    }


    /// Approves a bar. `bar` is a subject: the request names the row by id and version, and the method gets the row
    /// as it is stored. The rules hold before the method runs.
    ///
    /// @param bar      the bar to approve
    /// @param comment  approval comment
    /// @param notify   whether to notify the bar's owner
    ///
    /// @return the approval
    @QLiveAction
    @Rules({ DescriptionRequired.class, CommentWhenNotifying.class })
    public @NotNull ApproveResult approveBar(Bar bar, @NotNull String comment, boolean notify)
    {
        approve(bar);
        return new ApproveResult(bar.getId(), bar.getName(), comment, notify);
    }


    /// Approves several bars at once. All of them are approved, or none is.
    ///
    /// @param bars     the bars to approve
    /// @param comment  approval comment
    ///
    /// @return one approval per bar, in the order given
    @QLiveAction
    @Rules(DescriptionsRequired.class)
    public @NotNull List<ApproveResult> approveBars(List<Bar> bars, @NotNull String comment)
    {
        return bars.stream()
            .map(bar -> {
                approve(bar);
                return new ApproveResult(bar.getId(), bar.getName(), comment, false);
            })
            .toList();
    }


    /// Quotes a bar that may not be saved yet. `draft` is input rather than a subject: marked `@Input`, it takes
    /// the values the user is editing, and no row is loaded.
    ///
    /// @param draft    the bar as the user is editing it
    ///
    /// @return the price, a hundred per unit
    @QLiveAction
    @Rules(NumPositive.class)
    public @NotNull int quoteBar(@NotNull @Input Bar draft)
    {
        return draft.getNum() * 100;
    }


    private void approve(Bar bar)
    {
        bar.setApproved(new Timestamp(System.currentTimeMillis()));
        entityWriter.update(bar, BAR.APPROVED);
    }


    /// A bar is approved only with a description. Reads the subject's stored field, so it is evaluated against the
    /// database.
    public static class DescriptionRequired
        implements RuleFactory
    {
        @Override
        public Rule create(RuleContext ctx)
        {
            return new Rule(
                ctx.field("bar.description").isNotNull()
                    .and(ctx.field("bar.description").ne(value(""))),
                "bar.description",
                "Bar.descriptionRequired"
            );
        }
    }


    /// Notifying the owner takes a comment to send. Reads input alone, so it is evaluated in memory.
    public static class CommentWhenNotifying
        implements RuleFactory
    {
        @Override
        public Rule create(RuleContext ctx)
        {
            return new Rule(
                ctx.field("notify").isFalse()
                    .or(ctx.field("comment").ne(value(""))),
                "comment",
                "Bar.commentWhenNotifying"
            );
        }
    }


    /// The bulk form of {@link DescriptionRequired}. A refusal blames each failing bar by its position:
    /// `bars.1.description`.
    public static class DescriptionsRequired
        implements RuleFactory
    {
        @Override
        public Rule create(RuleContext ctx)
        {
            return new Rule(
                ctx.field("bars.description").isNotNull()
                    .and(ctx.field("bars.description").ne(value(""))),
                "bars.description",
                "Bar.descriptionRequired"
            );
        }
    }


    /// A quote is for at least one unit. Reads input alone, the unsaved bar's field, so it is evaluated in memory.
    public static class NumPositive
        implements RuleFactory
    {
        @Override
        public Rule create(RuleContext ctx)
        {
            return new Rule(
                ctx.field("draft.num").gt(value(0)),
                "draft.num",
                "Bar.numPositive"
            );
        }
    }
}
