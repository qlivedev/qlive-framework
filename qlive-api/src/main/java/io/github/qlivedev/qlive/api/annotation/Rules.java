package io.github.qlivedev.qlive.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Names the rules of a {@link QLiveAction} method.
///
/// Each class is a rule factory: it implements the framework's `RuleFactory`, is public, top-level or static
/// nested, and has a public no-arg constructor. The framework instantiates it once at startup and keeps the rule
/// it returns. A class that is no rule factory fails at startup, naming the action.
///
/// On the method rather than on a parameter, because a rule may read any of the method's parameters: a rule
/// over a subject and an input value belongs to neither.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Rules
{
    /// The rule factory classes.
    ///
    /// @return rule factory classes
    Class<?>[] value();
}
