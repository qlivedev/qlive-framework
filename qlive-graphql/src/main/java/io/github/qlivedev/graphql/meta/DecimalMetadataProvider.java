package io.github.qlivedev.graphql.meta;

import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import io.github.qlivedev.graphql.OutputType;
import io.github.qlivedev.graphql.PojoTypes;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.util.JSONUtil;
import jakarta.persistence.Column;
import org.svenson.info.JSONClassInfo;
import org.svenson.info.JSONPropertyInfo;

import java.math.BigDecimal;
import java.math.BigInteger;

/**
 * Writes the precision of every {@link BigDecimal} and {@link BigInteger} property of a domain type whose
 * {@link Column} declares one into its field meta data, as {@link DomainMeta#PRECISION}, and for a {@link BigDecimal}
 * the scale as well, as {@link DomainMeta#SCALE}.
 * <p>
 * jOOQ generates the annotation on every POJO property backed by a <code>numeric(p, s)</code> column -- a
 * {@link BigDecimal}, or a {@link BigInteger} where the scale is 0 -- so for table-backed types these are the
 * column's. Hand-written types carry whatever they were given.
 * <p>
 * A property whose column declares no precision is an unconstrained <code>numeric</code>, which holds any number of
 * digits, and gets neither. Where a {@link BigDecimal}'s precision is declared its scale is always written, because a
 * scale of 0 is a statement then: <code>numeric(10)</code> holds integers. A {@link BigInteger} gets no scale, as it
 * cannot be anything but 0.
 * <p>
 * Only these two: the precision jOOQ writes on a timestamp is that of its fractional seconds.
 */
public class DecimalMetadataProvider
    implements MetadataProvider
{
    @Override
    public void provideMetaData(QLiveDomain domain, DomainMeta meta)
    {
        final GraphQLSchema graphQLSchema = domain.getGraphQLSchema();

        for (GraphQLNamedType namedType : graphQLSchema.getTypeMap().values())
        {
            if (!(namedType instanceof GraphQLObjectType objectType))
            {
                continue;
            }

            final String typeName = objectType.getName();
            final OutputType outputType = domain.getTypeRegistry().lookup(typeName);
            if (outputType == null)
            {
                continue;
            }

            final JSONClassInfo classInfo = JSONUtil.getClassInfo(outputType.getJavaType());
            for (JSONPropertyInfo propertyInfo : classInfo.getPropertyInfos())
            {
                final Class<?> type = propertyInfo.getType();
                if (!PojoTypes.isNormalProperty(propertyInfo) ||
                    !BigDecimal.class.equals(type) && !BigInteger.class.equals(type))
                {
                    continue;
                }

                final Column columnAnno = JSONUtil.findAnnotation(propertyInfo, Column.class);
                final String fieldName = propertyInfo.getJsonName();
                if (columnAnno == null || columnAnno.precision() <= 0 ||
                    objectType.getFieldDefinition(fieldName) == null)
                {
                    continue;
                }

                final DomainTypeMeta typeMeta = meta.getTypeMeta(typeName);
                typeMeta.setFieldMeta(fieldName, DomainMeta.PRECISION, columnAnno.precision());
                if (BigDecimal.class.equals(type))
                {
                    typeMeta.setFieldMeta(fieldName, DomainMeta.SCALE, columnAnno.scale());
                }
            }
        }
    }
}
