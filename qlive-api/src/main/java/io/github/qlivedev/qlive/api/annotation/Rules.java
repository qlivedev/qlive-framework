package io.github.qlivedev.qlive.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Names the rules of a {@link QLiveAction} method, or the invariants of a type.
///
/// Each class is a rule factory: it implements the framework's `RuleFactory`, is public, top-level or static
/// nested, and has a public no-arg constructor. The framework instantiates it once at startup and keeps the rule
/// it returns. A class that is no rule factory fails at startup, naming the action or the type.
///
/// On an action method, the rules decide whether the action runs. On the method rather than on a parameter,
/// because a rule may read any of the method's parameters: a rule over a subject and an input value belongs to
/// neither.
///
/// On a type, the rules are invariants: they hold for every write of its rows, and their paths name the type's
/// own fields, `end`. On the class rather than on a getter, because a rule over two fields belongs to neither.
/// The class is a POJO the domain exposes, one backed by no table or a type override; the generated POJO of a
/// table gets its invariants through the framework's `RulesMetadataProvider` instead.
@Target({ ElementType.METHOD, ElementType.TYPE })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Rules
{
    /// The rule factory classes.
    ///
    /// @return rule factory classes
    Class<?>[] value();
}
