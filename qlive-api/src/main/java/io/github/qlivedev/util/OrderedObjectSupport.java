package io.github.qlivedev.util;

import org.svenson.info.ConstructorInfo;
import org.svenson.info.JSONClassInfo;
import org.svenson.info.JSONPropertyInfo;
import org.svenson.info.JavaObjectSupport;

import java.util.Map;
import java.util.TreeMap;

/// svenson's object support with properties in a stable order: by priority as svenson orders them, and by name
/// among equal priorities.
///
/// svenson collects a class's properties into a `HashMap` in the order `Class.getMethods()` lists them, which
/// the JVM leaves unspecified, and only sorts by priority after that. Properties that share a hash bucket then
/// come out in whatever order reflection produced, so the same bean can generate differently ordered JSON from
/// one start of the application to the next -- noise in every recorded fixture, snapshot or diff of it.
///
/// A class declaring `@JSONPropertyOrder` still gets its own comparator, which sorts the name-ordered properties.
final class OrderedObjectSupport
    extends JavaObjectSupport
{
    @Override
    public JSONClassInfo createClassInfo(Class<?> cls)
    {
        final JSONClassInfo info = super.createClassInfo(cls);

        final Map<String, JSONPropertyInfo> byName = new TreeMap<>();
        for (String name : info.getPropertyNames())
        {
            byName.put(name, info.getPropertyInfo(name));
        }

        // JSONClassInfo sorts the properties by priority with a stable sort, so this order survives among equals
        final ConstructorInfo ctor = info.getConstructorInfo();
        return new JSONClassInfo(
            cls,
            byName,
            ctor != null ? ctor.getConstructor() : null,
            ctor != null ? ctor.getCtorTypeHint() : null,
            info.getPostConstructMethod()
        );
    }
}
