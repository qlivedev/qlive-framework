package io.github.qlivedev.graphql;

import io.github.qlivedev.graphql.annotation.GraphQLField;
import io.github.qlivedev.graphql.config.RelationModel;
import graphql.schema.GraphQLScalarType;
import org.jooq.Field;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * What the domain knows about its types, as everything outside the schema build sees it: the GraphQL type, the Java
 * class behind it, and -- where the type is one of the application's tables -- the jOOQ table, its columns and the
 * relations it takes part in.
 * <p>
 * All lookups answer with <code>null</code> when they find nothing, including the ones about tables and columns: a
 * registered type need not be table-backed, and a property need not be a column. Callers that need an error say so
 * in their own words rather than relying on the registry to throw.
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
     * Looks up the jOOQ table backing the given domain type, together with the POJO class the schema exposes it as.
     *
     * @param domainType domain type name
     *
     * @return table lookup, or <code>null</code> where the type has no table behind it -- a logic bean's return
     * type, an input type or an enum
     */
    TableLookup lookupType(String domainType);


    /**
     * Returns every table-backed domain type by name.
     *
     * @return read-only map of domain type names to table lookups
     */
    Map<String, TableLookup> getJooqTables();


    /**
     * Looks up the database column backing the given property of the given domain type.
     *
     * @param domainType domain type name
     * @param property   JSON property name
     *
     * @return column, or <code>null</code> where no column backs the property -- which is what a computed field is
     */
    Field<?> lookupField(String domainType, String property);


    /**
     * Returns all relations of the domain.
     *
     * @return relations
     */
    List<RelationModel> getRelationModels();


    /**
     * Looks up the relation reached from its source type through the given field.
     *
     * @param sourceType domain type the relation starts at
     * @param fieldName  name of the object field on that type
     *
     * @return relation, or <code>null</code> if that field is not a relation
     */
    RelationModel lookupRelation(String sourceType, String fieldName);


    /**
     * Looks up the relation reached backwards from its target type through the given field.
     *
     * @param targetType domain type the relation points at
     * @param fieldName  name of the back-reference field on that type
     *
     * @return relation, or <code>null</code> if that field is not a back reference
     */
    RelationModel lookupBackReference(String targetType, String fieldName);
}
