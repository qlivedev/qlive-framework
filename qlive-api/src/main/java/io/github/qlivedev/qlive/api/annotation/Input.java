package io.github.qlivedev.qlive.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Makes a parameter of a {@link QLiveAction} method input where its type would make it a subject.
///
/// A parameter of a table-backed type, or a `List` of one, is a subject: the request names stored rows by id and
/// version, and the method gets them as they are stored. Marked `@Input`, it takes the values the client sends
/// instead, as the type's input type, for an action about data that isn't saved:
///
/// ```java
/// @QLiveAction
/// public @NotNull Quote quoteBar(@Input Bar draft)
/// ```
///
/// Such a parameter is input like any other: no row is loaded, no version checked, and rules read its properties
/// in memory.
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface Input
{
}
