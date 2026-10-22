package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveMutation;
import io.github.qlivedev.qlive.graphql.beans.ComplexInput;
import io.github.qlivedev.qlive.graphql.beans.MyEnum;
import io.github.qlivedev.qlive.api.generic.DomainObject;

import jakarta.validation.constraints.NotNull;
import java.util.List;

@QLiveLogic
public class ListInputLogic
{
    @QLiveMutation
    public String testListOfDomainObjectScalars(
        @NotNull List<DomainObject> domainObjects
        )
    {
        return domainObjects.toString();
    }

    @QLiveMutation
    public String testListOfDomainObjects(
        @NotNull List<ComplexInput> complexInputs
        )
    {
        StringBuilder sb = new StringBuilder();

        for (ComplexInput complexInput : complexInputs)
        {
            sb.append(complexInput).append("|");
        }
        return sb.toString();
    }

    @QLiveMutation
    public String testListOfEnums(
        @NotNull List<MyEnum> enums
        )
    {
        StringBuilder sb = new StringBuilder();

        for (MyEnum myEnum : enums)
        {
            sb.append(myEnum).append("|");
        }
        return sb.toString();
    }

    @QLiveMutation
    public String testNullableListOfScalars(
        List<String> strings
    )
    {
        return strings != null ? strings.toString() : "null";
    }
}
