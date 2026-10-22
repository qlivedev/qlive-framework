package io.github.qlivedev.qlive.runtime.action;

/// A rule of an action, named on the action method by `@Rules`.
///
/// Public, top-level or static nested, with a public no-arg constructor: the framework instantiates it itself, once
/// per action it is named on, at startup. What it returns is data and is kept; the factory is not called again.
///
/// ```java
/// public class DescriptionRequired implements RuleFactory
/// {
///     public Rule create(RuleContext ctx)
///     {
///         return new Rule(ctx.field("bar.description").isNotNull(), "bar.description", "Bar.descriptionRequired");
///     }
/// }
/// ```
public interface RuleFactory
{
    /// Creates the rule for one action.
    ///
    /// @param ctx  hands out the paths the condition reads, checked against the action's parameters
    ///
    /// @return rule
    Rule create(RuleContext ctx);
}
