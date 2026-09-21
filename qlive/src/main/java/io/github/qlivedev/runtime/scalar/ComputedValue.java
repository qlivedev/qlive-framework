package io.github.qlivedev.runtime.scalar;

import de.quinscape.domainql.generic.GenericScalar;
import org.svenson.JSONTypeHint;

import java.util.Collections;
import java.util.List;

/**
 * Represents a dynamically evaluated FilterDSL value. (e.g. "now()")
 */
public class ComputedValue
{
    private String name;

    private List<GenericScalar> args;


    public ComputedValue()
    {
        this(null, Collections.emptyList());
    }
    public ComputedValue(String name, List<GenericScalar> args)
    {
        this.name = name;
        this.args = args;
    }


    public String getName()
    {
        return name;
    }


    public void setName(String name)
    {
        this.name = name;
    }


    public List<GenericScalar> getArgs()
    {
        return args;
    }


    @JSONTypeHint(GenericScalar.class)
    public void setArgs(List<GenericScalar> args)
    {
        this.args = args;
    }
}
