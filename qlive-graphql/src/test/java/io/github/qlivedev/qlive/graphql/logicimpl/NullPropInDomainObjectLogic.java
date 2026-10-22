package io.github.qlivedev.qlive.graphql.logicimpl;

import io.github.qlivedev.qlive.api.annotation.QLiveLogic;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.api.generic.DomainObject;
import io.github.qlivedev.qlive.graphql.testdomain.tables.pojos.Bar;

@QLiveLogic
public class NullPropInDomainObjectLogic
{

    @QLiveQuery
    public DomainObject fetch(DomainObject in, String name)
    {
        final Bar foo = new Bar();

        foo.setName(name);
        foo.setId("38fb6d2b-4946-4b96-8912-bfe81cce2fc0");
        foo.setOwnerId("3ef7126b-ac62-4cb9-a01c-684eaeeb6b3a");

        return foo;
    }
}
