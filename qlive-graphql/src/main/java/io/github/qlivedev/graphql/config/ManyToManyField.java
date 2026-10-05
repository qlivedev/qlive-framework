package io.github.qlivedev.graphql.config;

/// A many-to-many as one of its through fields sees it: the end the field is on and the end whose rows it lists.
///
/// @param model    the declaration
/// @param own      the end the field is on, e.g. Bar for `Bar.bazes`
/// @param other    the end the field lists the rows of, e.g. Baz for `Bar.bazes`
public record ManyToManyField(ManyToManyModel model, ManyToManyEnd own, ManyToManyEnd other)
{
    /// Name of the field.
    public String name()
    {
        return own.getField();
    }
}
