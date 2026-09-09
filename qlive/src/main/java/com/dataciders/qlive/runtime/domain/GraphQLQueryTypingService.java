package com.dataciders.qlive.runtime.domain;

import de.quinscape.domainql.DomainQL;
import com.dataciders.qlive.model.ts.ModuleFunctionReferences;
import com.dataciders.qlive.model.ts.TrackUsageData;
import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.util.Util;
import de.quinscape.spring.jsview.util.JSONUtil;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.InlineFragment;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.parser.Parser;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLType;
import graphql.schema.GraphQLTypeUtil;
import org.apache.commons.io.FileUtils;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Service that listens to changes to the track-usage data and generates new TypeScript types from the
 * detected GraphQL queries
 */
public class GraphQLQueryTypingService
{
    private final static Logger log = LoggerFactory.getLogger(GraphQLQueryTypingService.class);

    private final DomainQL domainQL;

    private final GraphQLSchema graphQLSchema;

    private final File tsSourcePath;

    /** npm package the generated result types import their QLive types from */
    final static String QLIVE_PACKAGE = "@quinscape/qlive-ts";

    /** Interface mixed into the result type of a query selecting a query document */
    final static String DOCUMENT_METHODS = "QueryDocumentMethods";

    /**
     * Finds the "export const <Name>" a tracked GraphQLQuery construction belongs to. The name is ASCII rather
     * than a full TypeScript identifier on purpose: it doubles as the query's name, and a GraphQL Name is
     * {@code [_A-Za-z][_0-9A-Za-z]*}. Upper case first, since query names are type-like.
     */
    final static Pattern RE_VAR_NAME =
        Pattern.compile(".*((export)\\s+?const\\s+([A-Z][A-Za-z0-9_$]*))\\s*=.*", Pattern.DOTALL);

    final static Pattern RE_TYPE_PARAM = Pattern.compile("^new GraphQLQuery<(.*)>");

    /** Finds the named imports of an existing import from {@link #QLIVE_PACKAGE}, so we can join them */
    final static Pattern RE_QLIVE_IMPORT = Pattern.compile(
        "import\\s+(?:type\\s+)?\\{([^}]*)}\\s*from\\s*[\"']" + Pattern.quote(QLIVE_PACKAGE) + "[\"']"
    );

    /** Finds {@link #DOCUMENT_METHODS} among already imported names */
    final static Pattern RE_DOCUMENT_METHODS_IMPORTED = Pattern.compile("\\b" + DOCUMENT_METHODS + "\\b");

    public GraphQLQueryTypingService(DomainQL domainQL, File tsSourcePath)
    {
        log.trace("Create GraphQLQueryTypingService");

        this.domainQL = domainQL;
        this.graphQLSchema = domainQL.getGraphQLSchema();
        this.tsSourcePath = tsSourcePath;
    }


    /**
     * Updates the TS query sources of the modules in the given track usage data.
     * <p>
     * Only those modules are looked at, so in dev -- where the Vite plugin pushes an editing round's changed
     * modules and does the debouncing -- this is the work that one save actually caused.
     *
     * @param refs track usage data, whole or a slice of it
     */
    public void updateGraphQLQueryTypes(TrackUsageData refs)
    {
        final Map<String, ModuleFunctionReferences> fnRefs = refs.getModuleFunctionReferences();

        for (Map.Entry<String, ModuleFunctionReferences> e : fnRefs.entrySet())
        {
            final String modulePath = e.getKey();
            final ModuleFunctionReferences modFnRef = e.getValue();

            final SelectionInfo selectionInfo = analyzeGraphQLQuery(modFnRef, modulePath);
            final List<SelectionTypeNode> selectedOperations = selectionInfo.selectedOperations;

            if (!selectedOperations.isEmpty())
            {
                final DocumentContext ctx = selectionInfo.ctx;
                final QueryInfo result = selectionInfo.result;


                String rootTypeName = result.rootTypeName();
                boolean allComplete = result.allComplete();
                log.trace("{}: {}, complete = {}", rootTypeName, selectedOperations, allComplete);

                final String tsCode = renderResultType(selectedOperations);

                final String source = readModule(ctx.modulePath);
                final ModuleInfo moduleInfo = analyzeModule(ctx, source);

                String updatedSource = null;
                if (moduleInfo != null)
                {
                    updatedSource = renderModule(
                        moduleInfo,
                        tsCode,
                        isQueryDocumentResult(selectedOperations.getFirst())
                    );
                }

                if (updatedSource == null)
                {
                    log.debug("Module '{}' seems to be invalid TypeScript at this point. Ignoring it.", ctx.modulePath);
                }
                else if (!source.equals(updatedSource))
                {
                    log.debug("Updating module source for module '{}'", ctx.modulePath);
                    log.trace("New source: {}", updatedSource);

                    writeModule(ctx.modulePath, updatedSource);
                }
                else
                {
                    log.debug("Module '{}' is already up-to-date", ctx.modulePath);
                }
            }
        }
    }

    SelectionInfo analyzeGraphQLQuery(ModuleFunctionReferences modFnRef, String modulePath)
    {
        final List<List<?>> calls = modFnRef.getCalls(ModuleFunctionReferences.GRAPHQL_QUERY_CONSTRUCTOR_NAME);
        final List<List<?>> indexes = modFnRef.getIndexes(ModuleFunctionReferences.GRAPHQL_QUERY_CONSTRUCTOR_NAME);
        if (!calls.isEmpty())
        {
            for (int i = 0; i < calls.size(); i++)
            {
                List<?> call = calls.get(i);

                if (call.isEmpty() || !(call.getFirst() instanceof String query))
                {
                    log.warn(
                        "Ignoring GraphQLQuery construction #{} in module '{}': the query was not recorded as a " +
                            "static string. Pass the query as a string or template literal without expressions.",
                        i,
                        modulePath
                    );
                    continue;
                }

                // Without the source offsets of the constructor call we cannot patch the result type back into
                // the module, so there is nothing useful to do for this module.
                List<?> index = i < indexes.size() ? indexes.get(i) : Collections.emptyList();
                if (index.size() < 2)
                {
                    log.warn(
                        "No source index recorded for GraphQLQuery construction #{} in module '{}'. Enable the " +
                            "'indexes' option of babel-plugin-track-usage to generate query types.",
                        i,
                        modulePath
                    );
                    continue;
                }

                int start = ((Long) index.get(0)).intValue();
                int end = ((Long) index.get(1)).intValue();

                Parser parser = new Parser();
                Document document = parser.parseDocument(query);

                log.trace("{}: {}", modulePath, document);

                final List<OperationDefinition> definitions =
                    document.getDefinitionsOfType(OperationDefinition.class);
                if (!definitions.isEmpty())
                {
                    List<SelectionTypeNode> selectedOperations = new ArrayList<>();

                    DocumentContext ctx = new DocumentContext(modulePath, start, end, definitions);

                    // A document holds queries or mutations, not both: it is analyzed as queries first and
                    // only read as mutations when it selects none.

                    QueryInfo result = analyzeQuery(
                        ctx,
                        selectedOperations,
                        true
                    );

                    if (selectedOperations.isEmpty())
                    {
                        result = analyzeQuery(
                            ctx,
                            selectedOperations,
                            false
                        );
                    }

                    if (!selectedOperations.isEmpty())
                    {
                        return new SelectionInfo(
                            ctx,
                            result,
                            selectedOperations
                        );
                    }
                }
            }
        }
        return new SelectionInfo(null, null, Collections.emptyList());
    }


    /**
     * <p>
     *      Analyzes a root type for selected operations. Gets called twice, once for queries and once for mutations
     * </p>
     * <p>
     *     This is the first pass traversal that collects information about all selections.
     * </p>
     *
     * @param ctx                   document context
     * @param selectedOperations    selected operations
     * @param processQueries        true if we should process queries, false for mutations
     *
     * @return query info
     */
    private QueryInfo analyzeQuery(
        DocumentContext ctx,
        List<SelectionTypeNode> selectedOperations,
        boolean processQueries
    )
    {
        final GraphQLObjectType rootType =
            processQueries ? graphQLSchema.getQueryType() : graphQLSchema.getMutationType();
        if (rootType == null)
        {
            // A schema without a mutation type is a schema whose queries are all this pass can find.
            return new QueryInfo(null, true);
        }
        final String rootTypeName = rootType.getName();

        boolean allComplete = true;
        for (OperationDefinition definition : ctx.definitions)
        {

            final SelectionSet selectionSet = definition.getSelectionSet();
            final String where = ctx.modulePath() + ", " +
                definition.getOperation().name().toLowerCase() + " " +
                (definition.getName() != null ? definition.getName() : "<unnamed>");

            for (Selection<?> s : selectionSet.getSelections())
            {
                refuseFragment(where, s, rootType.getName());
            }

            final Selection selection = selectionSet.getSelections().get(0);

            if (selection instanceof Field field)
            {
                final String fieldName = field.getName();
                final String fieldAlias = field.getAlias();
                final GraphQLFieldDefinition fieldDef = rootType.getFieldDefinition(fieldName);

                if (fieldDef == null)
                {
                    continue;
                }

                if (
                    processQueries && (definition.getOperation() == OperationDefinition.Operation.MUTATION) ||
                        !processQueries && (definition.getOperation() == OperationDefinition.Operation.QUERY)
                )
                {
                    continue;
                }

                TSResult result = follow(where, field, rootTypeName, 1);
                selectedOperations.add(new SelectionTypeNode(
                    rootType.getName(), field,
                    fieldDef.getType(),
                    result.selectedFields, result.complete && fieldAlias == null
                ));

                if (!result.complete)
                {
                    allComplete = false;
                }
            }
        }
        return new QueryInfo(rootTypeName, allComplete);

    }


    /**
     * Recursively follows the GraphQL types selected by the current operation.
     *
     * @param where    module and operation the field belongs to, for error messages
     * @param field    selected GraphQL node
     * @param typeName current type name
     * @param level    recursion level
     *
     * @return TS result
     */
    private TSResult follow(String where, Field field, String typeName, int level)
    {
        final GraphQLObjectType type = (GraphQLObjectType) graphQLSchema.getType(typeName);
        if (type == null)
        {
            throw new IllegalStateException("Type not found: " + typeName);
        }
        final String fieldName = field.getName();
        final String fieldAlias = field.getAlias();
        final GraphQLFieldDefinition fieldDef = type.getField(fieldName);
        if (fieldDef == null)
        {
            throw new IllegalStateException(type.getName() + " has no Field '" + fieldName + "'");
        }
        final GraphQLOutputType fieldType = (GraphQLOutputType) GraphQLTypeUtil.unwrapAll(fieldDef.getType());

        List<SelectionTypeNode> selectedFields = new ArrayList<>();
        boolean allComplete = true;
        // Scalars and enums both end the traversal: they carry no selection set to follow.
        if (!(fieldType instanceof GraphQLObjectType))
        {
            selectedFields.add(new SelectionTypeNode(
                typeName, field,
                fieldDef.getType(),
                Collections.emptyList(), fieldAlias == null
            ));
            log.trace("{}Field {}", indent(level), fieldAlias);
        }
        else
        {
            final GraphQLObjectType objectType = (GraphQLObjectType) fieldType;

            final SelectionSet selectionSet = field.getSelectionSet();
            if (selectionSet != null)
            {
                final List<Selection> selections = selectionSet.getSelections();

                if (!selections.isEmpty())
                {
                    for (Selection selection : selections)
                    {
                        refuseFragment(where, selection, objectType.getName());

                        if (selection instanceof Field kidField)
                        {
                            TSResult result = follow(where, kidField, objectType.getName(), level + 1);

                            final GraphQLOutputType kidsType = objectType.getField(
                                kidField.getName()).getType();
                            if (!result.complete)
                            {
                                allComplete = false;
                            }

                            selectedFields.add(new SelectionTypeNode(
                                objectType.getName(), kidField,
                                kidsType,
                                result.selectedFields, allComplete && kidField.getAlias() == null
                            ));
                        }
                    }
                }
            }

            if (!fieldsMatch(objectType, selectedFields))
            {
                allComplete = false;
            }
            if (!selectedFields.isEmpty())
            {
                log.trace(
                    "{}Object {}: {}, complete = {}",
                    indent(level),
                    objectType.getName(),
                    selectedFields,
                    allComplete
                );

                return new TSResult(selectedFields, allComplete);
            }
        }

        return new TSResult(selectedFields, allComplete);
    }

    /**
     * Refuses a selection that is a fragment spread or an inline fragment.
     * <p>
     * Their fields would be dropped from the generated result type without a word, and
     * the conversion map the frontend builds from the same query refuses them as well.
     * Supporting them means teaching both walkers to resolve fields in the type a
     * fragment is conditioned on, so until that happens, saying so beats a result type
     * that quietly misses half the selection.
     *
     * @param where     module and operation, for the message
     * @param selection selection to check
     * @param typeName  type the selection sits in
     */
    private static void refuseFragment(String where, Selection<?> selection, String typeName)
    {
        if (selection instanceof Field)
        {
            return;
        }

        throw new QLiveException(
            where + ": fragments are not supported, found " +
                (selection instanceof InlineFragment ? "an inline fragment" : "a fragment spread") +
                " in " + typeName
        );
    }


    /**
     * Renders the result type of a query, that is the type of the value one execution
     * of it yields.
     * <p>
     * That is the type of its single top-level selection, not an object keyed by that
     * selection's result key: GraphQLQuery&lt;T&gt; promises T for one execution, and
     * both inject() and execute() hand the value of the selection over unwrapped.
     *
     * @param selectedOperations selected operations, of which the first is the one the
     *                           result type describes
     *
     * @return TS code expression
     */
    String renderResultType(List<SelectionTypeNode> selectedOperations)
    {
        final SelectionTypeNode operation = selectedOperations.get(0);
        final String typeName = operation.getFieldTypeName();
        final GraphQLType fieldType = GraphQLTypeUtil.unwrapAll(operation.fieldType());

        String rendered;
        if (fieldType instanceof GraphQLObjectType objectType)
        {
            rendered = renderType(
                typeName,
                operation.selectedKids(),
                fieldsMatch(objectType, operation.selectedKids()),
                false,
                0
            );
        }
        else
        {
            // a scalar or enum valued method has no selection set describing it
            rendered = typeName;
        }

        return operation.isList() ? "Array<" + rendered + ">" : rendered;
    }


     /**
     * Renders the type expression for a selected, potentially aliased node
     *
     * @param typeName       containing type
     * @param selectedFields selected fields
     * @param allComplete    true if the containing type is complete
     * @param level          recursion level
     *
     * @return TS code expression
     */
    String renderType(
        String typeName,
        List<SelectionTypeNode> selectedFields,
        boolean allComplete,
        boolean isList,
        int level
    )
    {
        if (allComplete)
        {
            return typeName;
        }
        else
        {
            GraphQLObjectType type = (GraphQLObjectType) graphQLSchema.getType(typeName);
            if (type == null)
            {
                throw new IllegalStateException("GraphQL Type not found: " + typeName);
            }

            StringBuilder tb = new StringBuilder();

            final List<SelectionTypeNode> completed = selectedFields.stream().filter(sf -> sf.complete).toList();
            if (!completed.isEmpty())
            {
                tb.append("Pick<")
                    .append(completed.get(0).type)
                    .append(",")
                    .append(renderPickFields(selectedFields))
                    .append(">"
                    );
            }

            if (selectedFields.stream().anyMatch(sf -> !sf.complete))
            {
                // The intersection only has two sides when the picked half is there: everything
                // selected being aliased or incomplete leaves the redefinition standing on its own.
                if (tb.length() > 0)
                {
                    tb.append(" & ");
                }
                tb.append(renderPickRest(typeName, selectedFields, level));
            }

            return isList ? "Array<" + tb + ">" : tb.toString();
        }
    }


    /**
     * Handles the redefinition of un-picked fields within a type. That's the part that gets added with <code>& { xxx
     * : ... }</code>
     *
     * @param typeName       containing type
     * @param selectedFields selected fields
     * @param level          recursion level
     *
     * @return TS code term
     */
    private String renderPickRest(final String typeName, final List<SelectionTypeNode> selectedFields, int level)
    {
        GraphQLObjectType type = (GraphQLObjectType) graphQLSchema.getType(typeName);
        if (type == null)
        {
            throw new IllegalStateException("GraphQL Type not found: " + typeName);
        }

        StringBuilder rb = new StringBuilder();
        rb.append("{\n");
        rb.append(selectedFields.stream()
            .filter(selectedField -> !selectedField.complete)
            .map(selectedField -> {

                final GraphQLType gqlFieldType = graphQLSchema.getType(selectedField.getFieldTypeName());
                final GraphQLType fieldType = GraphQLTypeUtil.unwrapAll(gqlFieldType);

                if (fieldType instanceof GraphQLObjectType selectedFieldType)
                {
                    return indent(level + 1) + selectedField.getAliasedName() + (!selectedField.isNonNull() ? "?" : "") + " : " + renderType(
                        selectedField.getFieldTypeName(),
                        selectedField.selectedKids,
                        fieldsMatch(selectedFieldType, selectedField.selectedKids),
                        selectedField.isList(),
                        level + 1
                    );
                }
                else
                {
                    return indent(level + 1) + selectedField.getAliasedName() + (!selectedField.isNonNull() ? "?" : "") + " : " + selectedField.getFieldTypeName();
                }
            })
            .collect(Collectors.joining(",\n"))
        );
        rb.append("\n");
        rb.append(indent(level));
        rb.append("}");
        return rb.toString();
    }


    /**
     * Returns true if the given operation selects a query document, that is one of the GraphQL types
     * derived from QueryDocument&lt;T&gt;, e.g. FooDocument.
     * <p>
     * Those arrive in the application as QueryDocument instances, not as the plain JSON objects they
     * are on the wire, which is what earns their result type the {@link #DOCUMENT_METHODS} mix-in.
     *
     * @param operation selected operation
     *
     * @return true if the operation yields a query document
     */
    private boolean isQueryDocumentResult(SelectionTypeNode operation)
    {
        // A list of documents is not a document, and nothing produces one -- so we stay on the
        // safe side of a type that would promise methods the values do not have.
        return !operation.isList() && Util.isQueryDocumentType(domainQL, operation.getFieldTypeName());
    }


    /**
     * Renders the updated module source with the given result type.
     *
     * @param moduleInfo      analyzed module
     * @param tsCode          result type expression as rendered by {@link #renderResultType(List)}
     * @param isQueryDocument true if the query selects a query document
     *
     * @return new TS source of the module
     */
    static String renderModule(ModuleInfo moduleInfo, String tsCode, boolean isQueryDocument)
    {
        String prologue;

        final String typeName = moduleInfo.variableName() + "Result";

        String resultType = tsCode;
        if (isQueryDocument)
        {
            // The document methods are parameterized with the result type itself, so an updated
            // document is typed exactly like the one it came from -- selection and all.
            resultType += " & " + DOCUMENT_METHODS + "<" + typeName + ">";
        }

        final String tsTypeDef = "export type " + typeName + " = " + resultType;

        prologue = (isQueryDocument ? withDocumentMethodsImport(moduleInfo.prologue()) : moduleInfo.prologue()) +
            tsTypeDef + "\n\n";

        return prologue + moduleInfo.leftSideOfDefinition + " = " + moduleInfo.graphQLQueryDefinition + moduleInfo.epilogue();
    }


    /**
     * Returns the given module prologue with {@link #DOCUMENT_METHODS} imported from {@link #QLIVE_PACKAGE}.
     * <p>
     * The generated result type is the only place referring to that name, so the user should not have to
     * keep an import for it around. An existing import from the QLive package takes the name in, otherwise
     * a new import is prepended.
     *
     * @param prologue module source in front of the generated result type
     *
     * @return prologue importing the document methods
     */
    static String withDocumentMethodsImport(String prologue)
    {
        final Matcher matcher = RE_QLIVE_IMPORT.matcher(prologue);
        if (!matcher.find())
        {
            return "import { " + DOCUMENT_METHODS + " } from \"" + QLIVE_PACKAGE + "\";\n" + prologue;
        }

        final String names = matcher.group(1);
        if (RE_DOCUMENT_METHODS_IMPORTED.matcher(names).find())
        {
            return prologue;
        }

        final String imported = names.stripTrailing();
        // whatever separated the last name from the closing brace stays, so a multi-line import stays multi-line
        final String trailing = names.substring(imported.length());
        final String separator = imported.isEmpty() ? "" : (imported.endsWith(",") ? " " : ", ");

        return prologue.substring(0, matcher.start(1)) +
            imported + separator + DOCUMENT_METHODS + trailing +
            prologue.substring(matcher.end(1));
    }


    /**
     * <p>
     * Analyzes the existing module
     * </p>
     * <p>
     * Regrettably, the {@link TrackUsageData} only provides the "new GraphQL<...>(...)" definition itself, so we
     * have to use more clumsy means to get the rest of the information.
     * </p>
     *
     * @param ctx    document context
     * @param source TS source
     *
     * @return module info
     */
    static ModuleInfo analyzeModule(DocumentContext ctx, String source)
    {
        log.trace("Read source: {}", source);

        int start = ctx.start;
        int end = ctx.end;

        final String beforeGraphQLDef = source.substring(0, start);

        if (start > end || end > source.length())
        {
            return null;
        }

        String graphQlDef = source.substring(start, end);
        final String epilogue = source.substring(end);

        final Matcher matcher = RE_VAR_NAME.matcher(beforeGraphQLDef);
        if (!matcher.find())
        {
            throw new IllegalStateException("Cannot extract variable name: " + beforeGraphQLDef);
        }

        final int definitionStart = matcher.start(1);
        String prologue = source.substring(0, definitionStart);

        final int index = prologue.lastIndexOf("export");
        if (index >= 0)
        {
            prologue = prologue.substring(0, index);
        }

        final Matcher typeParamMatcher = RE_TYPE_PARAM.matcher(graphQlDef);
        if (!typeParamMatcher.find())
        {
            throw new IllegalStateException("Cannot extract variable name from definition: " + graphQlDef);
        }
        final String variableName = matcher.group(3);

        graphQlDef = typeParamMatcher.replaceFirst("new GraphQLQuery<" + variableName + "Result>");

        final String leftSideOfDefinition = matcher.group(1);
        return new ModuleInfo(variableName, prologue, leftSideOfDefinition, graphQlDef, epilogue);
    }


    /**
     * Reads a TS from the given relative path
     *
     * @param module relative module path
     *
     * @return TS code
     */
    String readModule(String module)
    {
        try
        {
            return FileUtils.readFileToString(new File(tsSourcePath, module + ".ts"), "UTF-8");
        }
        catch (IOException e)
        {
            throw new QLiveException(e);
        }
    }


    /**
     * Writes the given source code to the given TypeScript module
     *
     * @param modulePath    relative module path
     * @param updatedSource new TS source
     */
    void writeModule(String modulePath, String updatedSource)
    {
        try
        {
            FileUtils.writeStringToFile(new File(tsSourcePath, modulePath + ".ts"), updatedSource, "UTF-8");
        }
        catch (IOException e)
        {
            throw new QLiveException(e);
        }

    }


    /**
     * Returns true if the node selection matches the original fields of the type and none of the fields are aliased.
     *
     * @param type         GraphQL type
     * @param selectedKids selected fields
     *
     * @return true if the given type is fully selected
     */
    private boolean fieldsMatch(GraphQLObjectType type, List<SelectionTypeNode> selectedKids)
    {
        final List<GraphQLFieldDefinition> fields = type.getFields();
        if (fields.size() != selectedKids.size())
        {
            return false;
        }

        final boolean result = fields.stream().allMatch(
            f -> f.getName().startsWith("__") ||
                selectedKids.stream().anyMatch(
                    sf -> sf.getAliasedName().equals(f.getName()) && sf.field.getAlias() == null
                )
        ) && selectedKids.stream().allMatch(sf -> sf.complete);
        return result;
    }


    /**
     * Renders the pick fields (second <code>Pick&lt;name,fields&gt;</code> type parameter)
     *
     * @param selectedFields selected fields
     *
     * @return TS code expression
     */
    private String renderPickFields(List<SelectionTypeNode> selectedFields)
    {
        return selectedFields.stream()
            .filter(selectedField -> selectedField.complete)
            .map(selectedField -> JSONUtil.DEFAULT_GENERATOR.forValue(selectedField.field.getName()))
            .collect(Collectors.joining(" | "));
    }


    /**
     * Returns a string that is n times one indentation level.
     *
     * @param level indentation level
     *
     * @return full indentation string
     */
    private String indent(int level)
    {
        return "    ".repeat(level);
    }

    /**
     * Query document context
     *
     * @param modulePath  relative module path
     * @param start       TS declaration start index
     * @param end         TS declaration end index
     * @param definitions List of GraphQL definitions within the Query document
     */
    record DocumentContext(String modulePath, int start, int end, List<OperationDefinition> definitions)
    {
    }

    record SelectionInfo(DocumentContext ctx, QueryInfo result, List<SelectionTypeNode> selectedOperations)
    {
    }

    /**
     * Result of our first pass
     *
     * @param selectedFields selected fields
     * @param complete       true if the node selection is complete
     */
    private record TSResult(List<SelectionTypeNode> selectedFields, boolean complete)
    {
    }


    /**
     * Encapsulates our tree traversal knowledge for one node
     *
     * @param type         containing type
     * @param field        selection node
     * @param fieldType    modified GraphQL node type
     * @param selectedKids sub selection within the node
     * @param complete     true if the node is selected completely and unaliased
     */
    record SelectionTypeNode(String type, Field field, GraphQLType fieldType,
                                     List<SelectionTypeNode> selectedKids, boolean complete
    )
    {

        public String getAliasedName()
        {
            return field.getAlias() != null ? field.getAlias() : field.getName();
        }


        public String getFieldTypeName()
        {
            return GraphQLTypeUtil.unwrapAll(fieldType).getName();
        }


        public boolean isList()
        {
            return GraphQLTypeUtil.isList(GraphQLTypeUtil.unwrapNonNull(fieldType));
        }


        public boolean isNonNull()
        {
            return GraphQLTypeUtil.isNonNull(fieldType);
        }


        @Override
        public @NonNull String toString()
        {
            return complete ? getAliasedName() : getAliasedName() + "'";
        }
    }

    /**
     * <p>
     * Encapsulates the knowledge we gained of analyzing the root type
     * </p><p>
     * allComplete describes the root type by the same rule every other type is described by, so a query
     * selecting the whole of it is not a case the renderer has to special-case.
     * </p>
     *
     * @param rootTypeName root type name ("QueryType" or "MutationType" usually)
     * @param allComplete  true if the top type was selected completely
     */
    record QueryInfo(String rootTypeName, boolean allComplete)
    {
    }

    /**
     * Encapsulates our knowledge of an existing TypeScript module
     *
     * @param variableName           Name of the defined variable
     * @param prologue               Code before the type definition
     * @param leftSideOfDefinition   Left side of the GraphQL assignment
     * @param graphQLQueryDefinition TypeScript snippet of the GraphQL Definition (right side of assignment)
     * @param epilogue               Code after the query
     */
    record ModuleInfo(String variableName, String prologue, String leftSideOfDefinition,
                      String graphQLQueryDefinition, String epilogue)
    {
    }

}
