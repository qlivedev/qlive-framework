package io.github.qlivedev.graphql.docs;

import io.github.qlivedev.graphql.SchemaNames;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Reshapes lists of {@link TypeDoc} into the form the schema expects.
 *
 * Separate from {@link DocsExtractor} because this half is what runs at runtime:
 * {@code DomainQLBuilder} normalizes the typedocs it loads from resources, and doing
 * so must not drag javaparser onto the classpath of an application that only consumes
 * the generated JSON.
 */
public final class TypeDocs
{
    private TypeDocs()
    {
    }


    /**
     * Joins all {@link SchemaNames#QUERY_TYPE} and {@link SchemaNames#MUTATION_TYPE} into a single type each.
     * <p>
     * Ensures that type names and field names within the query / mutation types are unique
     * <p>
     * Sorts types and fields to stable, reproducible generation.
     *
     * @param typeDocs
     *
     * @return
     */
    public static List<TypeDoc> normalize(List<TypeDoc> typeDocs)
    {
        TypeDoc queryDoc = new TypeDoc(SchemaNames.QUERY_TYPE);
        TypeDoc mutationDoc = new TypeDoc(SchemaNames.MUTATION_TYPE);

        for (Iterator<TypeDoc> iterator = typeDocs.iterator(); iterator.hasNext(); )
        {
            TypeDoc typeDoc = iterator.next();

            if (typeDoc.getName().equals(SchemaNames.QUERY_TYPE))
            {
                queryDoc.getFieldDocs().addAll(typeDoc.getFieldDocs());
                iterator.remove();
            }
            else if (typeDoc.getName().equals(SchemaNames.MUTATION_TYPE))
            {
                mutationDoc.getFieldDocs().addAll(typeDoc.getFieldDocs());
                iterator.remove();
            }
        }

        if (queryDoc.getFieldDocs().size() > 0)
        {
            typeDocs.add(queryDoc);
        }
        if (mutationDoc.getFieldDocs().size() > 0)
        {
            typeDocs.add(mutationDoc);
        }


        ensureUniqueFields(queryDoc);
        ensureUniqueFields(mutationDoc);
        final List<TypeDoc> unique = mergeTypeDocs(typeDocs);

        // sort types by type name
        unique.sort(TypeDocComparator.INSTANCE);

        unique.forEach( doc -> doc.getFieldDocs().sort(FieldDocComparator.INSTANCE));

        return unique;
    }


    private static void ensureUniqueFields(TypeDoc mutationDoc)
    {
        Set<String> names = new HashSet<>();

        for (FieldDoc fieldDoc : mutationDoc.getFieldDocs())
        {
            final String name = fieldDoc.getName();
            if (names.contains(name))
            {
                throw new IllegalStateException("Field name not unique: '" + name + "'");
            }
            names.add(name);
        }
    }


    /**
     * Removes typedocs with duplicate names so that the last such type doc in iteration order remains.
     *
     * @return new set with unique names
     */
    private static List<TypeDoc> mergeTypeDocs(List<TypeDoc> typeDocs)
    {
        Map<String, TypeDoc> names = new LinkedHashMap<>();
        for (TypeDoc typeDoc : typeDocs)
        {
            final String name = typeDoc.getName();
            final TypeDoc existing = names.put(name, typeDoc);

            if (existing != null)
            {
                final Set<String> fieldNames = typeDoc.getFieldDocs()
                    .stream()
                    .map(FieldDoc::getName)
                    .collect(Collectors.toSet());

                for (FieldDoc fieldDoc : existing.getFieldDocs())
                {
                    if (!fieldNames.contains(fieldDoc.getName()))
                    {
                        final List<FieldDoc> existingFieldDocs = typeDoc.getFieldDocs();

                        if (existingFieldDocs instanceof ArrayList)
                        {
                            existingFieldDocs.add(fieldDoc);
                        }
                        else
                        {
                            final ArrayList<FieldDoc> fieldDocs = new ArrayList<>(existingFieldDocs);
                            fieldDocs.add(fieldDoc);
                            typeDoc.setFieldDocs(fieldDocs);
                        }
                    }
                }
            }
        }
        return new ArrayList<>(names.values());
    }
}
