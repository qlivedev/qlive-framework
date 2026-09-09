package com.dataciders.qlive.runtime.service;

/// Turns the static arguments of a `useInjection()` call into the GraphQL variables of one type.
///
/// The parameters of a useInjection() call are not GraphQL variables yet when they reach the injection: they
/// were TypeScript when the developer wrote them, and JSON by the time the analysis carried them across, and
/// nothing in between owes GraphQL anything. A processor is the last point in front of the execution where
/// that can be put right, which is why it is put right here rather than by widening what the coercing
/// downstream accepts -- the same value posted by a browser stays exactly as strict as it was.
///
/// An application contributes processors as beans; every {@link InjectionArgumentProcessor} bean is found.
/// The one that runs for a variable is the first whose {@link #handles(String)} says so, in bean order, so a
/// processor that means to take over a type the framework already handles -- `QueryConfig`, see
/// {@link QueryConfigArgumentProcessor} -- needs an `@Order` to say that it comes first. The framework's own
/// are registered at {@link org.springframework.core.Ordered#LOWEST_PRECEDENCE}.
///
/// Types are matched by name and by nothing else, so {@link #handles(String)} is free to answer for a whole
/// family of them: a processor for every query document of the domain asks
/// {@link com.dataciders.qlive.runtime.util.Util#isQueryDocumentType(de.quinscape.domainql.DomainQL, String)}
/// instead of naming any of them.
public interface InjectionArgumentProcessor
{
    /// Whether this processor handles variables of the given GraphQL type.
    ///
    /// @param typeName  name of the declared type, unwrapped of `!` and `[]` -- "QueryConfig" for
    ///                  `[QueryConfig!]!`
    boolean handles(String typeName);


    /// Produces the value the GraphQL variable is executed with.
    ///
    /// Called once per argument the call actually names -- a variable the call left out is left out, because
    /// a query that insists on one should report it missing rather than be handed a default nobody asked
    /// for. A list-typed variable is unwrapped first, so this always sees one element and never the list.
    ///
    /// @return the value to pass, which may be the recorded one unchanged
    ///
    /// @throws com.dataciders.qlive.runtime.QLiveException  when the value cannot be one, via
    ///                                                      {@link InjectionArgument#reject(String)}
    Object process(InjectionArgument argument);
}
