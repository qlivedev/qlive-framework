package io.github.qlivedev.runtime.query.condition;

/// Resolves the dotted field paths of a FilterDSL condition to database fields.
///
/// This is what makes {@link ConditionTransformer} usable outside a query document: a caller that just
/// wants to filter one table can resolve names against that table and be done, while the document query
/// resolves them against its query plan, where a path can cross joins and to-many relations.
public interface FieldResolver
{
    /// Resolves one path.
    ///
    /// @param path     dotted path as the FilterDSL writes it, e.g. `name` or `owner.login`
    ///
    /// @return the resolved field, never `null`
    ///
    /// @throws io.github.qlivedev.runtime.QLiveException  if the path names nothing the caller can
    ///                                                      resolve. Resolution failures are errors, never
    ///                                                      a silently dropped condition.
    ResolvedField resolve(String path);
}
