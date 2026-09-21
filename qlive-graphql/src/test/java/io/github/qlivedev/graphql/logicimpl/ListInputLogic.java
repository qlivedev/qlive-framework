package io.github.qlivedev.graphql.logicimpl;

import io.github.qlivedev.graphql.annotation.GraphQLLogic;
import io.github.qlivedev.graphql.annotation.GraphQLMutation;
import io.github.qlivedev.graphql.beans.ComplexInput;
import io.github.qlivedev.graphql.beans.MyEnum;
import io.github.qlivedev.graphql.generic.DomainObject;

import jakarta.validation.constraints.NotNull;
import java.util.List;

@GraphQLLogic
public class ListInputLogic
{
    @GraphQLMutation
    public String testListOfDomainObjectScalars(
        @NotNull List<DomainObject> domainObjects
        )
    {
        return domainObjects.toString();
    }

    @GraphQLMutation
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

    @GraphQLMutation
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

    @GraphQLMutation
    public String testNullableListOfScalars(
        List<String> strings
    )
    {
        return strings != null ? strings.toString() : "null";
    }
}
