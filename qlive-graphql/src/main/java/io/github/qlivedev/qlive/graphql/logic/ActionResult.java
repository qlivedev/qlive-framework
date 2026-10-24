package io.github.qlivedev.qlive.graphql.logic;

import java.util.Map;

/// What an action came to: the method's result, which is the mutation's data, and what the response carries
/// beside it in its top-level `extensions`.
///
/// @param data         what the action method returned
/// @param extensions   entries for the response's `extensions`, empty for none
public record ActionResult(Object data, Map<Object, Object> extensions)
{
    public ActionResult
    {
        extensions = Map.copyOf(extensions);
    }
}
