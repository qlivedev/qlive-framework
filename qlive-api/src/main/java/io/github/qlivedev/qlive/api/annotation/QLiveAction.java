package io.github.qlivedev.qlive.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/// Marks a logic method as an action: a GraphQL mutation the framework guards with rules.
///
/// Used in place of {@link QLiveMutation}. The method's name is the mutation's name. A parameter of a
/// table-backed domain type, or a `List` of one, is a subject: the request names its rows by id and version, and
/// the framework loads them and checks the versions before the method sees them. Every other parameter is input,
/// as on any mutation. The rules named by {@link Rules} are evaluated against the loaded rows and the input, and
/// the method is called only if all of them hold.
///
/// A refusal, from a rule or from the method itself, reaches the client as GraphQL errors and never together with
/// data -- see `io.github.qlivedev.qlive.api.action.ActionRefusedException`.
///
/// The mutation's description is the method's doc comment, which the build extracts into the type docs.
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QLiveAction
{
}
