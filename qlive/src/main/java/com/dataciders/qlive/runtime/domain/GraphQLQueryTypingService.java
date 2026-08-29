package com.dataciders.qlive.runtime.domain;

import de.quinscape.domainql.DomainQL;
import com.dataciders.qlive.model.ts.ModuleFunctionReferences;
import com.dataciders.qlive.model.ts.TrackUsageData;
import com.dataciders.qlive.runtime.QLiveException;
import de.quinscape.spring.jsview.util.JSONUtil;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.parser.Parser;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
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
import java.util.Timer;
import java.util.TimerTask;
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

    private final GraphQLSchema graphQLSchema;

    private final File tsSourcePath;

    final static Pattern RE_VAR_NAME = Pattern.compile(".*((export)\\s+?const\\s+([A-Za-z_]+))\\s*=.*", Pattern.DOTALL);

    final static Pattern RE_TYPE_PARAM = Pattern.compile("^new GraphQLQuery<(.*)>");

    private int timeoutMillis = 300;

    private Timer timer = null;


    public GraphQLQueryTypingService(DomainQL domainQL, File tsSourcePath)
    {
        log.trace("Create GraphQLQueryTypingService");

        this.graphQLSchema = domainQL.getGraphQLSchema();
        this.tsSourcePath = tsSourcePath;
    }


    /**
     * <p>
     * Use a timer to delay triggering the TrackUsage update a few milliseconds. If any new updates come in during that
     * time, the timer is cancelled and a new one is created.
     * </p><p>
     * The method returns the given track usage data unchanged to conform with the signature of Consumer&lt;
     * TrackUsageData&gt;
     * </p>
     *
     * @param refs New TrackUsage content
     *
     * @return the same track usage data
     */
    public TrackUsageData triggerDebouncedUpdate(TrackUsageData refs)
    {
        try
        {
            if (timer != null)
            {
                timer.cancel();
            }

            timer = new Timer();
            timer.schedule(
                new TimerTask()
                {
                    @Override
                    public void run()
                    {
                        try
                        {
                            timer = null;
                            updateGraphQLQueryTypes(refs);
                        }
                        catch (Exception e)
                        {
                            log.error("Error updating TrackUsageData", e);
                        }
                    }
                },
                timeoutMillis
            );
        }
        catch (Exception e)
        {
            log.error("Error updating TrackUsageData", e);
        }

        return refs;
    }


    /**
     * Returns the current timeout milliseconds for debounced updates.
     *
     * @return timeout in milliseconds
     */
    public int getTimeoutMillis()
    {
        return timeoutMillis;
    }


    /**
     * Reconfigures the update delay for debounced updates (default is 300 milliseconds)
     *
     * @param timeoutMillis delay in milliseconds
     */
    public void setTimeoutMillis(int timeoutMillis)
    {
        this.timeoutMillis = timeoutMillis;
    }


    /**
     * Updates all TS query sources according to the given updated track usage data,
     * <p>
     * (Also see {@link #triggerDebouncedUpdate(TrackUsageData)})
     *
     * @param refs track usage data
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

                final String tsCode = renderType(
                    rootTypeName,
                    selectedOperations,
                    allComplete,
                    false,
                    0
                );

                final String source = readModule(ctx.modulePath);
                final ModuleInfo moduleInfo = analyzeModule(ctx, source);

                String updatedSource = null;
                if (moduleInfo != null)
                {
                    updatedSource = renderModule(moduleInfo, tsCode);
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
                List<?> index = indexes.get(i);

                String query = (String) call.get(0);
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

                    // For now, we expect that we either have queries or mutations. The use-case of mixing
                    // both seems fishy at this point.

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
        final String queryTypeName = graphQLSchema.getQueryType().getName();
        final String mutationTypeName = graphQLSchema.getMutationType().getName();
        final String rootTypeName = processQueries ? queryTypeName : mutationTypeName;
        final GraphQLObjectType rootType = (GraphQLObjectType) graphQLSchema.getType(rootTypeName);
        if (rootType == null)
        {
            throw new IllegalStateException("GraphQLType " + rootTypeName + " not found");
        }

        boolean allComplete = true;
        for (OperationDefinition definition : ctx.definitions)
        {

            final SelectionSet selectionSet = definition.getSelectionSet();
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

                TSResult result = follow(field, queryTypeName, 1);
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
     * @param field    selected GraphQL node
     * @param typeName current type name
     * @param level    recursion level
     *
     * @return TS result
     */
    private TSResult follow(Field field, String typeName, int level)
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
        if (fieldType instanceof GraphQLScalarType)
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
                        if (selection instanceof Field kidField)
                        {
                            TSResult result = follow(kidField, objectType.getName(), level + 1);

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

            if (isList)
            {
                tb.append("Array<");
            }

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
                if (tb.length() > 0)
                {
                    tb.append(" & ");
                }
                tb.append(renderPickRest(typeName, selectedFields, level));
            }
            if (isList)
            {
                tb.append(">");
            }
            return tb.toString();
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


    static String renderModule(ModuleInfo moduleInfo, String tsCode)
    {
        String prologue;

        final String tsTypeDef = "export type " + moduleInfo.variableName() + "Result = " + tsCode;

        prologue = moduleInfo.prologue() + tsTypeDef + "\n\n";

        return prologue + moduleInfo.leftSideOfDefinition + " = " + moduleInfo.graphQLQueryDefinition + moduleInfo.epilogue();
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
     * Admittedly, having an allComplete = true here is odd, who is selecting all things?, but for completeness’ sake.
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
    private record ModuleInfo(String variableName, String prologue, String leftSideOfDefinition,
                              String graphQLQueryDefinition, String epilogue)
    {
    }

}
