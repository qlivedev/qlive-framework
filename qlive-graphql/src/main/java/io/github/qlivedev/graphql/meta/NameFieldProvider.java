package io.github.qlivedev.graphql.meta;

import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeUtil;
import io.github.qlivedev.graphql.OutputType;
import io.github.qlivedev.graphql.PojoTypes;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.QLiveDomainTypeException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/// Writes which fields name an instance of a type to a user, most significant first -- the
/// {@link DomainMeta#NAME_FIELDS} a grid column of a to-one relation and a pick option show.
///
/// Opt-in and empty by default: an application that wants name fields registers this as a MetadataProvider
/// and says which types are named by what.
///
///     @Bean
///     public MetadataProvider nameFieldMetadata()
///     {
///         return NameFieldProvider.newProvider()
///             .forAllTypes()
///                 .nameFields("name")
///             .andForType(AppUser.class)
///                 .nameFields("login")
///             .andForType(Bar.class)
///                 .nameFields("name", "description")
///                 .build();
///     }
///
/// Which types a statement is about is said first -- {@link #forType(Class)}, {@link #forTypes(Class[])} or
/// {@link #forAllTypes()} -- and the name fields follow on the configurer that comes back. Chain the next
/// statement with its `andFor...` twin and close the last one with {@link NameFieldTypeConfigurer#build()},
/// which hands the provider back.
///
/// A name field is a scalar field of the type itself. Name fields represent a row's identity to a user, which
/// is the row's own business: a path like `"owner.name"` would name it by something a query might just as well
/// not select.
///
/// Nothing keeps an application from writing {@link DomainMeta#NAME_FIELDS} from a provider of its own -- this
/// is the convenient way to say it, not the only one. What reads it does not care who wrote it.
public class NameFieldProvider
    implements MetadataProvider
{
    private final static Logger log = LoggerFactory.getLogger(NameFieldProvider.class);

    /// What every type gets that has the fields and no statement of its own, or null where nothing said
    /// {@link #forAllTypes()}.
    private NameFieldTypeConfigurer allTypesConfigurer;

    /// Statements by Java type, whose GraphQL name only the built domain knows.
    private final Map<Class<?>, NameFieldTypeConfigurer> byJavaType = new LinkedHashMap<>();


    private NameFieldProvider()
    {
    }


    public static NameFieldProvider newProvider()
    {
        return new NameFieldProvider();
    }


    /// Declares what names one type, i.e. {@link #forTypes(Class[])} for the one type it usually is.
    ///
    /// A class the domain does not expose as a type, or a name field the type does not have, is reported when
    /// the domain is built.
    ///
    /// @return the configurer for that type
    public NameFieldTypeConfigurer forType(Class<?> javaType)
    {
        return forTypes(javaType);
    }


    /// Declares what names a number of types, all of them by the same fields.
    ///
    /// @param javaTypes  the types, at least one
    ///
    /// @return the configurer for those types
    public NameFieldTypeConfigurer forTypes(Class<?>... javaTypes)
    {
        if (javaTypes == null || javaTypes.length == 0)
        {
            throw new IllegalArgumentException("No types given");
        }

        final NameFieldTypeConfigurer configurer = new NameFieldTypeConfigurer(this);

        for (Class<?> cls : javaTypes)
        {
            PojoTypes.ensurePojoType(cls);

            // Two statements about one type are two opinions about it, and the second silently winning would be
            // the kind of thing an application finds out about in a browser.
            if (byJavaType.putIfAbsent(cls, configurer) != null)
            {
                throw new IllegalStateException("Name fields declared twice for " + cls.getSimpleName());
            }
        }

        return configurer;
    }


    /// Declares what names every type of the domain that has all the fields, which is the way round an
    /// application wants where most types are named the same way and the ones that are not are the exception.
    ///
    /// The fallback, not a requirement: a type that declares name fields of its own keeps them, and a type that
    /// lacks one of these fields is left without any rather than reported.
    ///
    /// Called more than once -- including through {@link NameFieldTypeConfigurer#andForAllTypes()} -- this goes
    /// on configuring the one all-types statement rather than starting a second.
    ///
    /// @return the configurer for all types
    public NameFieldTypeConfigurer forAllTypes()
    {
        if (allTypesConfigurer == null)
        {
            allTypesConfigurer = new NameFieldTypeConfigurer(this);
        }

        return allTypesConfigurer;
    }


    @Override
    public void provideMetaData(QLiveDomain domain, DomainMeta meta)
    {
        final GraphQLSchema schema = domain.getGraphQLSchema();
        final Map<String, List<String>> nameFieldsByType = new LinkedHashMap<>();

        for (Map.Entry<Class<?>, NameFieldTypeConfigurer> e : byJavaType.entrySet())
        {
            final Class<?> cls = e.getKey();
            final List<String> nameFields = e.getValue().getNameFields();
            if (nameFields == null)
            {
                continue;
            }

            final OutputType outputType = domain.getTypeRegistry().lookup(cls);
            if (outputType == null)
            {
                throw new QLiveDomainTypeException(
                    "Name fields declared for " + cls + ", which the domain does not expose as a type."
                );
            }

            final String typeName = outputType.getName();
            final GraphQLObjectType type = schema.getObjectType(typeName);
            if (type == null)
            {
                throw new QLiveDomainTypeException(
                    "Name fields declared for " + cls + ", whose type " + typeName + " is no object type of the " +
                        "schema."
                );
            }

            for (String name : nameFields)
            {
                if (!isScalarField(type, name))
                {
                    throw new QLiveDomainTypeException(
                        "Name field '" + name + "' declared for type " + typeName + ", which has no scalar field " +
                            "of that name."
                    );
                }
            }

            nameFieldsByType.put(typeName, nameFields);
        }

        final List<String> allTypesNameFields = allTypesConfigurer != null ? allTypesConfigurer.getNameFields() : null;
        if (allTypesNameFields != null)
        {
            for (GraphQLNamedType namedType : schema.getTypeMap().values())
            {
                final String typeName = namedType.getName();

                // the root types and introspection types are in the schema, but no type of the domain
                if (namedType instanceof GraphQLObjectType type &&
                    !nameFieldsByType.containsKey(typeName) &&
                    domain.getTypeRegistry().lookup(typeName) != null &&
                    allTypesNameFields.stream().allMatch(name -> isScalarField(type, name)))
                {
                    nameFieldsByType.put(typeName, allTypesNameFields);
                }
            }
        }

        for (Map.Entry<String, List<String>> e : nameFieldsByType.entrySet())
        {
            log.debug("Name fields of type {}: {}", e.getKey(), e.getValue());

            meta.getTypeMeta(e.getKey()).setMeta(DomainMeta.NAME_FIELDS, e.getValue());
        }
    }


    private static boolean isScalarField(GraphQLObjectType type, String name)
    {
        final GraphQLFieldDefinition fieldDef = type.getFieldDefinition(name);
        return fieldDef != null && GraphQLTypeUtil.unwrapNonNull(fieldDef.getType()) instanceof GraphQLScalarType;
    }
}
