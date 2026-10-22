package io.github.qlivedev.qlive.graphql.logic;

import com.esotericsoftware.reflectasm.MethodAccess;
import io.github.qlivedev.qlive.api.action.ActionRefusedException;
import io.github.qlivedev.qlive.api.action.Refusal;
import io.github.qlivedev.qlive.graphql.QLiveDomain;
import io.github.qlivedev.qlive.graphql.param.ParameterProvider;
import graphql.GraphQLError;
import graphql.GraphqlErrorBuilder;
import graphql.execution.DataFetcherResult;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLOutputType;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/// An `@QLiveAction` method: a mutation the framework guards with rules.
///
/// Its data fetcher hands the arguments to the {@link ActionExecutor} the runtime bound to it, which loads the
/// subjects, checks versions and rules and calls the method. A refusal becomes one GraphQL error per reason and no
/// data.
public class Action
    extends Mutation
{
    private final Method method;

    private final List<ActionSubject> subjects;

    private final List<Class<?>> ruleClasses;

    private final String location;

    private volatile ActionExecutor executor;


    public Action(
        Supplier<QLiveDomain> domain,
        String name,
        String description,
        Object logicBean,
        MethodAccess methodAccess,
        int methodIndex,
        List<ParameterProvider> parameterProviders,
        GraphQLOutputType resultType,
        Method method,
        List<ActionSubject> subjects,
        List<Class<?>> ruleClasses,
        String location
    )
    {
        super(domain, name, description, logicBean, methodAccess, methodIndex, parameterProviders, resultType, null, null);
        this.method = method;
        this.subjects = List.copyOf(subjects);
        this.ruleClasses = List.copyOf(ruleClasses);
        this.location = location;
    }


    /// Binds the executor that runs this action. Done once, by the runtime, at startup.
    ///
    /// @param executor executor
    public void bind(ActionExecutor executor)
    {
        if (this.executor != null)
        {
            throw new IllegalStateException("Action '" + name + "' is bound to an executor already");
        }
        this.executor = executor;
    }


    @Override
    public Object get(DataFetchingEnvironment env)
    {
        final ActionExecutor executor = this.executor;
        if (executor == null)
        {
            throw new IllegalStateException(
                "Action '" + name + "' has no executor. Actions are run by the QLive runtime, which binds one to " +
                    "every action at startup -- is QLiveConfiguration imported?"
            );
        }

        try
        {
            return executor.execute(this, provideParameters(env));
        }
        catch (ActionRefusedException e)
        {
            final List<GraphQLError> errors = new ArrayList<>();
            for (Refusal refusal : e.getRefusals())
            {
                errors.add(toError(env, refusal));
            }

            return DataFetcherResult.newResult()
                .errors(errors)
                .build();
        }
    }


    private static GraphQLError toError(DataFetchingEnvironment env, Refusal refusal)
    {
        final Map<String, Object> extensions = new LinkedHashMap<>();
        extensions.put("code", refusal.code());
        extensions.put("messageKey", refusal.messageKey());
        extensions.put("field", refusal.field());
        extensions.put("errorClass", refusal.errorClass());

        return GraphqlErrorBuilder.newError(env)
            .message(refusal.messageKey())
            .extensions(extensions)
            .build();
    }


    /// The names of the method's parameters as the action's rules see them, by position: the mutation's argument
    /// names, and `null` for a parameter the framework provides rather than the request, such as the
    /// `DataFetchingEnvironment`.
    public List<String> getParameterNames()
    {
        final List<String> names = new ArrayList<>(parameterProviders.size());
        for (ParameterProvider provider : parameterProviders)
        {
            names.add(provider instanceof GraphQLValueProvider valueProvider ? valueProvider.getArgumentName() : null);
        }
        return names;
    }


    public Method getMethod()
    {
        return method;
    }


    public Parameter[] getParameters()
    {
        return method.getParameters();
    }


    public List<ActionSubject> getSubjects()
    {
        return subjects;
    }


    public List<Class<?>> getRuleClasses()
    {
        return ruleClasses;
    }


    /// Bean class and method name, for messages.
    public String getLocation()
    {
        return location;
    }


    @Override
    public String toString()
    {
        return "Action '" + name + "' (" + location + ")";
    }
}
