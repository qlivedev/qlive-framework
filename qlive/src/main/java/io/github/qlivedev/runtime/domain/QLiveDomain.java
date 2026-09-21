package io.github.qlivedev.runtime.domain;

import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.DomainQLBuilder;
import de.quinscape.domainql.generic.DomainObject;
import de.quinscape.domainql.generic.DomainObjectScalar;
import de.quinscape.domainql.generic.GenericScalar;
import de.quinscape.domainql.generic.GenericScalarType;
import de.quinscape.domainql.jsonb.JSONB;
import de.quinscape.domainql.jsonb.JSONBScalar;
import de.quinscape.domainql.meta.MetadataProvider;
import de.quinscape.domainql.scalar.BigDecimalScalar;
import de.quinscape.domainql.scalar.BigIntegerScalar;
import io.github.qlivedev.model.QueryConfig;
import io.github.qlivedev.model.condition.CNode;
import io.github.qlivedev.runtime.scalar.ComputedValueCoercing;
import io.github.qlivedev.runtime.scalar.ComputedValue;
import io.github.qlivedev.runtime.scalar.ConditionType;
import io.github.qlivedev.runtime.scalar.FieldExpressionType;
import io.github.qlivedev.runtime.scalar.QueryConfigCoercing;
import graphql.schema.GraphQLScalarType;
import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;

/**
 * Builder helper to help standardize QLive GraphQL environments
 */
public class QLiveDomain
{
    private final static Logger log = LoggerFactory.getLogger(QLiveDomain.class);

    public static DomainQLBuilder newDomain(
        DSLContext dslContext,
        Collection<MetadataProvider> metadataProviders
    )
    {
        log.debug("Creating automaton domain: metadataProviders = {}", metadataProviders);

        return DomainQL.newDomainQL(dslContext)
            .withAdditionalScalar(DomainObject.class, DomainObjectScalar.newDomainObjectScalar())
            .withAdditionalScalar(JSONB.class, JSONBScalar.newScalar())
            .withAdditionalScalar(CNode.class, ConditionType.newConditionType())
            .withAdditionalScalar(CNode.class, FieldExpressionType.newFieldExpressionType())
            .withAdditionalScalar(GenericScalar.class, GenericScalarType.newGenericScalar())
            .withAdditionalScalar(BigDecimal.class, BigDecimalScalar.newScalar())
            .withAdditionalScalar(BigInteger.class, BigIntegerScalar.newScalar())

            .withAdditionalScalar(
                QueryConfig.class,
                GraphQLScalarType.newScalar()
                    .name("QueryConfig")
                    .description("Generalized query configuration")
                    .coercing(
                        new QueryConfigCoercing()
                    )
                    .build()
            )

            .withAdditionalScalar(
                ComputedValue.class,
                GraphQLScalarType.newScalar()
                    .name("ComputedValue")
                    .description("Encapsulates a dynamically evaluated FilterDSL value")
                    .coercing(
                        new ComputedValueCoercing()
                    )
                    .build()
            )

            .withMetadataProviders(
                metadataProviders.toArray(new MetadataProvider[0])
            );
    }
}
