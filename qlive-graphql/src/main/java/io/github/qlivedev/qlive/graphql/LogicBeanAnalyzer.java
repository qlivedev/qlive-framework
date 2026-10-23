package io.github.qlivedev.qlive.graphql;

import io.github.qlivedev.qlive.api.QLiveDomainException;
import com.esotericsoftware.reflectasm.MethodAccess;
import io.github.qlivedev.qlive.api.annotation.Input;
import io.github.qlivedev.qlive.api.annotation.QLiveAction;
import io.github.qlivedev.qlive.api.annotation.QLiveField;
import io.github.qlivedev.qlive.api.annotation.QLiveMutation;
import io.github.qlivedev.qlive.api.annotation.QLiveQuery;
import io.github.qlivedev.qlive.api.annotation.QLiveTypeParam;
import io.github.qlivedev.qlive.api.annotation.ResolvedGenericType;
import io.github.qlivedev.qlive.api.annotation.Rules;
import io.github.qlivedev.qlive.graphql.logic.Action;
import io.github.qlivedev.qlive.graphql.logic.ActionSubject;
import io.github.qlivedev.qlive.graphql.logic.GraphQLValueProvider;
import io.github.qlivedev.qlive.graphql.logic.Mutation;
import io.github.qlivedev.qlive.graphql.logic.Query;
import io.github.qlivedev.qlive.graphql.logic.SubjectRef;
import io.github.qlivedev.qlive.graphql.logic.SubjectValueProvider;
import io.github.qlivedev.qlive.graphql.param.ParameterProvider;
import io.github.qlivedev.qlive.graphql.param.ParameterProviderFactory;
import io.github.qlivedev.qlive.graphql.util.MethodOrder;
import graphql.schema.GraphQLInputType;
import graphql.schema.GraphQLList;
import graphql.schema.GraphQLNonNull;
import graphql.schema.GraphQLOutputType;
import graphql.schema.GraphQLScalarType;
import graphql.schema.GraphQLTypeReference;
import org.apache.commons.beanutils.ConvertUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.AopProxyUtils;

import jakarta.validation.constraints.NotNull;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.TypeVariable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Analyzes annotated LogicBeans and finds queries, mutations and actions.
 */
class LogicBeanAnalyzer
{

    private final static Logger log = LoggerFactory.getLogger(LogicBeanAnalyzer.class);


    private final Set<Query> queries = new LinkedHashSet<>();

    private final Set<Mutation> mutations = new LinkedHashSet<>();

    private final List<Action> actions = new ArrayList<>();

    /// Where each mutation name was declared, so that a second declaration can name the first.
    private final Map<String, String> mutationLocations = new HashMap<>();

    /// The POJO types of the domain's tables, which is what makes an action parameter a subject.
    private final Set<Class<?>> subjectTypes;

    private final Supplier<QLiveDomain> domain;

    private final Collection<ParameterProviderFactory> parameterProviderFactories;

    private final MutableTypeRegistry typeRegistry;

    private final Map<String,Class<?>> outputTypeOverrides;

    LogicBeanAnalyzer(
        Supplier<QLiveDomain> domain,
        Collection<ParameterProviderFactory> parameterProviderFactories,
        Collection<Object> logicBeans,
        MutableTypeRegistry typeRegistry,
        Set<Class<?>> subjectTypes
    )
    {
        this.domain = domain;
        this.subjectTypes = subjectTypes;
        this.parameterProviderFactories = parameterProviderFactories;
        this.typeRegistry = typeRegistry;
        this.outputTypeOverrides = new HashMap<>();

        logicBeans.forEach(this::discover);
    }


    Set<Query> getQueries()
    {
        return queries;
    }


    Set<Mutation> getMutations()
    {
        return mutations;
    }


    /// The actions, which are among the mutations as well.
    List<Action> getActions()
    {
        return actions;
    }


    private void discover(Object logicBean)
    {
        final Class<?> cls = AopProxyUtils.ultimateTargetClass(logicBean);
        log.debug("Analyzing logic bean {}", cls.getName());

        final MethodAccess methodAccess = MethodAccess.get(cls);


        for (Method method : MethodOrder.publicMethods(cls))
        {
            final String methodName = method.getName();
            final Class<?>[] parameterTypes = method.getParameterTypes();

            final QLiveQuery queryAnno = method.getDeclaredAnnotation(QLiveQuery.class);
            final boolean isNotNullQuery = method.getDeclaredAnnotation(NotNull.class) != null;

            final String locationInfo = logicBean.getClass().getName() + ":" + methodName;
            if (queryAnno != null)
            {
                queries.addAll(
                    buildQueries(
                        logicBean,
                        methodAccess,
                        method,
                        methodName,
                        parameterTypes,
                        queryAnno,
                        locationInfo,
                        isNotNullQuery
                    )
                );
            }

            final QLiveMutation mutationAnno = method.getDeclaredAnnotation(QLiveMutation.class);
            final QLiveAction actionAnno = method.getDeclaredAnnotation(QLiveAction.class);
            final boolean isNotNullMutation = method.getAnnotation(NotNull.class) != null;
            if (mutationAnno != null)
            {
                if (actionAnno != null)
                {
                    throw new QLiveDomainException(locationInfo + ": @QLiveAction is used in place of " +
                        "@QLiveMutation, not with it. An action is a mutation already.");
                }

                for (Mutation mutation : buildMutations(
                    logicBean,
                    methodAccess,
                    method,
                    methodName,
                    parameterTypes,
                    locationInfo,
                    mutationAnno,
                    isNotNullMutation
                ))
                {
                    addMutation(mutation, locationInfo);
                }
            }

            if (actionAnno != null)
            {
                if (queryAnno != null)
                {
                    throw new QLiveDomainException(locationInfo + ": An action is a mutation and can't be a query too.");
                }

                final Action action = buildAction(
                    logicBean,
                    methodAccess,
                    method,
                    parameterTypes,
                    locationInfo,
                    isNotNullMutation
                );
                addMutation(action, locationInfo);
                actions.add(action);
            }
            else if (method.getDeclaredAnnotation(Rules.class) != null)
            {
                throw new QLiveDomainException(locationInfo + ": @Rules belongs on an @QLiveAction method.");
            }
        }
    }


    /// Adds a mutation, refusing a name that is taken. An action is a mutation, so an action and a plain mutation
    /// can't share one either.
    private void addMutation(Mutation mutation, String locationInfo)
    {
        final String existing = mutationLocations.putIfAbsent(mutation.getName(), locationInfo);
        if (existing != null)
        {
            throw new QLiveDomainException(
                "Mutation '" + mutation.getName() + "' is defined twice: by " + existing + " and by " + locationInfo
            );
        }
        mutations.add(mutation);
    }


    private Action buildAction(
        Object logicBean,
        MethodAccess methodAccess,
        Method method,
        Class<?>[] parameterTypes,
        String locationInfo,
        boolean isNotNull
    )
    {
        final String name = method.getName();
        log.debug("ACTION {}", name);

        if (findTypeParamAnnoParameter(method) != null)
        {
            throw new QLiveDomainException(locationInfo + ": An action can't have a @QLiveTypeParam parameter.");
        }

        final Parameter[] parameters = method.getParameters();
        final Type[] genericParameterTypes = method.getGenericParameterTypes();

        final List<ParameterProvider> parameterProviders = new ArrayList<>(parameters.length);
        final List<ActionSubject> subjects = new ArrayList<>();
        for (int i = 0; i < parameters.length; i++)
        {
            final Parameter parameter = parameters[i];
            final ActionSubject subject = subjectOf(locationInfo, parameter, i);
            if (subject != null)
            {
                subjects.add(subject);
                parameterProviders.add(createSubjectProvider(subject));
            }
            else
            {
                parameterProviders.add(createValueProvider(locationInfo, genericParameterTypes[i], parameter));
            }
        }

        final Rules rulesAnno = method.getDeclaredAnnotation(Rules.class);
        final List<Class<?>> ruleClasses = rulesAnno != null ? Arrays.asList(rulesAnno.value()) : List.of();

        GraphQLOutputType resultType = getGraphQLOutputType(locationInfo, method);
        if (isNotNull)
        {
            resultType = new GraphQLNonNull(resultType);
        }

        return new Action(
            domain,
            name,
            logicBean,
            methodAccess,
            methodAccess.getIndex(name, parameterTypes),
            parameterProviders,
            resultType,
            method,
            subjects,
            ruleClasses,
            locationInfo
        );
    }


    /// The subject a parameter is, or `null` where it is input: a subject is a parameter of a table-backed type,
    /// or a `List` of one, unless it is marked {@link Input}.
    private ActionSubject subjectOf(String locationInfo, Parameter parameter, int index)
    {
        if (parameter.isAnnotationPresent(Input.class))
        {
            return null;
        }

        final Class<?> type = parameter.getType();
        if (subjectTypes.contains(type))
        {
            return new ActionSubject(argumentName(locationInfo, parameter), index, type, false);
        }

        if (List.class.isAssignableFrom(type) &&
            parameter.getParameterizedType() instanceof ParameterizedType parameterized &&
            parameterized.getActualTypeArguments()[0] instanceof Class<?> elementType &&
            subjectTypes.contains(elementType))
        {
            return new ActionSubject(argumentName(locationInfo, parameter), index, elementType, true);
        }
        return null;
    }


    private ParameterProvider createSubjectProvider(ActionSubject subject)
    {
        final InputType refType = typeRegistry.registerInput(new TypeContext(null, SubjectRef.class));
        final GraphQLTypeReference ref = GraphQLTypeReference.typeRef(refType.getName());

        return new SubjectValueProvider(
            subject,
            subject.list() ? new GraphQLList(new GraphQLNonNull(ref)) : ref,
            typeRegistry
        );
    }


    private static String argumentName(String locationInfo, Parameter parameter)
    {
        final QLiveField argAnno = parameter.getDeclaredAnnotation(QLiveField.class);
        if (argAnno != null && argAnno.value().length() > 0)
        {
            return argAnno.value();
        }
        if (!parameter.isNamePresent())
        {
            throw new IllegalStateException(locationInfo + ": Cannot determine the name of a subject parameter. " +
                "Enable parameter metadata in your Java compiler (-parameters, for Maven " +
                "maven.compiler.parameters=true) or name it with @QLiveField.");
        }
        return parameter.getName();
    }

    private final static String NAME_PATTERN_WILDCARD = "*";

    private List<Query> buildQueries(
        Object logicBean,
        MethodAccess methodAccess,
        Method method,
        String methodName,
        Class<?>[] parameterTypes,
        QLiveQuery queryAnno,
        String locationInfo,
        boolean isNotNull
    )
    {
        final String name = queryAnno.value().length() > 0 ? queryAnno.value() : methodName;
        final int methodIndex = methodAccess.getIndex(methodName, parameterTypes);
        log.debug("QUERY {}", name);

        final List<ParameterProvider> parameterProviders = createParameterProviders(
            locationInfo,
            method
        );

        final Parameter typeParamAnnoParameter = findTypeParamAnnoParameter(method);

        if (typeParamAnnoParameter != null)
        {
            if (!typeParamAnnoParameter.getType().equals(Class.class))
            {
                throw new IllegalStateException(locationInfo + ": @GrapQLTypeParam parameter must be of type Class<T>");
            }

            final QLiveTypeParam typeParamAnno = typeParamAnnoParameter.getAnnotation(QLiveTypeParam.class);

            final Type actualTypeArgument =
                ((ParameterizedType) typeParamAnnoParameter.getParameterizedType()).getActualTypeArguments()[0];

            if (!(actualTypeArgument instanceof TypeVariable))
            {
                throw new IllegalStateException(locationInfo + ": @GrapQLTypeParam parameter must be declared as type variable ( e.g. Class<T> ) ");
            }

            final List<Query> list = new ArrayList<>();

            final String namePattern = typeParamAnno.namePattern().length() == 0 ? methodName + NAME_PATTERN_WILDCARD : typeParamAnno.namePattern();
            if (!namePattern.contains(NAME_PATTERN_WILDCARD))
            {
                throw new IllegalStateException(locationInfo + ": @QLiveTypeParam: namePattern must contain a '*' wildcard");
            }
            final String typeNamePattern = typeParamAnno.typeNamePattern();
            if (!typeNamePattern.isEmpty() && !namePattern.contains(NAME_PATTERN_WILDCARD))
            {
                throw new IllegalStateException(locationInfo + ": @QLiveTypeParam: namePattern must contain a '*' wildcard");
            }

            for (Class typeParam : typeParamAnno.types())
            {
                PojoTypes.ensurePojoType(typeParam);


                final String varName = ((TypeVariable) actualTypeArgument).getName();
                Map<String, Class<?>> map = new LinkedHashMap<>();
                map.put(varName, typeParam);

                final TypeContext newContext = new TypeContext(null, method.getReturnType(), map, typeNamePattern);
                final GraphQLOutputType outputType = getGraphQLOutputType(
                    locationInfo,
                    method.getReturnType(),
                    null,
                    newContext
                );
                GraphQLOutputType modifiedType = outputType;
                if (isNotNull)
                {
                    modifiedType = new GraphQLNonNull(modifiedType);
                }
                list.add(
                    new Query(
                        domain,
                        namePattern.replace(NAME_PATTERN_WILDCARD, typeParam.getSimpleName()),
                        logicBean,
                        methodAccess,
                        methodIndex,
                        parameterProviders,
                        modifiedType,
                        newContext,
                        methodName
                    )
                );
            }
            return list;
        }
        else
        {
            GraphQLOutputType modifiedType = getGraphQLOutputType(locationInfo, method);
            if (isNotNull)
            {
                modifiedType = new GraphQLNonNull(modifiedType);
            }

            return Collections.singletonList(
                new Query(
                    domain,
                    name,
                    logicBean,
                    methodAccess,
                    methodIndex,
                    parameterProviders,
                    modifiedType,
                    null,
                    null
                )
            );
        }
    }


    private Parameter findTypeParamAnnoParameter(Method method)
    {
        for (Parameter parameter : method.getParameters())
        {
            final QLiveTypeParam anno = parameter.getAnnotation(QLiveTypeParam.class);
            if (anno != null)
            {
                return parameter;
            }
        }

        return null;
    }


    private List<Mutation> buildMutations(
        Object logicBean,
        MethodAccess methodAccess,
        Method method,
        String methodName,
        Class<?>[] parameterTypes,
        String locationInfo,
        QLiveMutation mutationAnno,
        boolean isNotNull
    )
    {
        final String name = mutationAnno.value().length() > 0 ? mutationAnno.value() : methodName;
        final int methodIndex = methodAccess.getIndex(methodName, parameterTypes);

        log.debug("MUTATION {}", name);

        final List<ParameterProvider> parameterProviders = createParameterProviders(
            locationInfo,
            method
        );
        final Parameter typeParamAnnoParameter = findTypeParamAnnoParameter(method);

        if (typeParamAnnoParameter != null)
        {
            if (!typeParamAnnoParameter.getType().equals(Class.class))
            {
                throw new IllegalStateException(locationInfo + ": @GrapQLTypeParam parameter must be of type Class<T>");
            }

            final QLiveTypeParam typeParamAnno = typeParamAnnoParameter.getAnnotation(QLiveTypeParam.class);

            final Type actualTypeArgument =
                ((ParameterizedType) typeParamAnnoParameter.getParameterizedType()).getActualTypeArguments()[0];

            if (!(actualTypeArgument instanceof TypeVariable))
            {
                throw new IllegalStateException(locationInfo + ": @GrapQLTypeParam parameter must be declared as type variable ( e.g. Class<T> ) ");
            }

            final List<Mutation> list = new ArrayList<>();

            final String queryNamePattern = typeParamAnno.namePattern().length() == 0 ? methodName + NAME_PATTERN_WILDCARD : typeParamAnno.namePattern();
            if (!queryNamePattern.contains(NAME_PATTERN_WILDCARD))
            {
                throw new IllegalStateException(locationInfo + ": @QLiveTypeParam: namePattern() must contain a '*' wildcard");
            }
            final String typeNamePattern = typeParamAnno.typeNamePattern();
            if (!typeNamePattern.isEmpty() && !typeNamePattern.contains(NAME_PATTERN_WILDCARD))
            {
                throw new IllegalStateException(locationInfo + ": @QLiveTypeParam: typeNamePattern() must contain a '*' wildcard");
            }

            for (Class typeParam : typeParamAnno.types())
            {
                PojoTypes.ensurePojoType(typeParam);

                final String varName = ((TypeVariable) actualTypeArgument).getName();
                Map<String, Class<?>> map = new LinkedHashMap<>();
                map.put(varName, typeParam);

                final TypeContext newContext = new TypeContext(null, method.getReturnType(), map, typeNamePattern);
                final GraphQLOutputType outputType = getGraphQLOutputType(
                    locationInfo,
                    method.getReturnType(),
                    null,
                    newContext
                );
                GraphQLOutputType modifiedType = outputType;
                if (isNotNull)
                {
                    modifiedType = new GraphQLNonNull(modifiedType);
                }
                list.add(
                    new Mutation(
                        domain,
                        queryNamePattern.replace(NAME_PATTERN_WILDCARD, typeParam.getSimpleName()),
                        logicBean,
                        methodAccess,
                        methodIndex,
                        parameterProviders,
                        modifiedType,
                        newContext,
                        methodName
                    )
                );
            }
            return list;
        }
        else
        {
            final GraphQLOutputType resultType = getGraphQLOutputType(locationInfo, method);

            GraphQLOutputType modifiedType = resultType;
            if (isNotNull)
            {
                modifiedType = new GraphQLNonNull(modifiedType);
            }

            return Collections.singletonList(
                new Mutation(
                    domain,
                    name,
                    logicBean,
                    methodAccess,
                    methodIndex,
                    parameterProviders,
                    modifiedType,
                    null,
                    null
                )
            );
        }
    }


    private GraphQLOutputType getGraphQLOutputType(String locationInfo, Method method)
    {
        final Class<?> returnType = method.getReturnType();

        final QLiveField fieldAnno = method.getAnnotation(QLiveField.class);

        final TypeContext ctx = new TypeContext(null, method);

        return getGraphQLOutputType(locationInfo, returnType, fieldAnno, ctx);
    }


    private GraphQLOutputType getGraphQLOutputType(
        String locationInfo,
        Class<?> returnType,
        QLiveField fieldAnno,
        TypeContext ctx
    )
    {
        final GraphQLOutputType resultType;
        if (returnType == null || returnType.equals(Void.TYPE))
        {
            throw new QLiveDomainException(locationInfo + ": Logic methods must return an output type");
        }
        else
        {

            if (fieldAnno != null && fieldAnno.value().length() > 0)
            {
                throw new QLiveDomainException(locationInfo + ": Return values can't have names");
            }

            final GraphQLScalarType scalarType = typeRegistry.getGraphQLScalarFor(returnType, fieldAnno);
            if (scalarType != null)
            {
                return scalarType;
            }


            if (List.class.isAssignableFrom(returnType))
            {
                if (!ctx.isParametrized())
                {
                    throw new IllegalStateException(locationInfo + ": List return type must be parametrized.");
                }


                final Class<?> elementClass = ctx.getFirstActualType();
                final GraphQLScalarType scalar = typeRegistry.getGraphQLScalarFor(elementClass, fieldAnno);
                if (scalar != null)
                {
                    resultType = new GraphQLList(scalar);
                }
                else
                {
                    // create new type context for the element of the generic list
                    final OutputType outputType = typeRegistry.register(new TypeContext(null,  elementClass));
                    resultType = new GraphQLList(new GraphQLTypeReference(outputType.getName()));
                }
            }
            else if (Enum.class.isAssignableFrom(returnType))
            {
                final OutputType enumType = typeRegistry.register(ctx);

                resultType = new GraphQLTypeReference(enumType.getName());
            }
            else
            {
                if (returnType.isInterface())
                {
                    throw new QLiveDomainException("Cannot handle " + returnType);
                }

                final OutputType outputType = typeRegistry.register(ctx);
                resultType = new GraphQLTypeReference(outputType.getName());
            }
        }
        return resultType;
    }


    private List<ParameterProvider> createParameterProviders(
        String name, Method method
    )
    {
//        try
//        {
            final Parameter[] parameters = method.getParameters();
            final Type[] genericParameterTypes = method.getGenericParameterTypes();

            List<ParameterProvider> list = new ArrayList<>(parameters.length);
            for (int i = 0, parameterTypesLength = parameters.length; i < parameterTypesLength; i++)
            {
                final Parameter parameter = parameters[i];
                ParameterProvider provider = createValueProvider(name, genericParameterTypes[i], parameter);

                list.add(
                    provider
                );
            }
            return list;
//        }
//        catch (Exception e)
//        {
//            throw new QLiveDomainException(e);
//        }
    }


    private ParameterProvider createValueProvider(String name, Type genericParameterType, Parameter parameter)
    {
        Class<?> parameterType = parameter.getType();
        final Annotation[] parameterAnnotations = parameter.getDeclaredAnnotations();


        ParameterProvider provider = null;
        for (ParameterProviderFactory factory : parameterProviderFactories)
        {
            provider = invokeFactory(parameterType, parameterAnnotations, factory);

            if (provider != null)
            {
                break;
            }
        }

        if (provider == null)
        {
            provider = createValueProvider(
                name,
                parameter,
                genericParameterType,
                parameterType
            );
        }
        return provider;
    }


    private ParameterProvider createValueProvider(
        String name,
        Parameter parameter,
        Type genericParameterType,
        Class<?> parameterType
    )
    {

        final QLiveField argAnno = parameter.getDeclaredAnnotation(QLiveField.class);
        final ResolvedGenericType genericTypeAnno = parameter.getDeclaredAnnotation(ResolvedGenericType.class);

        if ((argAnno == null || argAnno.value().length() == 0) && !parameter.isNamePresent())
        {
            throw new IllegalStateException(name +
                ": Cannot determine Method parameter name due to metadata not provided by Java compiler and " +
                "no @QLiveField annotation being defined. " +
                "Either you need to define @QLiveField annotations for all logic parameters or you need " +
                "to enable parameter metadata in your java compiler. " +
                "For maven this is the property maven.compiler.parameters=true, for the javac command line " +
                "compiler it is the -parameters option.");
        }

        ParameterProvider provider;
        final TypeContext paramTypeContext = new TypeContext(
            null,
            parameterType,
            genericParameterType,
            genericTypeAnno
        );

        GraphQLInputType inputType = typeRegistry.getGraphQLScalarFor(parameterType, argAnno);
        if (inputType == null)
        {
            if (List.class.isAssignableFrom(parameterType))
            {
                final Type genericReturnType = parameter.getParameterizedType();
                if (!(genericReturnType instanceof ParameterizedType))
                {
                    throw new QLiveDomainException(parameter + ": List parameter type must be parametrized.");
                }

                final Class<?> elementClass = (Class<?>) ((ParameterizedType) genericReturnType)
                    .getActualTypeArguments()[0];


                final GraphQLScalarType scalar = typeRegistry.getGraphQLScalarFor(elementClass, argAnno);
                if (scalar != null)
                {
                    inputType = new GraphQLList(scalar);
                }
                else
                {
                    InputType newInputType = typeRegistry.registerInput(
                        // create new type context for the element of the generic list.
                        new TypeContext(paramTypeContext, elementClass)
                    );
                    inputType = new GraphQLList(new GraphQLTypeReference(newInputType.getName()));
                }
            }
            else
            {
                if (parameterType.isInterface())
                {
                    throw new QLiveDomainException("Cannot handle " + parameterType);
                }


                final InputType parameterInputType = typeRegistry.registerInput(paramTypeContext);
                final String nameFromConfig = parameterInputType.getName();
                inputType = GraphQLTypeReference.typeRef(nameFromConfig);
            }
        }

        final boolean fieldAnnoPresent = argAnno != null;
        final NotNull notNullAnno = parameter.getAnnotation(NotNull.class);
        boolean jpaNotNull = notNullAnno != null;


        if (jpaNotNull && fieldAnnoPresent && !argAnno.notNull())
        {
            throw new QLiveDomainException(name +
                ": Required field disagreement between @NotNull and @QLiveField required value");
        }

        // a primitive has no null to receive, so a request leaving it out has to be refused before the call
        final boolean isNotNull = jpaNotNull || (fieldAnnoPresent && argAnno.notNull()) || parameterType.isPrimitive();

        final String parameterName = fieldAnnoPresent && argAnno.value().length() > 0 ? argAnno
            .value() : parameter.getName();
        final Object defaultValue;

        final Object defaultValueFromAnno = fieldAnnoPresent ? argAnno.defaultValue() : null;
        if (String.class.isAssignableFrom(parameterType))
        {
            defaultValue = defaultValueFromAnno;
        }
        else
        {
            defaultValue = defaultValueFromAnno != null ? ConvertUtils.convert(defaultValueFromAnno, parameterType) : null;
        }

        final GraphQLValueProvider graphQLValueProvider = new GraphQLValueProvider(
            parameterName,
            isNotNull,
            inputType,
            defaultValue,
            typeRegistry,
            parameterType
        );

        log.debug("  {}", graphQLValueProvider.getArgumentName() + ": " + graphQLValueProvider.getInputType());


        provider = graphQLValueProvider;
        return provider;
    }


    private ParameterProvider invokeFactory(
        Class<?> parameterType, Annotation[] parameterAnnotations, ParameterProviderFactory factory
    )
    {
        try
        {
            return factory.createIfApplicable(parameterType, parameterAnnotations);
        }
        catch (Exception e)
        {
            throw new QLiveDomainException(e);
        }
    }
}
