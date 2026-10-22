package io.github.qlivedev.qlive.graphql.logic;

import io.github.qlivedev.qlive.graphql.TypeRegistry;
import graphql.schema.DataFetchingEnvironment;
import graphql.schema.GraphQLInputType;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/// Provides the argument of a subject parameter: the {@link SubjectRef} the request sent, or a list of them. The
/// action's executor replaces them with the rows.
public class SubjectValueProvider
    extends GraphQLValueProvider
{
    private final ActionSubject subject;


    public SubjectValueProvider(
        ActionSubject subject,
        GraphQLInputType inputType,
        TypeRegistry typeRegistry
    )
    {
        super(subject.name(), true, inputType, null, typeRegistry, SubjectRef.class);
        this.subject = subject;
    }


    public ActionSubject getSubject()
    {
        return subject;
    }


    @Override
    public Object provide(DataFetchingEnvironment environment)
    {
        final Object value = environment.getArgument(getArgumentName());

        if (subject.list())
        {
            final List<?> values = (List<?>) value;
            final List<SubjectRef> refs = new ArrayList<>(values.size());
            for (Object element : values)
            {
                refs.add(toRef(element));
            }
            return refs;
        }
        return toRef(value);
    }


    private static SubjectRef toRef(Object value)
    {
        final Map<?, ?> map = (Map<?, ?>) value;
        return new SubjectRef((String) map.get("id"), (String) map.get("version"));
    }
}
