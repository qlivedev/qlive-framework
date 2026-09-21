package com.dataciders.qlive.runtime.filter;

import com.dataciders.qlive.runtime.QLiveException;
import com.dataciders.qlive.runtime.util.JSONUtil;
import org.svenson.DynamicProperties;
import org.svenson.info.JSONPropertyInfo;
import org.svenson.info.JavaObjectPropertyInfo;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/// One FilterDSL field path, checked against a declared class once and read from an object many times.
///
/// Reading is Svenson property access and nothing else: a real getter, a map entry or a dynamic property,
/// the same three things {@link org.svenson.util.JSONBeanUtil} looks at. There is no GraphQL field
/// resolution here and no live query to fall back to -- what a condition reads is a dead data structure
/// by the time it gets here, and a path that finds nothing simply finds nothing. Nor is this
/// Svenson's own `JSONPathUtil`, whose generic walk throws on a missing intermediate value and grows
/// missing maps and lists as it goes, both of which are the opposite of what a filter wants.
///
/// A path segment that is all digits is an index into the collection the previous segment reached. A dead
/// data tree's natural path semantics are positional, the way any data pointer works -- deliberately not
/// the SQL backend's, where a to-many hop becomes "does some element satisfy the rest of the path".
///
/// Validation walks the declared class's own JSON properties, which is the entire schema there is to
/// check against; a hop resolves to a getter or it does not exist. Where a hop leads somewhere whose type
/// is not declared -- a collection with no element type, a map's values -- validation stops there and the
/// remaining segments are whatever the object turns out to hold. Nothing is enforced at read time: the
/// declared class decides which paths compile, never which objects may be read.
final class PropertyPath
{
    private final String path;

    private final List<Object> segments;


    private PropertyPath(String path, List<Object> segments)
    {
        this.path = path;
        this.segments = segments;
    }


    /// Compiles one dotted path against the class the objects read through it are declared to have.
    ///
    /// @param path           dotted path as the FilterDSL writes it, e.g. `entityType` or `bazLinks.0.baz.name`
    /// @param declaredType   class the path is checked against, or `null` where no shape is declared
    ///                       anywhere and there is nothing to check against
    ///
    /// @throws QLiveException   if a segment names no property of the type it is read from, or crosses a
    ///                          to-many relation without saying which element
    static PropertyPath compile(String path, Class<?> declaredType)
    {
        if (path == null || path.isEmpty())
        {
            throw new QLiveException("Filter field has no name");
        }

        final List<Object> segments = new ArrayList<>();

        // the declared class at the position the walk has reached, or null where nothing declares it, and
        // the property that reached it, which is where a collection's element type is recorded
        Class<?> at = known(declaredType) ? declaredType : null;
        JSONPropertyInfo reachedBy = null;

        for (String segment : path.split("\\.", -1))
        {
            if (segment.isEmpty())
            {
                throw new QLiveException("Filter field path '" + path + "' has an empty segment");
            }

            if (isIndex(segment))
            {
                if (at != null && !Collection.class.isAssignableFrom(at))
                {
                    throw new QLiveException(
                        "Filter field path '" + path + "' indexes into " + at.getName() + ", which is not a collection"
                    );
                }

                segments.add(Integer.valueOf(segment));
                at = reachedBy == null ? null : elementType(reachedBy);
                reachedBy = null;
                continue;
            }

            if (at != null && Collection.class.isAssignableFrom(at))
            {
                throw new QLiveException(
                    "Filter field path '" + path + "' crosses a to-many relation without saying which element: " +
                        "an index segment belongs before '" + segment + "'"
                );
            }

            segments.add(segment);

            if (at == null || Map.class.isAssignableFrom(at))
            {
                at = null;
                reachedBy = null;
                continue;
            }

            final JSONPropertyInfo info = JSONUtil.getClassInfo(at).getPropertyInfo(segment);

            if (info == null || info.isIgnore() || !info.isReadable())
            {
                throw new QLiveException(
                    "Filter field path '" + path + "' has no property '" + segment + "' on " + at.getName()
                );
            }

            reachedBy = info;
            at = known(info.getType()) ? info.getType() : null;
        }

        return new PropertyPath(path, List.copyOf(segments));
    }


    /// Reads the path off one payload.
    ///
    /// @return the value the path reaches, or `null` for a path that runs out part way -- an unpopulated
    ///         relation, a collection shorter than the index, a property the payload does not have
    Object read(Object payload)
    {
        Object current = payload;

        for (Object segment : segments)
        {
            if (current == null)
            {
                return null;
            }

            current = segment instanceof Integer index
                ? element(current, index)
                : property(current, (String) segment);
        }

        return current;
    }


    /// The element type of a to-many property: what `@JSONTypeHint` says, and failing that what the
    /// getter's own generic signature says. Neither is required -- a class that declares neither
    /// simply stops being checkable past this point.
    private static Class<?> elementType(JSONPropertyInfo info)
    {
        final Class<?> hint = info.getTypeHint();

        if (hint != null)
        {
            return known(hint) ? hint : null;
        }

        if (info instanceof JavaObjectPropertyInfo javaInfo && javaInfo.getGetterMethod() != null
            && javaInfo.getGetterMethod().getGenericReturnType() instanceof ParameterizedType parameterized)
        {
            final Type[] arguments = parameterized.getActualTypeArguments();

            if (arguments.length == 1 && arguments[0] instanceof Class<?> element)
            {
                return known(element) ? element : null;
            }
        }

        return null;
    }


    /// Whether a class says anything about what it holds. `Object` says nothing, and checking a path
    /// against it would reject every path rather than none.
    private static boolean known(Class<?> type)
    {
        return type != null && type != Object.class;
    }


    private static boolean isIndex(String segment)
    {
        for (int i = 0; i < segment.length(); i++)
        {
            if (!Character.isDigit(segment.charAt(i)))
            {
                return false;
            }
        }
        return true;
    }


    private static Object property(Object value, String name)
    {
        if (value instanceof Map<?, ?> map)
        {
            return map.get(name);
        }

        final JSONPropertyInfo info = JSONUtil.getClassInfo(value.getClass()).getPropertyInfo(name);

        if (info != null && !info.isIgnore() && info.isReadable())
        {
            return info.getProperty(value);
        }

        return value instanceof DynamicProperties dynamic ? dynamic.getProperty(name) : null;
    }


    private static Object element(Object value, int index)
    {
        if (value instanceof List<?> list)
        {
            return index < list.size() ? list.get(index) : null;
        }

        if (value instanceof Collection<?> collection)
        {
            final Iterator<?> iterator = collection.iterator();
            for (int i = 0; i < index && iterator.hasNext(); i++)
            {
                iterator.next();
            }
            return iterator.hasNext() ? iterator.next() : null;
        }

        return null;
    }


    @Override
    public String toString()
    {
        return path;
    }
}
