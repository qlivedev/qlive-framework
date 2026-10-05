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

/**
 * Writes the precision and scale of every {@link BigDecimal} property of a domain type whose {@link Column} declares
 * them into its field meta data, as {@link DomainMeta#PRECISION} and {@link DomainMeta#SCALE}.
 * <p>
 * jOOQ generates the annotation with both on every POJO property backed by a <code>numeric(p, s)</code> column, so
 * for table-backed types these are the column's. Hand-written types carry whatever they were given.
 * <p>
 * A property whose column declares no precision is an unconstrained <code>numeric</code>, which holds any number of
 * digits on either side of the point, and gets neither. Where the precision is declared the scale is always written,
 * because a scale of 0 is a statement then: <code>numeric(10)</code> holds integers.
 * <p>
 * Only {@link BigDecimal}: the precision jOOQ writes on a timestamp is that of its fractional seconds, and on an
 * integer type a count of digits nothing on the client needs.
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
                if (!PojoTypes.isNormalProperty(propertyInfo) || !BigDecimal.class.equals(propertyInfo.getType()))
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
                typeMeta.setFieldMeta(fieldName, DomainMeta.SCALE, columnAnno.scale());
            }
        }
    }
}
