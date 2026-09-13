package com.dataciders.qlive.runtime.service;

import com.dataciders.qlive.model.bootstrap.Injection;
import com.dataciders.qlive.model.ts.ModuleFunctionReferences;
import com.dataciders.qlive.model.ts.TrackUsageData;
import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.QLivePaths;
import com.dataciders.qlive.runtime.util.GraphQLUtil;
import de.quinscape.domainql.DomainQL;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.GraphQLError;
import graphql.language.Argument;
import graphql.language.Document;
import graphql.language.Field;
import graphql.language.InlineFragment;
import graphql.language.ListType;
import graphql.language.NonNullType;
import graphql.language.OperationDefinition;
import graphql.language.Selection;
import graphql.language.SelectionSet;
import graphql.language.Type;
import graphql.language.TypeName;
import graphql.language.VariableDefinition;
import graphql.language.VariableReference;
import graphql.parser.Parser;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLFieldsContainer;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLSchema;
import graphql.schema.GraphQLTypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/// Runs the queries an application's views declare with `useInjection()`, so that a page arrives with its
/// data already in it instead of fetching it after it has rendered.
///
/// Nothing about that is declared twice: `useInjection(Q_Foo)` in the view is the only place the query is
/// named, and the frontend build's track-usage analysis is what carries it to this side. What the analysis
/// records is a call with the *identifier* the view used, so the query it refers to is looked up here the
/// same way the bundler resolves it -- in the module making the call and in the modules it imports.
///
/// The result goes out under the injection id the client reads it back with: the `__id` of the call where it
/// has one, and otherwise the name of the GraphQL operation, which is what `inject()` falls back to.
///
/// Only the view being served is read, and only its own calls. A component cannot inject: what runs for a
/// page would otherwise depend on what that page happens to import, and a component would quietly cost a
/// query in every view that pulls it in, whether or not it renders. A component that needs data gets it from
/// the view that injected it. {@link #injectionsOutsideViews(TrackUsageData)} is where that is enforced.
public class InjectionService
{
    private final static Logger log = LoggerFactory.getLogger(InjectionService.class);

    /// Call parameter name disambiguating two injections of the same query within one view. Has to agree
    /// with `InjectParams.__id` on the client, which is what reads the data back out.
    private final static String ID_PARAM = "__id";

    private final GraphQL graphQL;

    private final GraphQLSchema schema;

    /// The processors that turn recorded call parameters into GraphQL variables, in the order they are
    /// asked -- see {@link InjectionArgumentProcessor}.
    private final List<InjectionArgumentProcessor> argumentProcessors;

    /// The plans built so far, by module. Each entry carries the references it was built from, so an entry
    /// is only rebuilt when the analysis it actually read has changed -- see {@link CachedPlans}.
    private final Map<String, CachedPlans> cache = new ConcurrentHashMap<>();


    /// An injection service handling the argument types the framework itself brings, i.e. `QueryConfig`.
    public InjectionService(GraphQL graphQL, DomainQL domainQL)
    {
        this(graphQL, domainQL, List.of(new QueryConfigArgumentProcessor(domainQL)));
    }


    /// @param argumentProcessors  what turns the recorded parameters of a `useInjection()` call into the
    ///                            variables its query is executed with. The framework's own are not implied:
    ///                            a list left without a {@link QueryConfigArgumentProcessor} is one where
    ///                            query configs reach GraphQL as the partial deltas they were written as.
    public InjectionService(
        GraphQL graphQL, DomainQL domainQL, List<InjectionArgumentProcessor> argumentProcessors
    )
    {
        this.graphQL = graphQL;
        this.schema = domainQL.getGraphQLSchema();
        this.argumentProcessors = List.copyOf(argumentProcessors);
    }


    /// Executes the injections the given module declares, directly or through the modules it imports.
    ///
    /// @param analysis     current track-usage data
    /// @param module       track-usage module name of the view or entry point being served, or `null` for a
    ///                     path that resolves to no module of the application
    ///
    /// @return injections by injection id, empty where the module declares none
    public Map<String, Injection> provideInjections(TrackUsageData analysis, String module)
    {
        final List<InjectionPlan> plans = plansFor(analysis, module);
        if (plans.isEmpty())
        {
            return Map.of();
        }

        final Map<String, Injection> injections = LinkedHashMap.newLinkedHashMap(plans.size());
        for (InjectionPlan plan : plans)
        {
            final Injection injection = execute(plan);

            log.debug("INJECTION: {} = {}", plan,  injection);

            injections.put(plan.injectionId(), injection);
        }

        log.debug("Injections for module {}: {}", module, injections.keySet());

        return injections;
    }


    private Injection execute(InjectionPlan plan)
    {
        final ExecutionResult result = GraphQLUtil.executeGraphQLQuery(
            graphQL,
            plan.query(),
            plan.variables(),
            null
        );

        final List<GraphQLError> errors = result.getErrors();
        if (!errors.isEmpty())
        {
            // Fatal for the page rather than an injection left out: the view reads its injection
            // unconditionally, so a page served without one fails in the browser, where the reason for it
            // is no longer visible.
            throw new QLiveException(
                "Error executing injection '" + plan.injectionId() + "' declared in module '" +
                    plan.module() + "': " + GraphQLUtil.formatErrors(errors)
            );
        }

        return new Injection(result.getData(), plan.resultType());
    }


    // -----------------------------------------------------------------------------------------------------
    // planning
    // -----------------------------------------------------------------------------------------------------

    /// What a page needs of one `useInjection()` call: everything that can be worked out from the analysis
    /// alone, so that only the execution itself is left to do per request.
    private record InjectionPlan(
        String injectionId,
        String module,
        String query,
        Map<String, Object> variables,
        String resultType
    )
    {
    }


    /// One module's plans together with the references they were read from: the module's own and those of
    /// every module it imports, which is where {@link #findQuery} looks for the injected query.
    ///
    /// The analysis is not one immutable snapshot in dev -- the dev server pushes the modules a save changed
    /// and the rest keep the references they were pushed with. Comparing those references by identity is
    /// what lets a saved file cost the plans of the modules that read it and of no others.
    private record CachedPlans(Map<String, ModuleFunctionReferences> sources, List<InjectionPlan> plans)
    {
        /// Whether the given analysis still says what these plans were built from. A module that has since
        /// appeared or gone counts as a change: it changes what an injected identifier resolves to.
        boolean readsSameAs(TrackUsageData analysis)
        {
            for (Map.Entry<String, ModuleFunctionReferences> source : sources.entrySet())
            {
                if (analysis.getModuleFunctionReferences(source.getKey()) != source.getValue())
                {
                    return false;
                }
            }
            return true;
        }
    }


    /// One module's plans, built once per version of the analysis they read.
    ///
    /// Planning parses every query the module can reach, which is work that only changes when the frontend
    /// does -- once per `vite build` in production, and in dev whenever the dev server pushes an edit to one
    /// of the modules that plan was read from.
    private List<InjectionPlan> plansFor(TrackUsageData analysis, String module)
    {
        if (module == null)
        {
            return List.of();
        }

        final CachedPlans cached = cache.get(module);
        if (cached != null && cached.readsSameAs(analysis))
        {
            return cached.plans();
        }

        final CachedPlans built = buildPlans(analysis, module);
        cache.put(module, built);
        return built.plans();
    }


    /// Plans the injections of one view, which are the `useInjection()` calls of that view's own module and
    /// no others.
    private CachedPlans buildPlans(TrackUsageData analysis, String module)
    {
        final ModuleFunctionReferences refs = analysis.getModuleFunctionReferences(module);
        final Map<String, ModuleFunctionReferences> sources = sourcesOf(analysis, module, refs);
        if (refs == null)
        {
            return new CachedPlans(sources, List.of());
        }

        final List<InjectionPlan> plans = new ArrayList<>();

        for (List<?> call : refs.getCalls(ModuleFunctionReferences.USE_INJECTION_CALL_NAME))
        {
            final InjectionPlan plan = planFor(analysis, module, call);

            final InjectionPlan existing = plans.stream()
                .filter(p -> p.injectionId().equals(plan.injectionId()))
                .findFirst()
                .orElse(null);

            if (existing == null)
            {
                plans.add(plan);
            }
            else if (!existing.equals(plan))
            {
                // Both calls read the same id, so one of them gets data it did not ask for. Only the
                // application can say which, hence an error rather than a guess.
                throw new QLiveException(
                    "Module '" + module + "' injects '" + plan.injectionId() + "' twice, with different " +
                        "queries or parameters. Give one of the calls an " + ID_PARAM + " parameter to tell " +
                        "the two injections apart."
                );
            }
        }

        return new CachedPlans(sources, List.copyOf(plans));
    }


    /// The references one module's plans are read from, so that {@link CachedPlans} can tell when they no
    /// longer say what they said. Modules the analysis does not know are recorded as `null`, because one
    /// turning up later adds a candidate to {@link #findQuery}.
    private static Map<String, ModuleFunctionReferences> sourcesOf(
        TrackUsageData analysis,
        String module,
        ModuleFunctionReferences refs
    )
    {
        final Map<String, ModuleFunctionReferences> sources = new HashMap<>();
        sources.put(module, refs);

        if (refs != null)
        {
            for (String required : refs.getRequires())
            {
                sources.put(required, analysis.getModuleFunctionReferences(required));
            }
        }

        return sources;
    }


    // -----------------------------------------------------------------------------------------------------
    // validation
    // -----------------------------------------------------------------------------------------------------

    /// The modules that call `useInjection()` without being a view, i.e. every place the call is not valid.
    ///
    /// Injections belong to the view being served, because that is the only module a request identifies. A
    /// call anywhere else looks like it works while the component is written and then does not: the server
    /// never sees the component, so the data it asks for is not in the page, and the failure surfaces in the
    /// browser as a missing injection rather than where the mistake is. Reported off the analysis instead --
    /// at startup where a production build already has it, and at every push in dev.
    ///
    /// @return offending module names, sorted; empty when every injection sits in a view
    public static List<String> injectionsOutsideViews(TrackUsageData analysis)
    {
        return analysis.getModuleFunctionReferences().entrySet().stream()
            .filter(e -> !e.getValue().getCalls(ModuleFunctionReferences.USE_INJECTION_CALL_NAME).isEmpty())
            .map(Map.Entry::getKey)
            .filter(module -> !module.startsWith(QLivePaths.VIEW_ROOT))
            .sorted()
            .toList();
    }


    /// How to say that {@link #injectionsOutsideViews(TrackUsageData)} found something, for the two places
    /// that report it differently.
    public static String describeInjectionsOutsideViews(List<String> modules)
    {
        return "useInjection() is only valid in a view, i.e. in a module below " + QLivePaths.VIEW_ROOT +
            ", but " + modules + " call it. A component gets its data from the view that renders it, as a " +
            "prop -- the view is the only module a request names, so it is the only one whose injections " +
            "the server can prepare.";
    }


    private InjectionPlan planFor(TrackUsageData analysis, String module, List<?> call)
    {
        if (call.isEmpty() || !(call.getFirst() instanceof Map<?, ?> first) ||
            !(first.get("__identifier") instanceof String identifier))
        {
            throw new QLiveException(
                "Module '" + module + "' calls useInjection() with something other than a GraphQLQuery " +
                    "declared at module level: " + call + ". The query has to be named by an identifier the " +
                    "build can follow to its declaration."
            );
        }

        final DeclaredQuery declared = findQuery(analysis, module, identifier);

        final Map<String, Object> variables = new LinkedHashMap<>();
        if (call.size() > 1 && call.get(1) instanceof Map<?, ?> params)
        {
            params.forEach((k, v) -> variables.put(String.valueOf(k), v));
        }

        final Object id = variables.remove(ID_PARAM);
        final String injectionId = id != null ? String.valueOf(id) : declared.operation().getName();

        processArguments(module, declared.operation(), variables);

        return new InjectionPlan(
            injectionId,
            module,
            declared.source(),
            Collections.unmodifiableMap(variables),
            typeOf(declared.operation())
        );
    }


    /// Turns the recorded parameters of one call into the variables its query is executed with, by handing
    /// each of them to the processor that handles its declared type.
    ///
    /// Which processor that is follows from the query itself: the operation says what type it declared each
    /// variable as, and {@link InjectionArgumentProcessor#handles(String)} says who answers for that type.
    /// A variable of a type nobody claims is passed on as the analysis recorded it.
    private void processArguments(
        String module, OperationDefinition operation, Map<String, Object> variables
    )
    {
        final Map<String, List<GraphQLFieldDefinition>> usages = variableUsages(operation);

        for (VariableDefinition definition : operation.getVariableDefinitions())
        {
            final String typeName = typeNameOf(definition.getType());

            // A variable the call did not name is left alone: a query that insists on one should report a
            // missing one, not be handed a default nobody asked for.
            final Object value = variables.get(definition.getName());
            if (typeName == null)
            {
                continue;
            }

            final InjectionArgumentProcessor processor = processorFor(typeName);
            variables.put(
                definition.getName(),
                processor != null ? process(
                    processor,
                    definition.getType(),
                    module,
                    definition.getName(),
                    typeName,
                    value,
                    usages.getOrDefault(definition.getName(), List.of())
                ) : value
            );
        }
    }


    /// Where each variable of the operation is passed to, which is what says what a value is *for* rather
    /// than only what type it has.
    ///
    /// Read off the query the same way the execution will read it: down the selection set, resolving every
    /// field against the type its parent turned out to be. A variable passed inside an object literal, or
    /// one only reachable through a fragment spread, is not found -- the processors this feeds all degrade
    /// to what the type alone says, so a query written that way loses the extra rather than breaking.
    private Map<String, List<GraphQLFieldDefinition>> variableUsages(OperationDefinition operation)
    {
        final GraphQLObjectType root = rootTypeOf(operation);
        if (root == null || operation.getSelectionSet() == null)
        {
            return Map.of();
        }

        final Map<String, List<GraphQLFieldDefinition>> usages = new LinkedHashMap<>();
        collectUsages(root, operation.getSelectionSet(), usages);

        return usages;
    }


    private void collectUsages(
        GraphQLFieldsContainer parent,
        SelectionSet selectionSet,
        Map<String, List<GraphQLFieldDefinition>> usages
    )
    {
        for (Selection<?> selection : selectionSet.getSelections())
        {
            switch (selection)
            {
                case Field field ->
                {
                    final GraphQLFieldDefinition definition = parent.getFieldDefinition(field.getName());
                    if (definition == null)
                    {
                        // Not in the schema, so executing the query will report it. Nothing to say about
                        // its arguments in the meantime.
                        continue;
                    }

                    for (Argument argument : field.getArguments())
                    {
                        if (argument.getValue() instanceof VariableReference reference)
                        {
                            usages.computeIfAbsent(reference.getName(), name -> new ArrayList<>())
                                .add(definition);
                        }
                    }

                    if (field.getSelectionSet() != null &&
                        GraphQLTypeUtil.unwrapAll(definition.getType()) instanceof GraphQLFieldsContainer sub)
                    {
                        collectUsages(sub, field.getSelectionSet(), usages);
                    }
                }

                case InlineFragment fragment when fragment.getSelectionSet() != null ->
                {
                    final GraphQLFieldsContainer container = fragment.getTypeCondition() == null
                        ? parent
                        : schema.getType(fragment.getTypeCondition().getName())
                            instanceof GraphQLFieldsContainer named ? named : null;

                    if (container != null)
                    {
                        collectUsages(container, fragment.getSelectionSet(), usages);
                    }
                }

                default ->
                {
                }
            }
        }
    }


    /// The processor answering for the given type, or `null` where none does.
    private InjectionArgumentProcessor processorFor(String typeName)
    {
        for (InjectionArgumentProcessor processor : argumentProcessors)
        {
            if (processor.handles(typeName))
            {
                return processor;
            }
        }
        return null;
    }


    /// One value through its processor, element by element where the variable is declared as a list.
    ///
    /// The type a processor is picked by is the unwrapped one, so a `[QueryConfig!]!` is that processor's
    /// business as much as a `QueryConfig!` is -- which it can only be if the list is opened here. Opening
    /// it here rather than in every processor is also what keeps a list from being the one place a value
    /// quietly goes through unprocessed.
    ///
    /// Which values are elements is read off the declared type and not off the value, so a processor for a
    /// type whose values are themselves lists still gets whole values. A single value where a list is
    /// declared is one element, the way GraphQL's own coercing reads it.
    private static Object process(
        InjectionArgumentProcessor processor,
        Type<?> type,
        String module,
        String variable,
        String typeName,
        Object value,
        List<GraphQLFieldDefinition> usedAt
    )
    {
        return switch (type)
        {
            case NonNullType nonNull ->
                process(processor, nonNull.getType(), module, variable, typeName, value, usedAt);

            case ListType list when value instanceof List<?> elements ->
            {
                final List<Object> processed = new ArrayList<>(elements.size());
                for (Object element : elements)
                {
                    processed.add(
                        process(processor, list.getType(), module, variable, typeName, element, usedAt)
                    );
                }
                yield Collections.unmodifiableList(processed);
            }

            case ListType list ->
                process(processor, list.getType(), module, variable, typeName, value, usedAt);

            default ->
                processor.process(new InjectionArgument(module, variable, typeName, value, usedAt));
        };
    }


    /// The name of a resultType as a query declares its variables, i.e. "QueryConfig" for `QueryConfig!`.
    private static String typeNameOf(Type<?> type)
    {
        return switch (type)
        {
            case NonNullType nonNull -> typeNameOf(nonNull.getType());
            case ListType list -> typeNameOf(list.getType());
            case TypeName name -> name.getName();
            default -> null;
        };
    }


    /// A `new GraphQLQuery(...)` the analysis recorded, with its query already parsed.
    private record DeclaredQuery(String module, String source, OperationDefinition operation)
    {
    }


    /// Resolves the identifier a `useInjection()` call named to the query it was declared with.
    ///
    /// Looked for where the bundler would find it: in the calling module itself and in the modules it
    /// imports directly -- an identifier the module can name is one it declared or imported. Two ways of
    /// naming a query are accepted, because both are in use:
    ///
    ///  * the query lives in a module of its own named after it (`app/Q_Foo.ts`), which is what the
    ///    generated result types assume, or
    ///  * the operation itself is named like the identifier (`export const Q_Foo = new GraphQLQuery("query
    ///    Q_Foo ...")`), which covers queries collected in a shared module.
    private DeclaredQuery findQuery(TrackUsageData analysis, String module, String identifier)
    {
        final List<String> candidates = new ArrayList<>();
        candidates.add(module);
        for (String required : analysis.getModuleFunctionReferences(module).getRequires())
        {
            if (analysis.getModuleFunctionReferences(required) != null)
            {
                candidates.add(required);
            }
        }

        final List<DeclaredQuery> declared = new ArrayList<>();
        for (String candidate : candidates)
        {
            declared.addAll(declaredQueries(analysis, candidate));
        }

        final List<DeclaredQuery> byModule = declared.stream()
            .filter(q -> baseName(q.module()).equals(identifier))
            .toList();

        final List<DeclaredQuery> matches = byModule.isEmpty()
            ? declared.stream().filter(q -> identifier.equals(q.operation().getName())).toList()
            : byModule;

        if (matches.size() == 1)
        {
            return matches.getFirst();
        }

        if (matches.isEmpty())
        {
            throw new QLiveException(
                "Module '" + module + "' injects '" + identifier + "', but no query of that name is " +
                    "declared in it or in the modules it imports (" + candidates + "). Declare the query " +
                    "with new GraphQLQuery() at module level, in a module named after it or with an " +
                    "operation of that name."
            );
        }

        throw new QLiveException(
            "Module '" + module + "' injects '" + identifier + "', which matches more than one declared " +
                "query: " + matches.stream().map(DeclaredQuery::module).toList()
        );
    }


    private List<DeclaredQuery> declaredQueries(TrackUsageData analysis, String module)
    {
        final List<List<?>> calls = analysis.getModuleFunctionReferences(module)
            .getCalls(ModuleFunctionReferences.GRAPHQL_QUERY_CONSTRUCTOR_NAME);

        final List<DeclaredQuery> declared = new ArrayList<>(calls.size());
        for (List<?> call : calls)
        {
            if (call.isEmpty() || !(call.getFirst() instanceof String source))
            {
                // The same case the query typing service reports: a query assembled at runtime is invisible
                // to the analysis. Skipped rather than fatal -- it only matters if something injects it, and
                // that comes out as "no query of that name" further up.
                log.debug("Ignoring non-static GraphQLQuery construction in module '{}'", module);
                continue;
            }

            final OperationDefinition operation = parseOperation(module, source);
            if (operation != null)
            {
                declared.add(new DeclaredQuery(module, source, operation));
            }
        }

        return declared;
    }


    private OperationDefinition parseOperation(String module, String source)
    {
        try
        {
            final Document document = new Parser().parseDocument(source);
            final List<OperationDefinition> operations = document.getDefinitionsOfType(OperationDefinition.class);

            return operations.isEmpty() ? null : operations.getFirst();
        }
        catch (RuntimeException e)
        {
            log.warn("Could not parse GraphQL query declared in module '{}'", module, e);
            return null;
        }
    }


    /// The GraphQL resultType of what the injection carries, i.e. of the operation's single top-level selection.
    ///
    /// Informational -- the client logs it next to the value and does not derive behavior from it -- so an
    /// operation whose field is not in the schema yields `null` here rather than an error. Executing it will
    /// report that properly.
    private String typeOf(OperationDefinition operation)
    {
        final GraphQLObjectType root = rootTypeOf(operation);

        if (root == null || operation.getSelectionSet() == null)
        {
            return null;
        }

        for (Selection<?> selection : operation.getSelectionSet().getSelections())
        {
            if (selection instanceof Field field)
            {
                final GraphQLFieldDefinition definition = root.getFieldDefinition(field.getName());
                if (definition != null)
                {
                    return GraphQLTypeUtil.unwrapAll(definition.getType()).getName();
                }
            }
        }

        return null;
    }


    /// The type the operation's own selections are resolved against.
    private GraphQLObjectType rootTypeOf(OperationDefinition operation)
    {
        return operation.getOperation() == OperationDefinition.Operation.MUTATION
            ? schema.getMutationType()
            : schema.getQueryType();
    }


    /// Last segment of a track-usage module name, i.e. "Q_Foo" for "./app/Q_Foo".
    private static String baseName(String module)
    {
        return module.substring(module.lastIndexOf('/') + 1);
    }
}
