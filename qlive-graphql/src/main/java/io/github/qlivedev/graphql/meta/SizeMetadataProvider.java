package io.github.qlivedev.graphql.meta;

import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import io.github.qlivedev.graphql.OutputType;
import io.github.qlivedev.graphql.PojoTypes;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.util.JSONUtil;
import jakarta.validation.constraints.Size;
import org.svenson.info.JSONClassInfo;
import org.svenson.info.JSONPropertyInfo;

/**
 * Writes the bounds of every {@link Size}-annotated property of a domain type into its field meta data, as
 * {@link DomainMeta#MAX_LENGTH} and {@link DomainMeta#MIN_LENGTH}.
 * <p>
 * jOOQ generates the annotation with a maximum on every POJO property backed by a column of limited length, so for
 * table-backed types this is the column length. Hand-written types carry whatever bounds they were given.
 * <p>
 * A bound the annotation leaves at its default says nothing and is not written: a minimum of 0 and a maximum of
 * {@link Integer#MAX_VALUE} are what a field without the annotation has anyway.
 */
public class SizeMetadataProvider
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
                if (!PojoTypes.isNormalProperty(propertyInfo))
                {
                    continue;
                }

                final Size sizeAnno = JSONUtil.findAnnotation(propertyInfo, Size.class);
                final String fieldName = propertyInfo.getJsonName();
                if (sizeAnno == null || objectType.getFieldDefinition(fieldName) == null)
                {
                    continue;
                }

                final DomainTypeMeta typeMeta = meta.getTypeMeta(typeName);
                if (sizeAnno.min() > 0)
                {
                    typeMeta.setFieldMeta(fieldName, DomainMeta.MIN_LENGTH, sizeAnno.min());
                }
                if (sizeAnno.max() < Integer.MAX_VALUE)
                {
                    typeMeta.setFieldMeta(fieldName, DomainMeta.MAX_LENGTH, sizeAnno.max());
                }
            }
        }
    }
}
