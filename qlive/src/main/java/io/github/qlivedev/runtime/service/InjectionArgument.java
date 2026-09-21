package io.github.qlivedev.runtime.service;

import io.github.qlivedev.runtime.QLiveException;
import graphql.schema.GraphQLFieldDefinition;

import java.util.List;

/// One static argument of a `useInjection()` call, as an {@link InjectionArgumentProcessor} is handed it.
///
/// The value is what the frontend build's track-usage analysis recorded, which is JSON: maps, lists,
/// strings, booleans, and numbers that arrive as `Long` and `Double` whatever the TypeScript said. Turning
/// that into what the GraphQL variable of {@link #typeName()} expects is the processor's job.
///
/// @param module    track-usage module name of the view declaring the injection, for error messages
/// @param variable  name of the GraphQL variable the value is passed as
/// @param typeName  name of the variable's declared type, unwrapped of `!` and `[]`
/// @param value     the recorded value, never `null`
/// @param usedAt    the fields of the query the variable is passed to, outermost first. A type alone does
///                  not always say what a value means -- what a query config starts out as depends on what
///                  is being queried, see {@link QueryConfigArgumentProcessor} -- and this is where the
///                  query says that. Empty where the variable is declared and never passed to a field.
public record InjectionArgument(
    String module,
    String variable,
    String typeName,
    Object value,
    List<GraphQLFieldDefinition> usedAt
)
{
    /// An exception saying that this argument is not what its type needs, naming where it sits.
    ///
    /// A processor runs while a page is being planned, i.e. long before anything could look at the value in
    /// a browser, so the only place the mistake is still visible is the call it was written in. Everything
    /// this can say about that is here, which is why rejecting goes through this rather than through a
    /// message each processor phrases its own way.
    ///
    /// @param reason  what is wrong with the value, as a sentence continuing "... in its 'x' parameter: "
    public QLiveException reject(String reason)
    {
        return new QLiveException(
            "Module '" + module + "' injects an invalid " + typeName + " in its '" + variable +
                "' parameter: " + reason
        );
    }
}
