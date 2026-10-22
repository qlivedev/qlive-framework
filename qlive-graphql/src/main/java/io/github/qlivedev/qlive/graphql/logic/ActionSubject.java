package io.github.qlivedev.qlive.graphql.logic;

/// A parameter of an action method that names stored rows: one row of a table-backed type, or a `List` of them.
///
/// @param name     the parameter's name, which is the mutation argument's name
/// @param index    position of the parameter in the method's parameter list
/// @param pojoType POJO class of the rows
/// @param list     whether the parameter is a `List` of rows rather than one row
public record ActionSubject(String name, int index, Class<?> pojoType, boolean list)
{
}
