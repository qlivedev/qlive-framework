package com.dataciders.qlive.runtime.scalar;

import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.generic.GenericScalar;
import de.quinscape.domainql.generic.GenericScalarCoercing;
import de.quinscape.domainql.schema.DomainQLAware;
import graphql.schema.Coercing;
import graphql.schema.CoercingParseLiteralException;
import graphql.schema.CoercingParseValueException;
import graphql.schema.CoercingSerializeException;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Coerces {@link ComputedValue} to map, its JSON form, and back.
 */
public class ComputedValueCoercing
    implements Coercing<ComputedValue, Map<String, Object>>, DomainQLAware
{

    private GenericScalarCoercing genericScalarCoercing = null;

    @Override
    public Map<String, Object> serialize(Object result) throws CoercingSerializeException
    {
        if (!(result instanceof ComputedValue))
        {
            throw new IllegalArgumentException(result + " is not a ComputedValueScalar");
        }
        final ComputedValue scalar = (ComputedValue) result;
        final List<GenericScalar> args = scalar.getArgs();

        final HashMap<String, Object> map = new HashMap<>();
        map.put("name", scalar.getName());
        map.put("args", args.stream()
            .map(
                gs -> {
                    return genericScalarCoercing.serialize(gs);
                }
            )
            .collect(
                Collectors.toList()
            )
        );
        return map;
    }


    @Override
    public ComputedValue parseValue(Object input) throws CoercingParseValueException
    {

        if (!(input instanceof Map))
        {
            throw new CoercingParseValueException(
                "Cannot coerce " + input + " to ComputedValueScalar, must be map"
            );
        }
        final Map<String, Object> map = (Map<String, Object>) input;
        return new ComputedValue(
            (String)map.get("name"),
            ((List<Map<String,Object>>)map.get("args")).stream()
                .map(
                    genericScalarJSON -> {
                        return genericScalarCoercing.parseValue(genericScalarJSON);
                    }
                )
                .collect(
                    Collectors.toList()
                )
        );

    }


    @Override
    public ComputedValue parseLiteral(Object input) throws CoercingParseLiteralException
    {
        throw new CoercingParseLiteralException("Cannot coerce ComputedValueScalar from literal");
    }


    @Override
    public void setDomainQL(DomainQL domainQL)
    {
        this.genericScalarCoercing = new GenericScalarCoercing();
        this.genericScalarCoercing.setDomainQL(domainQL);
    }
}
