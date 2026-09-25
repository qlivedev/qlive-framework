package io.github.qlivedev.graphql;

/**
 * The names the schema assembly gives the types it builds.
 * <p>
 * Static because the builder and the type registry follow these conventions while there is no domain yet, and
 * because a client reading a schema has to know the same rules -- {@code GraphQLUtil} strips
 * {@link #INPUT_SUFFIX} back off to find the output type an input type was derived from.
 */
public final class SchemaNames
{
    /**
     * Appended to an output type's name to name the input type derived from it.
     */
    public static final String INPUT_SUFFIX = "Input";

    /**
     * Name of the schema's query root, which holds every query the domain declares.
     */
    public static final String QUERY_TYPE = "QueryType";

    /**
     * Name of the schema's mutation root, which holds every mutation the domain declares.
     */
    public static final String MUTATION_TYPE = "MutationType";


    private SchemaNames()
    {
        // no instances
    }


    /**
     * Names the input type derived from the given Java type. Enums are their own input type, having no fields
     * to differ in.
     *
     * @param parameterType     Java type
     *
     * @return GraphQL input type name
     */
    public static String getInputTypeName(Class<?> parameterType)
    {
        if (Enum.class.isAssignableFrom(parameterType))
        {
            return parameterType.getSimpleName();
        }

        return getInputTypeName(parameterType.getSimpleName());
    }


    /**
     * Names the input type derived from the given output type. Idempotent, so a name that already carries the
     * suffix is returned unchanged.
     *
     * @param outputTypeName    GraphQL output type name
     *
     * @return GraphQL input type name
     */
    public static String getInputTypeName(String outputTypeName)
    {
        if (outputTypeName.endsWith(INPUT_SUFFIX))
        {
            return outputTypeName;
        }
        else
        {
            return outputTypeName + INPUT_SUFFIX;
        }
    }
}
