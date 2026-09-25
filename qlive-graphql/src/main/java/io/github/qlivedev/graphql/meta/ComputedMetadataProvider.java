package io.github.qlivedev.graphql.meta;

import io.github.qlivedev.graphql.PojoTypes;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.OutputType;
import io.github.qlivedev.graphql.annotation.GraphQLComputed;
import io.github.qlivedev.util.JSONUtil;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import org.svenson.info.JSONClassInfo;
import org.svenson.info.JSONPropertyInfo;

public class ComputedMetadataProvider
    implements MetadataProvider
{
    private static final String COMPUTED = "computed";

    @Override
    public void provideMetaData(QLiveDomain domainQL, DomainQLMeta meta)
    {
        final GraphQLSchema graphQLSchema = domainQL.getGraphQLSchema();

        for (GraphQLNamedType namedType : graphQLSchema.getTypeMap().values())
        {
            if (namedType instanceof GraphQLObjectType)
            {
                final String typeName = namedType.getName();
                final OutputType outputType = domainQL.getTypeRegistry().lookup(typeName);

                if (outputType != null)
                {
                    final JSONClassInfo classInfo = JSONUtil.getClassInfo(outputType.getJavaType());
                    final DomainQLTypeMeta typeMeta = meta.getTypeMeta(typeName);

                    for (JSONPropertyInfo propertyInfo : classInfo.getPropertyInfos())
                    {
                        if (!PojoTypes.isNormalProperty(propertyInfo))
                        {
                            continue;
                        }
                        final GraphQLComputed computedAnno = JSONUtil.findAnnotation(propertyInfo, GraphQLComputed.class);
                        if (computedAnno != null)
                        {
                            final String fieldName = propertyInfo.getJsonName();
                            final GraphQLFieldDefinition fieldDef =
                                ((GraphQLObjectType) namedType).getFieldDefinition(
                                fieldName);

                            if (fieldDef != null)
                            {
                                typeMeta.setFieldMeta(fieldName, COMPUTED, Boolean.TRUE);
                            }
                        }
                    }
                }
            }
        }
    }
}
