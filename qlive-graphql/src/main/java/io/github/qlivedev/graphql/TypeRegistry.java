package io.github.qlivedev.graphql;

import io.github.qlivedev.graphql.annotation.GraphQLField;
import graphql.schema.GraphQLScalarType;

import java.util.Collection;

/**
 * What the domain knows about its types, as everything outside the schema build sees it.
 * <p>
 * All lookups answer with <code>null</code> when they find nothing. Callers that need an error say so in their own
 * words rather than relying on the registry to throw.
 */
public interface TypeRegistry
{
    /**
     * Looks up the output type registered under the given GraphQL type name.
     *
     * @param name GraphQL type name
     *
     * @return output type or <code>null</code> if no type is registered under that name
     */
    OutputType lookup(String name);


    /**
     * Looks up the output type registered for the given Java class.
     *
     * @param cls Java type
     *
     * @return output type or <code>null</code> if no type is registered for that class
     */
    OutputType lookup(Class<?> cls);


    /**
     * Looks up the input type registered for the given type context.
     *
     * @param typeContext type context
     *
     * @return input type or <code>null</code> if no input type is registered for that context
     */
    InputType lookupInput(TypeContext typeContext);


    /**
     * Looks up the input type registered under the given GraphQL type name.
     *
     * @param name GraphQL input type name
     *
     * @return input type or <code>null</code> if no input type is registered under that name
     */
    InputType lookupInput(String name);


    /**
     * Returns all registered output types.
     *
     * @return output types
     */
    Collection<OutputType> getOutputTypes();


    /**
     * Returns all registered input types.
     *
     * @return input types
     */
    Collection<InputType> getInputTypes();


    /**
     * Returns all scalar types known to the domain, the built-in ones as well as those the application registered.
     *
     * @return scalar types
     */
    Collection<GraphQLScalarType> getScalarTypes();


    /**
     * Returns the scalar type a Java type maps to.
     *
     * @param cls       Java type
     * @param inputAnno field annotation naming a scalar by name, or <code>null</code> to map by class
     *
     * @return scalar type or <code>null</code> if the Java type is not scalar
     */
    GraphQLScalarType getGraphQLScalarFor(Class<?> cls, GraphQLField inputAnno);


    /**
     * Checks a given POJO type for override by resolving its simple name again.
     *
     * @param pojoClass POJO type to check
     *
     * @return overriding type, the identical type, or <code>null</code> if the name is not registered
     */
    Class<?> getOutputOverride(Class<?> pojoClass);
}
