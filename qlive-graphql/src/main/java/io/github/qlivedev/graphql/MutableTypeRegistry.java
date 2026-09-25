package io.github.qlivedev.graphql;

import io.github.qlivedev.graphql.annotation.GraphQLComputed;
import io.github.qlivedev.graphql.annotation.GraphQLField;
import io.github.qlivedev.graphql.config.RelationModel;
import io.github.qlivedev.graphql.scalar.ByteScalar;
import io.github.qlivedev.graphql.scalar.DateScalar;
import io.github.qlivedev.graphql.scalar.TimestampScalar;
import io.github.qlivedev.graphql.scalar.LongScalar;
import io.github.qlivedev.graphql.util.DegenerificationUtil;
import io.github.qlivedev.util.JSONUtil;
import graphql.Scalars;
import graphql.schema.GraphQLScalarType;
import org.jooq.Field;
import org.jooq.Table;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.svenson.info.JSONClassInfo;
import org.svenson.info.JSONPropertyInfo;
import org.svenson.info.JavaObjectPropertyInfo;

import java.lang.reflect.Method;
import java.sql.Date;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The registry as the schema build sees it: it registers types as well as answering questions about them.
 */
public class MutableTypeRegistry
    implements TypeRegistry
{
    private final static Logger log = LoggerFactory.getLogger(MutableTypeRegistry.class);

    /**
     * Default scalar types
     */
    private final static Map<Class<?>, GraphQLScalarType> JAVA_TYPE_TO_GRAPHQL;
    static
    {
        final Map<Class<?>, GraphQLScalarType> map = new HashMap<>();

        map.put(Boolean.class, Scalars.GraphQLBoolean);
        map.put(Boolean.TYPE, Scalars.GraphQLBoolean);
        map.put(Integer.class, Scalars.GraphQLInt);
        map.put(Integer.TYPE, Scalars.GraphQLInt);
        map.put(Double.class, Scalars.GraphQLFloat);
        map.put(Double.TYPE, Scalars.GraphQLFloat);
        map.put(String.class, Scalars.GraphQLString);
        map.put(Timestamp.class, TimestampScalar.newScalar());
        map.put(Date.class, DateScalar.newScalar());

        final GraphQLScalarType longScalar = LongScalar.newScalar();
        map.put(Long.class, longScalar);
        map.put(Long.TYPE, longScalar);

        final GraphQLScalarType byteScalar = ByteScalar.newScalar();

        map.put(Byte.class, byteScalar);
        map.put(Byte.TYPE, byteScalar);

        JAVA_TYPE_TO_GRAPHQL = Collections.unmodifiableMap(map);
    }

    private final Map<String, GraphQLScalarType> scalarTypeByName;

    private final Map<Class<?>, GraphQLScalarType> scalarTypeByClass;


    private final Map<TypeContext, InputType> inputTypes = new HashMap<>();

    private final Map<TypeContext, OutputType> outputTypes = new HashMap<>();

    private final Map<String, TableLookup> jooqTables = new HashMap<>();

    private final Map<String, TableLookup> jooqTablesRO = Collections.unmodifiableMap(jooqTables);

    private final Map<String, Field<?>> dbFieldLookup;

    private List<RelationModel> relationModels = Collections.emptyList();

    private Map<String, RelationModel> relationsBySourceField = Collections.emptyMap();

    private Map<String, RelationModel> relationsByTargetField = Collections.emptyMap();


    public MutableTypeRegistry(
        Map<Class<?>, GraphQLScalarType> additionalScalarTypes,
        Map<String, Field<?>> dbFieldLookup
    )
    {
        this.dbFieldLookup = dbFieldLookup;

        final Map<Class<?>, GraphQLScalarType> scalarTypeByClass = new HashMap<>(JAVA_TYPE_TO_GRAPHQL);
        scalarTypeByClass.putAll(additionalScalarTypes);

        this.scalarTypeByName = Collections.unmodifiableMap(mapByName(scalarTypeByClass));
        this.scalarTypeByClass = Collections.unmodifiableMap(scalarTypeByClass);
    }

    private Map<String, GraphQLScalarType> mapByName(Map<Class<?>, GraphQLScalarType> scalarTypeByClass)
    {
        final Map<String, GraphQLScalarType> map = new HashMap<>();

        for (GraphQLScalarType scalarType : scalarTypeByClass.values())
        {
            final String name = scalarType.getName();
            final GraphQLScalarType existing = map.put(name, scalarType);
            if (existing != null && existing != scalarType)
            {
                throw new DomainQLTypeException(
                    "Scalar name '" + name + "' is declared by both " +
                        scalarType + " (" + scalarType.getClass().getName() + ") and " +
                        existing + " (" + existing.getClass().getName() + "). " +
                        "Did you forget to rename a copy&pasted scalar?"
                );
            }
        }

        return map;
    }



    public InputType registerInput(TypeContext typeContext)
    {

        final Class<?> javaType = typeContext.getType();
        PojoTypes.ensurePojoType(javaType);


        final InputType existing = inputTypes.get(typeContext);
        if (existing != null)
        {
            return existing;
        }

        if (Enum.class.isAssignableFrom(javaType))
        {
            final InputType enumType = new InputType(javaType.getSimpleName(), typeContext);
            inputTypes.put(typeContext, enumType);
            return enumType;
        }

        final String inputTypeName = SchemaNames.getInputTypeName(typeContext.getTypeName());

        final InputType newType = new InputType(inputTypeName, typeContext);

        inputTypes.put(typeContext, newType);

        final Collection<Class<?>> actualTypeArguments = typeContext.getTypeArguments();
        for (Class<?> cls : actualTypeArguments)
        {
            registerInput(new TypeContext(typeContext, cls));
        }

        registerFields(
            newType,
            typeContext
        );

        return newType;
    }

    public OutputType register(TypeContext ctx)
    {
        final Class<?> javaType = ctx.getType();
        
        PojoTypes.ensurePojoType(javaType);

        final OutputType existing = outputTypes.get(ctx);
        if (existing != null)
        {
            ensureOverride(existing, javaType);
            return existing;
        }

        if (Enum.class.isAssignableFrom(javaType))
        {
            final OutputType enumType = new OutputType(ctx, javaType);
            outputTypes.put(ctx, enumType);
            return enumType;
        }


        final OutputType newType = new OutputType(ctx, javaType);

        outputTypes.put(ctx, newType);

        final Collection<Class<?>> actualTypeArguments = ctx.getTypeArguments();

        for (Class<?> cls : actualTypeArguments)
        {
            register(new TypeContext(ctx, cls));
        }

        registerFields(
            newType,
            ctx
        );

        return newType;
    }


    /**
     * Two classes share a domain type name only as an override: a hand-written class extending the generated POJO
     * whose name it takes, which is how an application adds computed fields to a generated type.
     * <p>
     * Unrelated classes of the same simple name are a collision instead. Entries are keyed by GraphQL type name, so
     * the second registration would return the first one's entry and the schema would declare the winner's fields
     * for a query whose resolver returns the loser -- something no caller can satisfy. Naming both classes is the
     * only useful thing to do with that.
     *
     * @param existing entry already registered under the name
     * @param javaType class being registered under it now
     */
    private void ensureOverride(OutputType existing, Class<?> javaType)
    {
        final Class<?> registered = existing.getJavaType();

        if (
            registered.equals(javaType) ||
            registered.isAssignableFrom(javaType) ||
            javaType.isAssignableFrom(registered)
        )
        {
            return;
        }

        throw new DomainQLTypeException(
            "Domain type '" + existing.getName() + "' is claimed by both " + registered.getName() + " and " +
                javaType.getName() + ". A class takes over another's name only by extending it, which is how a " +
                "hand-written class overrides a generated POJO. Rename one of the two."
        );
    }


    /**
     * Registers a table-backed domain type: the POJO the jOOQ generator produced for it, and the table itself.
     * <p>
     * The entry names whatever class holds the type name once registration is done, which is the hand-written class
     * where one overrides the generated POJO. There is no second map to keep in agreement about that.
     *
     * @param pojoType generated POJO for the table
     * @param table    jOOQ table
     *
     * @return output type registered for the domain type
     */
    public OutputType registerTable(Class<?> pojoType, Table<?> table)
    {
        final OutputType outputType = register(new TypeContext(null, pojoType));

        jooqTables.put(
            outputType.getName(),
            new TableLookup(outputType.getJavaType(), table)
        );

        return outputType;
    }


    /**
     * Takes over the domain's relations, resolving each against the types registered so far and indexing them by the
     * field they are reached through.
     *
     * @param relations relations as configured
     */
    public void registerRelations(List<RelationModel> relations)
    {
        final List<RelationModel> updated = new ArrayList<>(relations.size());
        final Map<String, RelationModel> bySourceField = new HashMap<>();
        final Map<String, RelationModel> byTargetField = new HashMap<>();

        for (RelationModel relation : relations)
        {
            final RelationModel resolved = relation.update(this);
            updated.add(resolved);

            if (resolved.getLeftSideObjectName() != null)
            {
                bySourceField.put(
                    relationKey(resolved.getSourceType(), resolved.getLeftSideObjectName()),
                    resolved
                );
            }
            if (resolved.getRightSideObjectName() != null)
            {
                byTargetField.put(
                    relationKey(resolved.getTargetType(), resolved.getRightSideObjectName()),
                    resolved
                );
            }
        }

        this.relationModels = Collections.unmodifiableList(updated);
        this.relationsBySourceField = Collections.unmodifiableMap(bySourceField);
        this.relationsByTargetField = Collections.unmodifiableMap(byTargetField);
    }


    private static String relationKey(String domainType, String fieldName)
    {
        return domainType + ":" + fieldName;
    }


    @Override
    public TableLookup lookupType(String domainType)
    {
        return jooqTables.get(domainType);
    }


    @Override
    public Map<String, TableLookup> getJooqTables()
    {
        return jooqTablesRO;
    }


    @Override
    public Field<?> lookupField(String domainType, String property)
    {
        return dbFieldLookup.get(DomainQLBuilder.fieldLookupKey(domainType, property));
    }


    @Override
    public List<RelationModel> getRelationModels()
    {
        return relationModels;
    }


    @Override
    public RelationModel lookupRelation(String sourceType, String fieldName)
    {
        return relationsBySourceField.get(relationKey(sourceType, fieldName));
    }


    @Override
    public RelationModel lookupBackReference(String targetType, String fieldName)
    {
        return relationsByTargetField.get(relationKey(targetType, fieldName));
    }


    @Override
    public InputType lookupInput(TypeContext typeContext)
    {
        return inputTypes.get(typeContext);
    }

    @Override
    public InputType lookupInput(String name)
    {

        for (InputType inputType : inputTypes.values())
        {
            if (inputType.getName().equals(name))
            {
                return inputType;
            }
        }
        return null;
    }


    @Override
    public OutputType lookup(String name)
    {
        for (OutputType outputType : outputTypes.values())
        {
            if (outputType.getName().equals(name))
            {
                return outputType;
            }

        }
        return null;
    }


    @Override
    public OutputType lookup(Class<?> cls)
    {
        for (OutputType outputType : outputTypes.values())
        {
            if (outputType.getJavaType().equals(cls))
            {
                return outputType;
            }

        }
        return null;
    }


    @Override
    public Collection<OutputType> getOutputTypes()
    {
        return outputTypes.values();
    }


    private void registerFields(
        ComplexType complexType,
        TypeContext parentContext
    )
    {
        final Class<?> javaType = complexType.getJavaType();

        final JSONClassInfo classInfo = JSONUtil.getClassInfo(javaType);
        for (JSONPropertyInfo info : classInfo.getPropertyInfos())
        {
            final Class<Object> type = info.getType();

            final GraphQLField graphQLFieldAnno = JSONUtil.findAnnotation(info, GraphQLField.class);
            final GraphQLComputed computedAnno = JSONUtil.findAnnotation(info, GraphQLComputed.class);

            if (!PojoTypes.isNormalProperty(info) || type.isArray() || computedAnno != null)
            {
                continue;
            }

            //registerNewOutputType.accept(complexType);

            final Method getterMethod = ((JavaObjectPropertyInfo) info).getGetterMethod();

            TypeContext ctx = new TypeContext(parentContext, getterMethod);

            final Class<?> nextType;
            if (List.class.isAssignableFrom(type))
            {
                nextType = DegenerificationUtil.getElementType(complexType, getterMethod);
                // create new type context for the element of the generic list
                ctx = new TypeContext(ctx, nextType);
            }
            else
            {
                ctx = DegenerificationUtil.getType(parentContext, complexType, getterMethod);
                nextType = ctx.getType();
            }

            if (getGraphQLScalarFor(nextType, graphQLFieldAnno) == null)
            {
                boolean isInput = complexType instanceof InputType;
                if (isInput)
                {
                    registerInput(ctx);
                }
                else
                {
                    register(ctx);
                }
            }
        }

        for (Method method : javaType.getMethods())
        {
            final Class<?>[] parameterTypes = method.getParameterTypes();
            final GraphQLField annotation = method.getAnnotation(GraphQLField.class);
            if (parameterTypes.length > 0 && annotation != null)
            {
                final Class<?> returnType = method.getReturnType();

                if (!Enum.class.isAssignableFrom(returnType) && getGraphQLScalarFor(returnType, null) == null)
                {
                    TypeContext ctx = new TypeContext(parentContext, method);
                    boolean isInput = complexType instanceof InputType;
                    if (isInput)
                    {
                        registerInput(ctx);
                    }
                    else
                    {
                        register(ctx);
                    }
                }
            }
        }
    }

    @Override
    public Collection<GraphQLScalarType> getScalarTypes()
    {
        return Collections.unmodifiableCollection(scalarTypeByName.values());
    }


    @Override
    public Collection<InputType> getInputTypes()
    {
        return inputTypes.values();
    }

    @Override
    public GraphQLScalarType getGraphQLScalarFor(Class<?> cls, GraphQLField inputAnno)
    {
        if (inputAnno != null && inputAnno.type().length() > 0)
        {
            return scalarTypeByName.get(inputAnno.type());
        }
        return scalarTypeByClass.get(cls);
    }


}
