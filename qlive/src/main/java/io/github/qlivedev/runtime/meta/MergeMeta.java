package io.github.qlivedev.runtime.meta;

import io.github.qlivedev.runtime.util.Util;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.OutputType;
import io.github.qlivedev.graphql.meta.DomainTypeMeta;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/// What a type says about merging it, and how it is read back.
///
/// There are two levels to it and only the second one is written anywhere.
///
/// **Whether a type takes part is derived, not declared.** A type with a `version` field is versioned, and
/// that is the whole rule -- see {@link #isVersioned(QLiveDomain, String)}. Nothing registers a type and nothing
/// can forget to: the server reads the field off the schema and the client reads it off the same schema in
/// `config().typesByName`, so the two cannot disagree about who is in. Adding the column is how a type opts
/// in, dropping it is how it opts out.
///
/// **How a versioned type behaves is declared**, with a {@link MergeMetadataProvider} the application
/// registers as a bean, and lands in the type meta data under {@link #MERGE} as one map of the four
/// properties below. The split is not arbitrary: detecting a concurrent change and merging one that does not
/// overlap are always correct and therefore automatic, while handing a user two values and asking them to
/// choose is a decision about the application's UI and has to be asked for.
///
/// The client reads the same map through declaration merging on `DomainQLTypeMetaProps`, the way
/// `queryConfig` and `maxPageSize` already do.
public final class MergeMeta
{
    private MergeMeta()
    {
        // no instances
    }

    /// Name of the field that makes a type versioned, and which holds the id of the `app_version` record
    /// describing the state the row is in now. Automaton made this configurable and nobody ever configured
    /// it.
    public final static String VERSION = "version";

    /// Name of the type meta data property holding everything a type declares about merging it. Has to agree
    /// with `DomainQLTypeMetaProps` on the client, which declares the same name to TypeScript.
    ///
    /// One grouped property rather than four loose ones: the type meta data is a namespace an application
    /// extends with addenda of its own, and `resolve`, `autoMerge`, `ignoredFields` and `linkType` are words
    /// too ordinary to claim there one by one.
    public final static String MERGE = "merge";

    /// Key under {@link #MERGE}: the type opts in to resolving conflicts in the view. Off -- the default --
    /// a real conflict fails the write and the application is told which fields clashed.
    public final static String RESOLVE = "resolve";

    /// Key under {@link #MERGE}: whether a concurrent change that does not overlap ours is merged silently.
    /// Defaults to true where the key is absent, which is the case worth having the mechanism for.
    public final static String AUTO_MERGE = "autoMerge";

    /// Key under {@link #MERGE}: fields whose change neither marks the type's field mask nor ever counts as
    /// a conflict.
    public final static String IGNORED_FIELDS = "ignoredFields";

    /// Key under {@link #MERGE}: the type is a link table, for the link tables that carry fields of their
    /// own and are therefore not recognisable by their shape.
    public final static String LINK_TYPE = "linkType";


    /// Whether rows of the given type take part in conflict detection, i.e. whether the type has a
    /// {@link #VERSION} field.
    ///
    /// Nothing declares this and nothing can: the field is the declaration. A type without one is written
    /// last-write-wins through the same path, which is what not having the column means.
    ///
    /// @param typeName  name of a GraphQL type, known or not
    public static boolean isVersioned(QLiveDomain domain, String typeName)
    {
        final GraphQLNamedType type = domain.getGraphQLSchema().getTypeMap().get(typeName);

        return type instanceof GraphQLObjectType objectType &&
            objectType.getFieldDefinition(VERSION) != null;
    }


    /// Whether rows of the given Java type take part, which is how a service has a type in hand: it writes
    /// rows of a POJO and which GraphQL type that is is the domain's to say.
    ///
    /// @param javaType  a Java type, exposed by the domain or not
    public static boolean isVersioned(QLiveDomain domain, Class<?> javaType)
    {
        final OutputType outputType = domain.getTypeRegistry().lookup(javaType);

        return outputType != null && isVersioned(domain, outputType.getName());
    }


    /// The names of every versioned type of the domain, alphabetically. Derived on every call rather than
    /// cached -- a domain is built once and this is asked at startup.
    public static List<String> versionedTypes(QLiveDomain domain)
    {
        final List<String> names = new ArrayList<>();

        for (GraphQLNamedType type : domain.getGraphQLSchema().getTypeMap().values())
        {
            if (isVersioned(domain, type.getName()))
            {
                names.add(type.getName());
            }
        }

        names.sort(String::compareTo);

        return names;
    }


    /// Whether the type asked for conflicts to come back to the view with both values per field, rather than
    /// failing the write.
    ///
    /// @param typeName  name of a GraphQL type, known or not
    public static boolean resolvesConflicts(QLiveDomain domain, String typeName)
    {
        return Boolean.TRUE.equals(merge(domain, typeName).get(RESOLVE));
    }


    /// Whether a concurrent change that touched none of the fields we touched is merged without asking. True
    /// unless the type said otherwise, because a non-overlapping change is the case this whole mechanism
    /// exists to swallow.
    ///
    /// @param typeName  name of a GraphQL type, known or not
    public static boolean isAutoMerge(QLiveDomain domain, String typeName)
    {
        return !Boolean.FALSE.equals(merge(domain, typeName).get(AUTO_MERGE));
    }


    /// The fields of the type whose change is neither recorded nor ever a conflict, alphabetically. Empty
    /// where the type declared none.
    ///
    /// @param typeName  name of a GraphQL type, known or not
    @SuppressWarnings("unchecked")
    public static List<String> ignoredFields(QLiveDomain domain, String typeName)
    {
        final Object ignored = merge(domain, typeName).get(IGNORED_FIELDS);

        return ignored == null ? List.of() : List.copyOf((List<String>) ignored);
    }


    /// Whether the type was declared a link table. Only ever true for the link tables that carry fields
    /// beyond the two foreign keys, since a link table of the plain shape is recognized by that shape.
    ///
    /// @param typeName  name of a GraphQL type, known or not
    public static boolean isLinkType(QLiveDomain domain, String typeName)
    {
        return Boolean.TRUE.equals(merge(domain, typeName).get(LINK_TYPE));
    }


    /// Everything the given type declared about merging it, or an empty map where it declared nothing or is
    /// no type of the domain. Every reader above goes through this, so "declared nothing" and "unknown type"
    /// answer alike, which is what a caller asking about a type name off the wire needs.
    @SuppressWarnings("unchecked")
    private static Map<String, Object> merge(QLiveDomain domain, String typeName)
    {
        final DomainTypeMeta typeMeta = Util.typeMeta(domain, typeName);

        final Map<String, Object> declared = typeMeta == null ? null : typeMeta.getMeta(MERGE);

        return declared == null ? Collections.emptyMap() : declared;
    }
}
