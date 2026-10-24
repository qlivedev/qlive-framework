package io.github.qlivedev.qlive.runtime.action;

import io.github.qlivedev.qlive.api.action.ActionRefusedException;
import io.github.qlivedev.qlive.api.action.Refusal;
import io.github.qlivedev.qlive.api.json.JSONUtil;
import io.github.qlivedev.qlive.graphql.QLiveDomain;
import io.github.qlivedev.qlive.graphql.TypeRegistry;
import io.github.qlivedev.qlive.graphql.logic.Action;
import io.github.qlivedev.qlive.graphql.logic.ActionExecutor;
import io.github.qlivedev.qlive.graphql.logic.ActionResult;
import io.github.qlivedev.qlive.graphql.logic.ActionSubject;
import io.github.qlivedev.qlive.graphql.logic.SubjectRef;
import io.github.qlivedev.qlive.model.QueryConfig;
import io.github.qlivedev.qlive.model.condition.CNode;
import io.github.qlivedev.qlive.model.condition.Component;
import io.github.qlivedev.qlive.model.condition.Condition;
import io.github.qlivedev.qlive.model.condition.FieldReference;
import io.github.qlivedev.qlive.model.condition.Operation;
import io.github.qlivedev.qlive.model.merge.MergeConflict;
import io.github.qlivedev.qlive.runtime.QLiveException;
import io.github.qlivedev.qlive.runtime.filter.FilterTransformer;
import io.github.qlivedev.qlive.runtime.filter.PropertyPath;
import io.github.qlivedev.qlive.runtime.merge.MergeConflictException;
import io.github.qlivedev.qlive.runtime.merge.WrittenRow;
import io.github.qlivedev.qlive.runtime.merge.WrittenRows;
import io.github.qlivedev.qlive.runtime.meta.MergeMeta;
import io.github.qlivedev.qlive.runtime.meta.QueryConfigMeta;
import io.github.qlivedev.qlive.runtime.query.QueryDocumentService;
import io.github.qlivedev.qlive.runtime.scalar.FilterDSL;
import graphql.schema.GraphQLFieldDefinition;
import graphql.schema.GraphQLNamedType;
import graphql.schema.GraphQLObjectType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLTypeUtil;
import org.jooq.DSLContext;
import org.jooq.Field;
import org.jooq.Record;
import org.jooq.Table;
import org.jooq.UniqueKey;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/// Runs the domain's actions: one transaction on committed data, and inside it the subjects' rows loaded and their
/// versions checked, the rules evaluated, the method called.
///
/// Every rule is created and checked when this is constructed, at startup, and the executor binds itself to every
/// action of the domain. A refusal is an {@link ActionRefusedException}, which rolls the transaction back and which
/// the action turns into GraphQL errors.
public class DefaultActionExecutor
    implements ActionExecutor
{
    /// i18n key of the conflict where a subject's row has a version other than the one sent.
    public final static String SUBJECT_CHANGED = "Action.subjectChanged";

    /// i18n key of the conflict where a subject's row no longer exists.
    public final static String SUBJECT_GONE = "Action.subjectGone";

    /// Key in the response's `extensions` of what an action wrote: one entry per write, in order, which is how the
    /// client's working sets learn the versions an action left.
    public final static String WRITTEN = "written";

    /// SQL state of a write refused because a concurrent transaction changed the row. Standard, so no dialect has
    /// to be asked.
    private final static String SERIALIZATION_FAILURE = "40001";

    private final QLiveDomain domain;

    private final TypeRegistry types;

    private final DSLContext dslContext;

    private final QueryDocumentService queryDocumentService;

    private final TransactionTemplate transactionTemplate;

    private final Map<Action, List<CompiledRule>> rules = new IdentityHashMap<>();

    private final Map<Class<?>, SubjectTable> subjectTables = new HashMap<>();


    public DefaultActionExecutor(
        QLiveDomain domain,
        DSLContext dslContext,
        QueryDocumentService queryDocumentService,
        PlatformTransactionManager transactionManager
    )
    {
        this.domain = domain;
        this.types = domain.getTypeRegistry();
        this.dslContext = dslContext;
        this.queryDocumentService = queryDocumentService;

        this.transactionTemplate = new TransactionTemplate(transactionManager);
        transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_REPEATABLE_READ);

        for (Action action : domain.getActions())
        {
            for (ActionSubject subject : action.getSubjects())
            {
                subjectTables.computeIfAbsent(subject.pojoType(), this::subjectTable);
            }
            rules.put(action, compile(action));
            action.bind(this);
        }
    }


    /// The tags each action brings to the frontend that no `i18n()` call its analysis could find names, so that the
    /// translations offer them on their own: the message keys of its rules, which arrive with a refusal, and the
    /// label of every input parameter a form field edits, `approveBar.comment`.
    ///
    /// @return the tags by action name
    public Map<String, Set<String>> getActionTags()
    {
        final Map<String, Set<String>> tagsByAction = new LinkedHashMap<>();
        for (Map.Entry<Action, List<CompiledRule>> entry : rules.entrySet())
        {
            final Action action = entry.getKey();
            final Set<String> tags = new LinkedHashSet<>();
            for (CompiledRule rule : entry.getValue())
            {
                tags.add(rule.rule().getMessageKey());
            }
            for (String parameterName : action.getParameterNames())
            {
                if (parameterName != null && subject(action, parameterName) == null)
                {
                    tags.add(action.getName() + "." + parameterName);
                }
            }
            tagsByAction.put(action.getName(), Set.copyOf(tags));
        }
        return tagsByAction;
    }


    @Override
    public ActionResult execute(Action action, Object[] arguments)
    {
        final List<CompiledRule> compiled = rules.get(action);
        if (compiled == null)
        {
            throw new IllegalStateException(action + " is not an action of this executor's domain");
        }

        try
        {
            return run(action, compiled, arguments);
        }
        catch (MergeConflictException e)
        {
            throw new ActionRefusedException(conflicts(action, arguments, e.getConflicts()));
        }
        catch (RuntimeException e)
        {
            // a subject's row is read and version-checked in the action's snapshot, so a write committed since
            // surfaces here rather than as a version that doesn't match
            if (serializationFailure(e))
            {
                throw new ActionRefusedException(List.of(new Refusal(Refusal.CONFLICT, SUBJECT_CHANGED, null, null)));
            }
            throw e;
        }
    }


    private ActionResult run(Action action, List<CompiledRule> compiled, Object[] arguments)
    {
        return transactionTemplate.execute(status -> {

            final WrittenRows written = WrittenRows.collect();
            final Object[] values = arguments.clone();

            final List<Refusal> conflicts = new ArrayList<>();
            for (ActionSubject subject : action.getSubjects())
            {
                values[subject.index()] = load(action, subject, refs(subject, arguments), conflicts);
            }
            if (!conflicts.isEmpty())
            {
                throw new ActionRefusedException(conflicts);
            }

            final List<Refusal> broken = new ArrayList<>();
            for (CompiledRule rule : compiled)
            {
                evaluate(rule, arguments, broken);
            }
            if (!broken.isEmpty())
            {
                throw new ActionRefusedException(broken);
            }

            final Object result = action.invoke(values);
            return new ActionResult(result, Map.of(WRITTEN, written(written.getRows())));
        });
    }


    /// What the action wrote, as the client reads it: the row's type, id, the version left and the one it was made
    /// against, whether it was deleted, the fields written as `GenericScalar` values, and the many-to-many fields
    /// whose associations changed.
    private List<Map<String, Object>> written(List<WrittenRow> rows)
    {
        final GraphQLScalarType genericScalar = (GraphQLScalarType) domain.getGraphQLSchema().getType("GenericScalar");

        final List<Map<String, Object>> entries = new ArrayList<>(rows.size());
        for (WrittenRow row : rows)
        {
            final Map<String, Object> fields = new LinkedHashMap<>();
            row.fields().forEach((name, value) -> fields.put(name, genericScalar.getCoercing().serialize(value)));

            final Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("type", row.type());
            entry.put("id", row.id());
            entry.put("version", row.version());
            entry.put("prev", row.prev());
            entry.put("deleted", row.deleted());
            entry.put("fields", fields);
            entry.put("links", row.links());
            entries.add(entry);
        }
        return entries;
    }


    /// One refusal per conflict a write of the method met, blaming the subject the row is, where it is one.
    private List<Refusal> conflicts(Action action, Object[] arguments, List<MergeConflict> conflicts)
    {
        final List<Refusal> refusals = new ArrayList<>(conflicts.size());
        for (MergeConflict conflict : conflicts)
        {
            refusals.add(
                new Refusal(
                    Refusal.CONFLICT,
                    conflict.isDeleted() ? SUBJECT_GONE : SUBJECT_CHANGED,
                    subjectPath(action, arguments, conflict),
                    null
                )
            );
        }
        return refusals;
    }


    /// The path of the subject row a conflict is about, or `null` where the row is no subject.
    private String subjectPath(Action action, Object[] arguments, MergeConflict conflict)
    {
        for (ActionSubject subject : action.getSubjects())
        {
            if (!subjectTables.get(subject.pojoType()).typeName().equals(conflict.getType()))
            {
                continue;
            }

            final List<SubjectRef> refs = refs(subject, arguments);
            for (int i = 0; i < refs.size(); i++)
            {
                if (refs.get(i).getId().equals(conflict.getId()))
                {
                    return subject.list() ? subject.name() + "." + i : subject.name();
                }
            }
        }
        return null;
    }


    /// Whether the given exception is the database refusing a write that a concurrent transaction got in front of.
    ///
    /// By SQL state through the whole cause chain, like the merge's duplicate check: what wraps it depends on
    /// whether Spring's exception translator is installed.
    private static boolean serializationFailure(Throwable e)
    {
        for (Throwable cause = e; cause != null; cause = cause == cause.getCause() ? null : cause.getCause())
        {
            if (cause instanceof SQLException sql && SERIALIZATION_FAILURE.equals(sql.getSQLState()))
            {
                return true;
            }
        }
        return false;
    }


    // -----------------------------------------------------------------------------------------------------
    // startup
    // -----------------------------------------------------------------------------------------------------

    /// A rule as it is evaluated.
    ///
    /// @param ruleClass    the factory, for messages
    /// @param rule         what the factory created
    /// @param anchor       the subject whose stored fields the rule reads, or `null` for a rule over input alone
    /// @param inputPaths   compiled read of every input path the rule references, by path
    private record CompiledRule(
        Class<?> ruleClass,
        Rule rule,
        ActionSubject anchor,
        Map<String, InputPath> inputPaths
    )
    {
    }


    /// Where an input path reads from: the argument at a position, and the path within it, if any.
    private record InputPath(int index, PropertyPath path)
    {
    }


    /// What loading and querying a subject type's rows takes.
    private record SubjectTable(
        String typeName,
        Table<?> table,
        Field<?> idColumn,
        String idField,
        String idScalar,
        Field<?> versionColumn
    )
    {
    }


    private List<CompiledRule> compile(Action action)
    {
        final List<CompiledRule> compiled = new ArrayList<>();
        for (Class<?> ruleClass : action.getRuleClasses())
        {
            final RuleContext ctx = new RuleContext(domain, action, ruleClass);

            final Rule rule = instantiate(action, ruleClass).create(ctx);
            if (rule == null)
            {
                throw ctx.fail("The rule factory returned no rule");
            }

            if (rule.getField() != null)
            {
                ctx.check(rule.getField());
            }

            // the factory may have built paths without the context, so every one of them is checked again
            final Set<String> paths = new LinkedHashSet<>();
            collectPaths(rule.getCondition(), paths);

            ActionSubject anchor = null;
            final Map<String, InputPath> inputPaths = new HashMap<>();
            for (String path : paths)
            {
                ctx.check(path);

                final String parameterName = firstSegment(path);
                final ActionSubject subject = subject(action, parameterName);
                if (subject != null)
                {
                    if (anchor != null && anchor != subject)
                    {
                        throw ctx.fail("The rule reads the stored fields of both '" + anchor.name() + "' and '" +
                            subject.name() + "'. A rule is evaluated against the rows of one subject; rules over " +
                            "two subjects are not supported yet.");
                    }
                    anchor = subject;
                }
                else
                {
                    final int index = action.getParameterNames().indexOf(parameterName);
                    final String rest = path.length() > parameterName.length()
                        ? path.substring(parameterName.length() + 1)
                        : null;
                    inputPaths.put(
                        path,
                        new InputPath(
                            index,
                            rest != null ? PropertyPath.compile(rest, action.getParameters()[index].getType()) : null
                        )
                    );
                }
            }

            compiled.add(new CompiledRule(ruleClass, rule, anchor, Map.copyOf(inputPaths)));
        }
        return List.copyOf(compiled);
    }


    private static RuleFactory instantiate(Action action, Class<?> ruleClass)
    {
        if (!RuleFactory.class.isAssignableFrom(ruleClass))
        {
            throw new QLiveException(action + ": @Rules names " + ruleClass.getName() + ", which is no " +
                RuleFactory.class.getSimpleName());
        }

        try
        {
            return (RuleFactory) ruleClass.getConstructor().newInstance();
        }
        catch (ReflectiveOperationException e)
        {
            throw new QLiveException(action + ": Cannot instantiate rule " + ruleClass.getName() + ". A rule " +
                "factory is public, top-level or static nested, with a public no-arg constructor.", e);
        }
    }


    private SubjectTable subjectTable(Class<?> pojoType)
    {
        final String typeName = types.lookup(pojoType).getName();
        final Table<?> table = types.lookupType(typeName).getTable();

        final UniqueKey<?> primaryKey = table.getPrimaryKey();
        if (primaryKey == null || primaryKey.getFields().size() != 1)
        {
            throw new QLiveException("Type '" + typeName + "' has no single-column primary key and cannot be " +
                "the subject of an action, which names its rows by one id.");
        }
        final Field<?> idColumn = primaryKey.getFields().get(0);

        final GraphQLObjectType objectType = (GraphQLObjectType) domain.getGraphQLSchema().getType(typeName);
        GraphQLFieldDefinition idDefinition = null;
        for (GraphQLFieldDefinition definition : objectType.getFieldDefinitions())
        {
            if (idColumn.equals(types.lookupField(typeName, definition.getName())))
            {
                idDefinition = definition;
                break;
            }
        }
        if (idDefinition == null)
        {
            throw new QLiveException("Type '" + typeName + "' has no field for its primary key " + idColumn);
        }

        return new SubjectTable(
            typeName,
            table,
            idColumn,
            idDefinition.getName(),
            ((GraphQLNamedType) GraphQLTypeUtil.unwrapAll(idDefinition.getType())).getName(),
            MergeMeta.isVersioned(domain, typeName) ? types.lookupField(typeName, MergeMeta.VERSION) : null
        );
    }


    // -----------------------------------------------------------------------------------------------------
    // loading
    // -----------------------------------------------------------------------------------------------------

    @SuppressWarnings("unchecked")
    private static List<SubjectRef> refs(ActionSubject subject, Object[] arguments)
    {
        final Object value = arguments[subject.index()];
        return subject.list() ? (List<SubjectRef>) value : List.of((SubjectRef) value);
    }


    /// Loads a subject's rows, in the order of their references, and records a conflict for every row that is gone
    /// or has another version than the one sent.
    private Object load(Action action, ActionSubject subject, List<SubjectRef> refs, List<Refusal> conflicts)
    {
        final SubjectTable subjectTable = subjectTables.get(subject.pojoType());

        final List<Object> ids = new ArrayList<>(refs.size());
        for (SubjectRef ref : refs)
        {
            if (subjectTable.versionColumn() != null && ref.getVersion() == null)
            {
                throw new QLiveException(
                    action + ": subject '" + subject.name() + "' names row '" + ref.getId() + "' without a " +
                        "version. '" + subjectTable.typeName() + "' is versioned, so an action on its rows is " +
                        "issued against the version the user saw -- select '" + MergeMeta.VERSION + "' in the " +
                        "query the row came from."
                );
            }
            ids.add(subjectTable.idColumn().getDataType().convert(ref.getId()));
        }

        final Map<String, Record> byId = new HashMap<>();
        for (Record record : dslContext.selectFrom(subjectTable.table()).where(any(subjectTable.idColumn()).in(ids)).fetch())
        {
            byId.put(String.valueOf(record.get(subjectTable.idColumn())), record);
        }

        final List<Object> rows = new ArrayList<>(refs.size());
        for (int i = 0; i < refs.size(); i++)
        {
            final SubjectRef ref = refs.get(i);
            final String path = subject.list() ? subject.name() + "." + i : subject.name();

            final Record record = byId.get(ref.getId());
            if (record == null)
            {
                conflicts.add(new Refusal(Refusal.CONFLICT, SUBJECT_GONE, path, null));
            }
            else if (subjectTable.versionColumn() != null &&
                !Objects.equals(String.valueOf(record.get(subjectTable.versionColumn())), ref.getVersion()))
            {
                conflicts.add(new Refusal(Refusal.CONFLICT, SUBJECT_CHANGED, path, null));
            }
            else
            {
                rows.add(record.into(subject.pojoType()));
            }
        }

        if (subject.list())
        {
            return rows;
        }
        return rows.isEmpty() ? null : rows.get(0);
    }


    // -----------------------------------------------------------------------------------------------------
    // rules
    // -----------------------------------------------------------------------------------------------------

    private void evaluate(CompiledRule compiled, Object[] arguments, List<Refusal> broken)
    {
        final Rule rule = compiled.rule();
        final ActionSubject anchor = compiled.anchor();

        if (anchor == null)
        {
            final CNode bound = substitute(rule.getCondition(), ref -> inputValue(compiled, ref, arguments));
            final Predicate<Object> predicate = new FilterTransformer(null).transform(bound);
            if (predicate != null && !predicate.test(null))
            {
                broken.add(refusal(rule, rule.getField()));
            }
            return;
        }

        final String prefix = anchor.name() + ".";
        final CNode bound = substitute(
            rule.getCondition(),
            ref -> ref.getName().startsWith(prefix)
                ? new FieldReference(ref.getName().substring(prefix.length()))
                : inputValue(compiled, ref, arguments)
        );

        final List<SubjectRef> refs = refs(anchor, arguments);
        final Set<String> holding = holdingIds(anchor, refs, bound);
        for (int i = 0; i < refs.size(); i++)
        {
            if (!holding.contains(refs.get(i).getId()))
            {
                broken.add(refusal(rule, blamedField(rule.getField(), anchor, i)));
            }
        }
    }


    /// The ids of the subject's rows the condition holds for. Asking for those rather than for the rows it fails
    /// for keeps SQL's unknown on the refusing side: a comparison with a null column is no proof the rule holds.
    private Set<String> holdingIds(ActionSubject anchor, List<SubjectRef> refs, CNode condition)
    {
        final SubjectTable subjectTable = subjectTables.get(anchor.pojoType());

        final List<String> ids = refs.stream().map(SubjectRef::getId).distinct().toList();

        // the query service holds every query to the type's maximum page size, so the ids go in pages of that size
        final int maxPageSize = QueryConfigMeta.maxPageSizeForType(domain, anchor.pojoType());
        final int pageSize = maxPageSize > 0 ? maxPageSize : Math.max(ids.size(), 1);

        final Set<String> holding = new HashSet<>();
        for (int start = 0; start < ids.size(); start += pageSize)
        {
            final List<Object> page = new ArrayList<>(ids.subList(start, Math.min(start + pageSize, ids.size())));

            final QueryConfig config = new QueryConfig();
            config.setCondition(
                FilterDSL.and(
                    FilterDSL.field(subjectTable.idField()).in(FilterDSL.values(page, subjectTable.idScalar())),
                    (Condition) condition
                )
            );
            config.setPageSize(page.size());

            // the rule reads fields the query selects nothing of but the id, so selecting by filter is the point
            for (Object row : queryDocumentService.buildQuery(anchor.pojoType(), List.of(subjectTable.idField()), config)
                .selectByFilter(true)
                .execute()
                .getRows())
            {
                holding.add(String.valueOf(JSONUtil.DEFAULT_UTIL.getProperty(row, subjectTable.idField())));
            }
        }
        return holding;
    }


    private static Refusal refusal(Rule rule, String field)
    {
        return new Refusal(Refusal.RULE, rule.getMessageKey(), field, rule.getErrorClass());
    }


    /// The blamed field of a rule broken by one row of a subject: for a list, with the row's position after the
    /// parameter name, `bars.2.description`.
    private static String blamedField(String field, ActionSubject anchor, int index)
    {
        if (field == null || !anchor.list())
        {
            return field;
        }
        if (field.equals(anchor.name()))
        {
            return field + "." + index;
        }
        if (field.startsWith(anchor.name() + "."))
        {
            return anchor.name() + "." + index + field.substring(anchor.name().length());
        }
        return field;
    }


    /// The value an input path reads, as a condition node.
    private CNode inputValue(CompiledRule compiled, FieldReference ref, Object[] arguments)
    {
        final InputPath inputPath = compiled.inputPaths().get(ref.getName());
        final Object argument = arguments[inputPath.index()];
        final Object value = inputPath.path() != null && argument != null ? inputPath.path().read(argument) : argument;

        if (value instanceof List<?> list)
        {
            final List<Object> values = new ArrayList<>(list.size());
            for (Object element : list)
            {
                values.add(plain(element));
            }
            return FilterDSL.values(values, values.isEmpty() ? "String" : scalarName(values.get(0)));
        }

        final Object plain = plain(value);
        return FilterDSL.value(plain, plain == null ? "String" : scalarName(plain));
    }


    private static Object plain(Object value)
    {
        return value instanceof Enum<?> e ? e.name() : value;
    }


    private String scalarName(Object value)
    {
        final GraphQLScalarType scalar = types.getGraphQLScalarFor(value.getClass(), null);
        return scalar != null ? scalar.getName() : "String";
    }


    // -----------------------------------------------------------------------------------------------------
    // condition walks
    // -----------------------------------------------------------------------------------------------------

    private static void collectPaths(CNode node, Set<String> paths)
    {
        switch (node)
        {
            case null -> { }
            case FieldReference ref -> paths.add(ref.getName());
            case Condition c -> nullSafe(c.getOperands()).forEach(operand -> collectPaths(operand, paths));
            case Operation o -> nullSafe(o.getOperands()).forEach(operand -> collectPaths(operand, paths));
            case Component component -> collectPaths(component.getCondition(), paths);
            default -> { }
        }
    }


    /// A copy of the condition with every field reference replaced.
    private static CNode substitute(CNode node, Function<FieldReference, CNode> replace)
    {
        return switch (node)
        {
            case null -> null;
            case FieldReference ref -> replace.apply(ref);
            case Condition c -> {
                final Condition copy = new Condition();
                copy.setName(c.getName());
                copy.setOperands(nullSafe(c.getOperands()).stream().map(operand -> substitute(operand, replace)).toList());
                yield copy;
            }
            case Operation o -> {
                final Operation copy = new Operation();
                copy.setName(o.getName());
                copy.setOperands(nullSafe(o.getOperands()).stream().map(operand -> substitute(operand, replace)).toList());
                yield copy;
            }
            case Component component -> {
                final Component copy = new Component();
                copy.setId(component.getId());
                copy.setCondition(substitute(component.getCondition(), replace));
                yield copy;
            }
            default -> node;
        };
    }


    private static String firstSegment(String path)
    {
        final int dot = path.indexOf('.');
        return dot < 0 ? path : path.substring(0, dot);
    }


    private static ActionSubject subject(Action action, String name)
    {
        for (ActionSubject subject : action.getSubjects())
        {
            if (subject.name().equals(name))
            {
                return subject;
            }
        }
        return null;
    }


    private static <T> List<T> nullSafe(List<T> list)
    {
        return list == null ? List.of() : list;
    }


    @SuppressWarnings("unchecked")
    private static Field<Object> any(Field<?> field)
    {
        return (Field<Object>) field;
    }
}
