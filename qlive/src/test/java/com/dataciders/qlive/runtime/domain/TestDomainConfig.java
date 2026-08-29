package com.dataciders.qlive.runtime.domain;

import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.config.SourceField;
import de.quinscape.domainql.config.TargetField;
import de.quinscape.domainql.meta.MetadataProvider;
import com.dataciders.qlive.testdomain.Public;

import java.util.Collection;
import java.util.Collections;

import static com.dataciders.qlive.testdomain.Tables.*;

public class TestDomainConfig
{
    public static DomainQL domainQL(Object... logicBeans)
    {
        Collection<MetadataProvider> metadataProviders = Collections.emptyList();
        return QLiveDomain.newDomain(null, metadataProviders)
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
