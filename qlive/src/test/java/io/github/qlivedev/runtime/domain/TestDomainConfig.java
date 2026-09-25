package io.github.qlivedev.runtime.domain;

import io.github.qlivedev.runtime.meta.QueryConfigMetadataProvider;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.config.SourceField;
import io.github.qlivedev.graphql.config.TargetField;
import io.github.qlivedev.graphql.meta.MetadataProvider;
import io.github.qlivedev.testdomain.Public;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

import static io.github.qlivedev.testdomain.Tables.*;

public class TestDomainConfig
{
    public static QLiveDomain domain(Object... logicBeans)
    {
        return domain(List.of(
            QueryConfigMetadataProvider.newProvider()
                .forAllTypes()
                .pageSize(5)
                .build()
        ), logicBeans);
    }

    public static QLiveDomain domainNoMeta(Object... logicBeans)
    {
        return domain(Collections.emptyList(), logicBeans);
    }


    /**
     * The same domain with meta data providers, for the tests that read what a provider wrote.
     */
    public static QLiveDomain domain(Collection<MetadataProvider> metadataProviders, Object... logicBeans)
    {
        return QLiveDefaultDomain.newDomain(null, metadataProviders)
            //.parameterProvider(new AutomatonConnectionProviderFactory(applicationContext))

            .logicBeans(logicBeans)

            .objectTypes(Public.PUBLIC)

//            .withAdditionalInputTypes(
//                Node.class,
//                Bar.class,
//                ValidationRules.class,
//                QuxMain.class,
//                QuxA.class,
//                QuxB.class,
//                QuxC.class,
//
//                Baz.class,
//                BazValue.class,
//                BazLink.class,
//
//                Corge.class,
//                CorgeAssoc.class,
//                CorgeLink.class,
//                CorgeType.class,
//
//                Grault.class,
//                Garply.class,
//                MetaConfig.class,
//                Waldo.class
//            )

            // configure object creation for schema relationships
            .configureRelation(TEST_FOO.OWNER_ID, SourceField.OBJECT_AND_SCALAR, TargetField.MANY)
            .configureRelation(TEST_FOO.TYPE, SourceField.OBJECT_AND_SCALAR, TargetField.NONE, "fooType", null)
            .configureNameField("name")


            .build();

    }
}
