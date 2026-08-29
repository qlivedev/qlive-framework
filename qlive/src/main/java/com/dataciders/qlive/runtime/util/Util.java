package com.dataciders.qlive.runtime.util;

import de.quinscape.domainql.DomainQL;
import de.quinscape.domainql.GenericTypeReference;
import com.dataciders.qlive.model.QueryDocument;
import org.jooq.tools.StringUtils;

import java.util.List;
import java.util.Optional;

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


    public static boolean isQueryDocumentType(DomainQL domainQL, String name)
    {
        return findQueryDocumentType(domainQL, name).isPresent();
    }


    public static Optional<GenericTypeReference> findQueryDocumentType(DomainQL domainQL, String name)
    {
        final List<GenericTypeReference> genericTypes = domainQL.getMetaData().getGenericTypes();

        return genericTypes.stream().filter(
            r -> r.getGenericType().equals(QueryDocument.class.getName()) &&
                r.getType().equals(name)
        ).findFirst();
    }
}
