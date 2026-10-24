package io.github.qlivedev.qlivetest.runtime.rules;

import io.github.qlivedev.qlive.runtime.action.Rule;
import io.github.qlivedev.qlive.runtime.action.RuleContext;
import io.github.qlivedev.qlive.runtime.action.RuleFactory;

/// An invariant of Quux: its first number is not above its second. A rule over two fields, which belongs to
/// neither and blames the second, the one a user changes to fix it.
///
/// Attached in `QLiveDomainConfiguration`, Quux's POJO being generated.
public class NumsOrdered
    implements RuleFactory
{
    @Override
    public Rule create(RuleContext ctx)
    {
        return new Rule(ctx.field("numA").le(ctx.field("numB")), "numB", "Quux.numsOrdered");
    }
}
