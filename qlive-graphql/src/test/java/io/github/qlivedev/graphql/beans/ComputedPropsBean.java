package io.github.qlivedev.graphql.beans;

import io.github.qlivedev.graphql.annotation.GraphQLComputed;
import org.svenson.JSONProperty;

public class ComputedPropsBean
{
    private String value;


    public String getValue()
    {
        return value;
    }


    public void setValue(String value)
    {
        this.value = value;
    }


    /// Computed, with a getter and nothing else.
    @GraphQLComputed
    public String getValuePlus()
    {
        return "+" + value;
    }


    /// Computed and marked read-only to svenson, which is also what excludes a property from the schema.
    @GraphQLComputed
    @JSONProperty(readOnly = true)
    public String getValueMinus()
    {
        return "-" + value;
    }
}
