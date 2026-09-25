package io.github.qlivedev.runtime.meta;

import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.OutputType;
import io.github.qlivedev.graphql.meta.DomainMeta;
import io.github.qlivedev.graphql.meta.MetadataProvider;
import graphql.schema.GraphQLObjectType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/// Writes what an application declares per type about merging it, i.e. the declaring end of
/// {@link MergeMeta}.
///
/// Opt-in and empty by default. An application that declares nothing still gets conflict *detection* and
/// auto-merge on every versioned type, because those are always right and are not anybody's decision; what
/// is declared here is everything that is:
///
///     @Bean
///     public MetadataProvider mergeMetadata()
///     {
///         return MergeMetadataProvider.newProvider()
///             .resolveConflicts(Bar.class)
///             .ignoreFields(Foo.class, "lastAccessed")
///             .autoMerge(Baz.class, false)
///             .linkType(CorgeLink.class);
///     }
///
/// Which types take part is not declared here and cannot be: a type with a `version` field is versioned, and
/// that is the whole rule. Everything but {@link #linkType} therefore has to be said about a versioned type
/// and is reported otherwise -- declaring how conflicts are resolved for a type that can have none is a
/// forgotten column, and it is much cheaper to hear about it at startup than to find the write silently
/// clobbering.
///
/// Nothing keeps an application from writing {@link MergeMeta#MERGE} from a provider of its own. This is the
/// convenient way to say it, not the only one, and what reads it does not care who wrote it.
public class MergeMetadataProvider
    implements MetadataProvider
{
    private final static Logger log = LoggerFactory.getLogger(MergeMetadataProvider.class);

    /// What one type declared, accumulated across the calls that named it. A property left null was never
    /// declared and is left out of the meta data, so that "not said" and "said to be the default" stay
    /// distinguishable to a reader.
    private static final class Declaration
    {
        private Boolean resolve;

        private Boolean autoMerge;

        private List<String> ignoredFields;

        private Boolean linkType;
    }

    /// Declarations by Java type, whose GraphQL name only the built domain knows.
    private final Map<Class<?>, Declaration> byJavaType = new LinkedHashMap<>();

    /// Declarations by GraphQL type name, in declaration order.
    private final Map<String, Declaration> byTypeName = new LinkedHashMap<>();


    private MergeMetadataProvider()
    {
    }


    public static MergeMetadataProvider newProvider()
    {
        return new MergeMetadataProvider();
    }


    /// Opts the type QLiveDomain exposes the given Java type as in to resolving conflicts in the view: a real
    /// conflict comes back with both values per field for the form the user was editing to offer, instead of
    /// failing the write.
    ///
    /// The way to say it where the application has the class: the GraphQL name of a domain type is QLiveDomain's
    /// to decide, and a class that turns out not to be in the schema is reported rather than written under a
    /// name nothing reads.
    public MergeMetadataProvider resolveConflicts(Class<?> javaType)
    {
        final Declaration declaration = declarationFor(javaType);

        declaration.resolve = declaredOnce(declaration.resolve, MergeMeta.RESOLVE, javaType);

        return this;
    }


    /// Opts the type of the given GraphQL name in to resolving conflicts in the view, for the types an
    /// application has no class at hand for.
    public MergeMetadataProvider resolveConflicts(String typeName)
    {
        final Declaration declaration = declarationFor(typeName);

        declaration.resolve = declaredOnce(declaration.resolve, MergeMeta.RESOLVE, typeName);

        return this;
    }


    /// Declares the fields of the type QLiveDomain exposes the given Java type as whose change is neither
    /// recorded in a version record nor ever a conflict -- a last-accessed timestamp, a counter, anything
    /// two users cannot meaningfully disagree about.
    ///
    /// @param fields  field names, as the GraphQL type spells them
    public MergeMetadataProvider ignoreFields(Class<?> javaType, String... fields)
    {
        final Declaration declaration = declarationFor(javaType);

        declaration.ignoredFields =
            declaredOnce(declaration.ignoredFields, MergeMeta.IGNORED_FIELDS, javaType, validFields(fields, javaType));

        return this;
    }


    /// Declares the ignored fields of the type of the given GraphQL name, for the types an application has
    /// no class at hand for.
    ///
    /// @param fields  field names, as the GraphQL type spells them
    public MergeMetadataProvider ignoreFields(String typeName, String... fields)
    {
        final Declaration declaration = declarationFor(typeName);

        declaration.ignoredFields =
            declaredOnce(declaration.ignoredFields, MergeMeta.IGNORED_FIELDS, typeName, validFields(fields, typeName));

        return this;
    }


    /// Declares whether a concurrent change to the type QLiveDomain exposes the given Java type as that touched
    /// none of the fields we touched is merged silently.
    ///
    /// True is what happens anyway. Declaring it false says that a user should see even a change that does
    /// not clash with theirs before it is folded into their save.
    public MergeMetadataProvider autoMerge(Class<?> javaType, boolean autoMerge)
    {
        final Declaration declaration = declarationFor(javaType);

        declaration.autoMerge =
            declaredOnce(declaration.autoMerge, MergeMeta.AUTO_MERGE, javaType, autoMerge);

        return this;
    }


    /// Declares the auto-merge behavior of the type of the given GraphQL name, for the types an application
    /// has no class at hand for.
    public MergeMetadataProvider autoMerge(String typeName, boolean autoMerge)
    {
        final Declaration declaration = declarationFor(typeName);

        declaration.autoMerge =
            declaredOnce(declaration.autoMerge, MergeMeta.AUTO_MERGE, typeName, autoMerge);

        return this;
    }


    /// Declares the type QLiveDomain exposes the given Java type as a link table, i.e. a row that exists to say
    /// two entities are associated.
    ///
    /// Only needed for the link tables carrying fields beyond the two foreign keys: one of the plain shape is
    /// recognized by that shape, on the client, out of the relation meta data it already has. The one
    /// statement here that says nothing about versioning, and the only one a type without a `version` field
    /// may make.
    public MergeMetadataProvider linkType(Class<?> javaType)
    {
        final Declaration declaration = declarationFor(javaType);

        declaration.linkType = declaredOnce(declaration.linkType, MergeMeta.LINK_TYPE, javaType);

        return this;
    }


    /// Declares the type of the given GraphQL name a link table, for the types an application has no class
    /// at hand for.
    public MergeMetadataProvider linkType(String typeName)
    {
        final Declaration declaration = declarationFor(typeName);

        declaration.linkType = declaredOnce(declaration.linkType, MergeMeta.LINK_TYPE, typeName);

        return this;
    }


    private Declaration declarationFor(Class<?> javaType)
    {
        return byJavaType.computeIfAbsent(javaType, ignored -> new Declaration());
    }


    private Declaration declarationFor(String typeName)
    {
        return byTypeName.computeIfAbsent(typeName, ignored -> new Declaration());
    }


    /// The value to store, having checked that this property was not already declared for this type. Several
    /// calls about one type accumulate, but the same call twice is a mistake worth naming: the second one
    /// either repeats the first or contradicts it, and neither reads as intended.
    private static <T> T declaredOnce(T current, String property, Object type, T value)
    {
        if (current != null)
        {
            throw new QLiveException(
                "Merge meta data '" + property + "' declared twice for " + type
            );
        }
        return value;
    }


    private static Boolean declaredOnce(Boolean current, String property, Object type)
    {
        return declaredOnce(current, property, type, Boolean.TRUE);
    }


    private static List<String> validFields(String[] fields, Object type)
    {
        if (fields.length == 0)
        {
            throw new QLiveException(
                "No fields named in the ignored fields declared for " + type + ". Declare none to ignore " +
                    "none, which is what a type that says nothing already does."
            );
        }

        final List<String> names = new ArrayList<>(List.of(fields));
        names.sort(String::compareTo);

        return names;
    }


    @Override
    public void provideMetaData(QLiveDomain domainQL, DomainMeta meta)
    {
        // Java types first, then names, so that a type declared through both is reported as declared twice
        // rather than half-written. The order within each is the application's.
        final Map<String, Declaration> byName = new LinkedHashMap<>();

        for (Map.Entry<Class<?>, Declaration> declared : byJavaType.entrySet())
        {
            byName.put(typeNameOf(domainQL, declared.getKey()), declared.getValue());
        }

        for (Map.Entry<String, Declaration> declared : byTypeName.entrySet())
        {
            if (byName.put(declared.getKey(), declared.getValue()) != null)
            {
                throw new QLiveException(
                    "Merge meta data declared for type '" + declared.getKey() + "' both by name and by class"
                );
            }
        }

        for (Map.Entry<String, Declaration> declared : byName.entrySet())
        {
            write(domainQL, meta, declared.getKey(), declared.getValue());
        }
    }


    /// The name the domain exposes the given Java type as.
    private static String typeNameOf(QLiveDomain domainQL, Class<?> javaType)
    {
        final OutputType outputType = domainQL.getTypeRegistry().lookup(javaType);
        if (outputType == null)
        {
            throw new QLiveException(
                "Merge meta data declared for " + javaType + ", which the domain does not expose as a " +
                    "type. Only a type that is in the schema can carry meta data."
            );
        }

        return outputType.getName();
    }


    private static void write(QLiveDomain domainQL, DomainMeta meta, String typeName, Declaration declaration)
    {
        requireType(domainQL, typeName);

        // Sorted, so that the written meta data reads the same however the application ordered its calls.
        final Map<String, Object> written = new TreeMap<>();

        if (declaration.resolve != null)
        {
            requireVersioned(domainQL, typeName, MergeMeta.RESOLVE);
            written.put(MergeMeta.RESOLVE, declaration.resolve);
        }

        if (declaration.autoMerge != null)
        {
            requireVersioned(domainQL, typeName, MergeMeta.AUTO_MERGE);
            written.put(MergeMeta.AUTO_MERGE, declaration.autoMerge);
        }

        if (declaration.ignoredFields != null)
        {
            requireVersioned(domainQL, typeName, MergeMeta.IGNORED_FIELDS);
            requireFields(domainQL, typeName, declaration.ignoredFields);
            written.put(MergeMeta.IGNORED_FIELDS, declaration.ignoredFields);
        }

        if (declaration.linkType != null)
        {
            written.put(MergeMeta.LINK_TYPE, declaration.linkType);
        }

        log.debug("Merge meta data of type {}: {}", typeName, written);

        meta.getTypeMeta(typeName).setMeta(MergeMeta.MERGE, written);
    }


    private static void requireType(QLiveDomain domainQL, String typeName)
    {
        if (domainQL.getTypeRegistry().lookup(typeName) == null)
        {
            // The type meta data only exists for the types QLiveDomain knows a Java type for, so this would
            // otherwise be meta data written nowhere -- or, for a name that is no type at all, a failure
            // phrased as the framework's rather than as the application's.
            throw new QLiveException(
                "Merge meta data declared for type '" + typeName + "', which is no type of the domain."
            );
        }
    }


    private static void requireVersioned(QLiveDomain domainQL, String typeName, String property)
    {
        if (!MergeMeta.isVersioned(domainQL, typeName))
        {
            throw new QLiveException(
                "Merge meta data '" + property + "' declared for type '" + typeName + "', which has no '" +
                    MergeMeta.VERSION + "' field and therefore takes no part in conflict detection. Add the " +
                    MergeMeta.VERSION + " column to the table, or drop the declaration."
            );
        }
    }


    private static void requireFields(QLiveDomain domainQL, String typeName, List<String> fields)
    {
        final GraphQLObjectType type =
            (GraphQLObjectType) domainQL.getGraphQLSchema().getTypeMap().get(typeName);

        for (String field : fields)
        {
            if (type.getFieldDefinition(field) == null)
            {
                throw new QLiveException(
                    "Ignored field '" + field + "' declared for type '" + typeName + "', which has no such " +
                        "field. A field name that matches nothing ignores nothing."
                );
            }
        }
    }
}
