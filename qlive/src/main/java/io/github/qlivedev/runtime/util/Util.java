package io.github.qlivedev.runtime.util;

import io.github.qlivedev.runtime.QLiveException;
import io.github.qlivedev.graphql.QLiveDomain;
import io.github.qlivedev.graphql.GenericTypeReference;
import io.github.qlivedev.graphql.meta.DomainQLTypeMeta;
import io.github.qlivedev.model.QueryDocument;
import org.jooq.tools.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class Util
{
    private Util()
    {
        // no instances
    }

    private final static String APP_TYPE_PREFIX = "App";

    /// Returns the initials of the given name in snakeCase (e.g. "foo" -> "f", "fooType" -> "ft")
    public static String getInitials(String typeName)
    {
        if (StringUtils.isEmpty(typeName))
        {
            throw new IllegalArgumentException("Type name cannot be null or empty");
        }

        if (typeName.startsWith(APP_TYPE_PREFIX))
        {
            typeName = typeName.substring(APP_TYPE_PREFIX.length());

            if (typeName.isEmpty())
            {
                throw new IllegalArgumentException("Invalid use of application type prefix 'App' without suffix");
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < typeName.length(); i++)
        {
            final char c = typeName.charAt(i);
            if (i == 0 || Character.isUpperCase(c))
            {
                sb.append(Character.toLowerCase(c));
            }
        }
        return sb.toString();
    }


    public static boolean isQueryDocumentType(QLiveDomain domainQL, String name)
    {
        return findQueryDocumentType(domainQL, name).isPresent();
    }


    public static Set<Class<?>> getQueryDocumentRowTypes(QLiveDomain domainQL)
    {
        return domainQL.getMetaData().getGenericTypes()
            .stream()
            .filter(
                gt -> gt.getGenericType().equals(QueryDocument.class.getName())
            ).map(
                gt -> domainQL.getTypeRegistry().lookup(gt.getTypeParameters().getFirst()).getJavaType()
            )
            .collect(Collectors.toUnmodifiableSet());
    }


    public static Optional<GenericTypeReference> findQueryDocumentType(QLiveDomain domainQL, String name)
    {
        final List<GenericTypeReference> genericTypes = domainQL.getMetaData().getGenericTypes();

        return genericTypes.stream().filter(
            r -> r.getGenericType().equals(QueryDocument.class.getName()) &&
                r.getType().equals(name)
        ).findFirst();
    }


    /// Key the domain meta data holds its type meta data under. Not a constant of DomainQLMeta's own,
    /// which only names its addenda.
    private final static String TYPES = "types";


    /// The meta data of the given type, or `null` where the domain has none for it.
    ///
    /// Answers rather than throws, which is the reason to go through this rather than through [
    /// io.github.qlivedev.graphql.meta.DomainQLMeta#getTypeMeta(String)]: that one raises on an unknown name, and
    /// asking about a name that may be no type at all is what every reader of type meta data does.
    ///
    /// @param typeName  name of a GraphQL type, known or not
    @SuppressWarnings("unchecked")
    public static DomainQLTypeMeta typeMeta(QLiveDomain domainQL, String typeName)
    {
        final Map<String, DomainQLTypeMeta> types =
            (Map<String, DomainQLTypeMeta>) domainQL.getMetaData().getData().get(TYPES);

        return types.get(typeName);
    }
}
