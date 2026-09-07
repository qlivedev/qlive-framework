package com.dataciders.qlive.runtime.query.condition;

/// Converts the raw values inside a FilterDSL condition to their Java types.
///
/// A `Value` node arrives carrying whatever JSON held: `ConditionCoercing` deliberately does not convert on
/// the way in, only on the way out, so a timestamp is still a string here. The scalar type the node names
/// is what says how to read it.
public interface ScalarConverter
{
    /// @param scalarType   GraphQL scalar name the value was written with, e.g. "String" or "Timestamp"
    /// @param value        raw value
    ///
    /// @return converted value
    Object convert(String scalarType, Object value);
}
