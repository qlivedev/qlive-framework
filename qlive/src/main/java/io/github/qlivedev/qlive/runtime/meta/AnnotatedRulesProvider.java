package io.github.qlivedev.qlive.runtime.meta;

import io.github.qlivedev.qlive.api.annotation.Rules;
import io.github.qlivedev.qlive.graphql.OutputType;
import io.github.qlivedev.qlive.graphql.QLiveDomain;
import io.github.qlivedev.qlive.graphql.meta.DomainMeta;
import io.github.qlivedev.qlive.graphql.meta.MetadataProvider;
import io.github.qlivedev.qlive.runtime.action.Invariants;
import io.github.qlivedev.qlive.runtime.action.Rule;

import java.util.ArrayList;
import java.util.List;

/// Reads the invariants a POJO the domain exposes names with `@Rules` on its class, and adds them to its type.
/// See {@link Invariants}.
///
/// Part of every domain {@link io.github.qlivedev.qlive.runtime.domain.QLiveDefaultDomain} starts, so that an
/// annotated class needs nothing else.
public class AnnotatedRulesProvider
    implements MetadataProvider
{
    @Override
    public void provideMetaData(QLiveDomain domain, DomainMeta meta)
    {
        for (OutputType outputType : domain.getTypeRegistry().getOutputTypes())
        {
            final Class<?> javaType = outputType.getJavaType();
            final Rules annotation = javaType == null ? null : javaType.getAnnotation(Rules.class);
            if (annotation == null)
            {
                continue;
            }

            final String owner = "Type '" + outputType.getName() + "'";
            final List<Rule> rules = new ArrayList<>(annotation.value().length);
            for (Class<?> ruleClass : annotation.value())
            {
                rules.add(Invariants.create(domain, outputType.getName(), Invariants.instantiate(ruleClass, owner)));
            }
            Invariants.add(meta, outputType.getName(), rules);
        }
    }
}
