package io.github.qlivedev.qlive.runtime.util;

import io.github.qlivedev.qlive.api.json.JSONUtil;
import io.github.qlivedev.qlive.runtime.QLiveException;
import jakarta.validation.constraints.NotNull;
import org.svenson.info.JSONPropertyInfo;
import org.svenson.info.JavaObjectPropertyInfo;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

/// Binds a JSON tree -- the maps, lists and scalars a svenson parser returns without a type -- onto Java types,
/// telling the members of a union apart by their shape, as TypeScript does: by a key one member has and the others
/// don't, not by a discriminator.
///
/// A union is a sealed type, its members the classes it permits. A member's key of its own is a required property
/// -- one whose getter is `@NotNull` -- that no other member has, or a constant: the read-only property of a class
/// built without arguments, `isStatus()` returning true, which then has to have that value. A member without a key
/// of its own is an error when the union is first bound, not when some input happens to need it.
///
/// A class is built by the constructor with the most parameters that are all named like its properties, so its
/// fields can be final, and its other properties set through their setters. The parameter names come from the
/// class file, which needs the class compiled with `-parameters`. Property names, and properties left out, are
/// svenson's, so the binder reads what svenson writes. Keys the class has no property for are ignored.
///
/// Types with a parser of their own, given with {@link #withParser}, are handed their part of the tree.
public final class ShapeBinder
{
    private final Map<Class<?>, Function<Object, ?>> parsers = new LinkedHashMap<>();

    private final Map<Class<?>, ClassShape> classShapes = new ConcurrentHashMap<>();

    private final Map<Class<?>, UnionShape> unionShapes = new ConcurrentHashMap<>();


    /// Hands the parser the tree wherever a value of the type, or of a subtype, is expected.
    ///
    /// @return this
    public <T> ShapeBinder withParser(Class<T> type, Function<Object, ? extends T> parser)
    {
        parsers.put(type, parser);
        return this;
    }


    /// Binds the tree onto the type.
    ///
    /// @param where    what the tree is, to start the path errors name
    /// @throws QLiveException for a tree that doesn't fit the type, naming the path to the part that doesn't
    public <T> T bind(Class<T> type, Object value, String where)
    {
        return type.cast(bind((Type) type, value, where));
    }


    /// Binds the tree onto the type, a parameterized one included.
    ///
    /// @see #bind(Class, Object, String)
    public Object bind(Type type, Object value, String where)
    {
        if (value == null)
        {
            return null;
        }

        final Class<?> cls = rawClass(type);
        final Function<Object, ?> parser = parserFor(cls);
        if (parser != null)
        {
            return parse(parser, cls, value, where);
        }
        if (cls == Object.class)
        {
            return value;
        }
        if (Collection.class.isAssignableFrom(cls))
        {
            return collection(cls, typeArgument(type, 0), value, where);
        }
        if (Map.class.isAssignableFrom(cls))
        {
            return map(typeArgument(type, 1), value, where);
        }
        if (cls.isPrimitive() || Number.class.isAssignableFrom(cls) || cls == String.class || cls == Boolean.class)
        {
            return scalar(cls, value, where);
        }
        if (cls.isEnum())
        {
            return enumValue(cls, value, where);
        }
        if (cls.isSealed())
        {
            return union(cls, object(value, where), where);
        }
        if (cls.isInterface() || Modifier.isAbstract(cls.getModifiers()))
        {
            throw new QLiveException(
                where + " is to be a " + cls.getSimpleName() + ", which the binder can't build: it is neither " +
                    "sealed nor has a parser."
            );
        }
        return instance(classShape(cls), object(value, where), where);
    }


    private Function<Object, ?> parserFor(Class<?> cls)
    {
        for (Map.Entry<Class<?>, Function<Object, ?>> entry : parsers.entrySet())
        {
            if (entry.getKey().isAssignableFrom(cls))
            {
                return entry.getValue();
            }
        }
        return null;
    }


    private static Object parse(Function<Object, ?> parser, Class<?> cls, Object value, String where)
    {
        final Object result;
        try
        {
            result = parser.apply(value);
        }
        catch (RuntimeException e)
        {
            throw new QLiveException(where + " is no " + cls.getSimpleName() + ": " + e.getMessage(), e);
        }
        if (result != null && !cls.isInstance(result))
        {
            throw new QLiveException(where + " is no " + cls.getSimpleName() + " but a " + result.getClass().getSimpleName());
        }
        return result;
    }


    private Object union(Class<?> cls, Map<String, Object> map, String where)
    {
        final UnionShape union = unionShapes.computeIfAbsent(cls, this::analyzeUnion);

        ClassShape match = null;
        for (ClassShape member : union.members())
        {
            if (member.isIn(map))
            {
                if (match != null)
                {
                    throw new QLiveException(
                        where + " is both a " + match.cls().getSimpleName() + " and a " + member.cls().getSimpleName() +
                            ", having the keys of both: " + map.keySet()
                    );
                }
                match = member;
            }
        }
        if (match == null)
        {
            throw new QLiveException(
                where + " is no " + cls.getSimpleName() + ", which has " + union.describe() + ". It has " + map.keySet()
            );
        }
        return instance(match, map, where);
    }


    private Object instance(ClassShape shape, Map<String, Object> map, String where)
    {
        for (Property property : shape.properties().values())
        {
            if (property.required() && map.get(property.jsonName()) == null)
            {
                throw new QLiveException(where + " has no " + property.jsonName() + ".");
            }
        }

        final Parameter[] parameters = shape.constructor().getParameters();
        final Object[] args = new Object[parameters.length];
        final Set<String> byConstructor = new HashSet<>();
        for (int i = 0; i < parameters.length; i++)
        {
            final Property property = shape.byJavaName(parameters[i].getName());
            byConstructor.add(property.jsonName());
            final Object arg = bind(
                parameters[i].getParameterizedType(), map.get(property.jsonName()), where + "." + property.jsonName()
            );
            args[i] = arg == null ? defaultValue(parameters[i].getType()) : arg;
        }

        final Object instance = construct(shape.constructor(), args, where);

        for (Property property : shape.properties().values())
        {
            final Object value = map.get(property.jsonName());
            if (value != null && property.setter() != null && !byConstructor.contains(property.jsonName()))
            {
                final Object bound = bind(
                    property.setter().getGenericParameterTypes()[0], value, where + "." + property.jsonName()
                );
                invoke(property.setter(), instance, bound, where);
            }
        }
        return instance;
    }


    private static Object construct(Constructor<?> constructor, Object[] args, String where)
    {
        try
        {
            return constructor.newInstance(args);
        }
        catch (InvocationTargetException e)
        {
            throw new QLiveException(where + ": " + e.getCause().getMessage(), e.getCause());
        }
        catch (ReflectiveOperationException e)
        {
            throw new QLiveException(where + ": " + e.getMessage(), e);
        }
    }


    private static void invoke(Method setter, Object instance, Object value, String where)
    {
        try
        {
            setter.invoke(instance, value);
        }
        catch (InvocationTargetException e)
        {
            throw new QLiveException(where + ": " + e.getCause().getMessage(), e.getCause());
        }
        catch (ReflectiveOperationException e)
        {
            throw new QLiveException(where + ": " + e.getMessage(), e);
        }
    }


    private Object collection(Class<?> cls, Type elementType, Object value, String where)
    {
        if (!(value instanceof List<?> list))
        {
            throw new QLiveException(where + " is no JSON list: " + value);
        }
        final Collection<Object> collection = Set.class.isAssignableFrom(cls) ? new LinkedHashSet<>() : new ArrayList<>();
        for (int i = 0; i < list.size(); i++)
        {
            collection.add(bind(elementType, list.get(i), where + "[" + i + "]"));
        }
        return collection;
    }


    private Object map(Type valueType, Object value, String where)
    {
        final Map<String, Object> map = new LinkedHashMap<>();
        object(value, where).forEach((key, entry) -> map.put(key, bind(valueType, entry, where + "." + key)));
        return map;
    }


    private static Object scalar(Class<?> cls, Object value, String where)
    {
        final Class<?> boxed = boxed(cls);
        if (boxed.isInstance(value))
        {
            return value;
        }
        if (value instanceof Number number && Number.class.isAssignableFrom(boxed))
        {
            try
            {
                final BigDecimal decimal = new BigDecimal(number.toString());
                if (boxed == Integer.class) return decimal.intValueExact();
                if (boxed == Long.class) return decimal.longValueExact();
                if (boxed == Short.class) return decimal.shortValueExact();
                if (boxed == Byte.class) return decimal.byteValueExact();
                if (boxed == BigInteger.class) return decimal.toBigIntegerExact();
                if (boxed == Double.class) return decimal.doubleValue();
                if (boxed == Float.class) return decimal.floatValue();
                if (boxed == BigDecimal.class) return decimal;
            }
            catch (ArithmeticException e)
            {
                throw new QLiveException(where + " is no " + boxed.getSimpleName() + ": " + value, e);
            }
        }
        throw new QLiveException(where + " is no " + boxed.getSimpleName() + ": " + value);
    }


    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object enumValue(Class<?> cls, Object value, String where)
    {
        if (value instanceof String name)
        {
            for (Object constant : cls.getEnumConstants())
            {
                if (((Enum) constant).name().equals(name))
                {
                    return constant;
                }
            }
        }
        throw new QLiveException(
            where + " is none of " + cls.getSimpleName() + " " + List.of(cls.getEnumConstants()) + ": " + value
        );
    }


    @SuppressWarnings("unchecked")
    private static Map<String, Object> object(Object value, String where)
    {
        if (!(value instanceof Map<?, ?>))
        {
            throw new QLiveException(where + " is no JSON object: " + value);
        }
        return (Map<String, Object>) value;
    }


    private UnionShape analyzeUnion(Class<?> cls)
    {
        final List<ClassShape> members = new ArrayList<>();
        final Deque<Class<?>> open = new ArrayDeque<>(List.of(cls.getPermittedSubclasses()));
        while (!open.isEmpty())
        {
            final Class<?> member = open.removeFirst();
            if (member.isSealed() && (member.isInterface() || Modifier.isAbstract(member.getModifiers())))
            {
                open.addAll(List.of(member.getPermittedSubclasses()));
            }
            else
            {
                members.add(classShape(member));
            }
        }

        final List<ClassShape> keyed = new ArrayList<>(members.size());
        for (ClassShape member : members)
        {
            final Map<String, Property> keys = new LinkedHashMap<>();
            for (Property property : member.properties().values())
            {
                final boolean shared = members.stream().anyMatch(
                    other -> other != member && other.properties().containsKey(property.jsonName())
                );
                if (!shared && (property.required() || property.constant() != null))
                {
                    keys.put(property.jsonName(), property);
                }
            }
            if (keys.isEmpty())
            {
                throw new QLiveException(
                    member.cls().getSimpleName() + " of " + cls.getSimpleName() + " has no key of its own: no " +
                        "required or constant property the other members don't have, so JSON can't tell it apart."
                );
            }
            keyed.add(member.withKeys(keys));
        }
        return new UnionShape(keyed);
    }


    private ClassShape classShape(Class<?> cls)
    {
        return classShapes.computeIfAbsent(cls, ShapeBinder::analyzeClass);
    }


    private static ClassShape analyzeClass(Class<?> cls)
    {
        final Map<String, JavaObjectPropertyInfo> infos = new LinkedHashMap<>();
        for (JSONPropertyInfo info : JSONUtil.getClassInfo(cls).getPropertyInfos())
        {
            if (!info.isIgnore())
            {
                infos.put(info.getJavaPropertyName(), (JavaObjectPropertyInfo) info);
            }
        }

        final Constructor<?> constructor = constructor(cls, infos.keySet());
        final Set<String> parameters = new HashSet<>();
        for (Parameter parameter : constructor.getParameters())
        {
            parameters.add(parameter.getName());
        }
        final Object blank = constructor.getParameterCount() == 0 ? construct(constructor, new Object[0], cls.getName()) : null;

        final Map<String, Property> properties = new LinkedHashMap<>();
        for (JavaObjectPropertyInfo info : infos.values())
        {
            final boolean input = info.isWriteable() || parameters.contains(info.getJavaPropertyName());
            final Object constant = !input && blank != null && info.isReadable() ? constant(info, blank) : null;
            if (input || constant != null)
            {
                properties.put(
                    info.getJsonName(),
                    new Property(
                        info.getJsonName(),
                        info.getJavaPropertyName(),
                        info.isWriteable() ? info.getSetterMethod() : null,
                        input && isRequired(cls, info.getGetterMethod()),
                        constant
                    )
                );
            }
        }
        return new ClassShape(cls, constructor, properties, Map.of());
    }


    /// The value of a read-only property that TypeScript would give a literal type: a string, number or boolean.
    private static Object constant(JavaObjectPropertyInfo info, Object blank)
    {
        final Object value = info.getProperty(blank);
        return value instanceof String || value instanceof Number || value instanceof Boolean ? value : null;
    }


    private static Constructor<?> constructor(Class<?> cls, Set<String> propertyNames)
    {
        Constructor<?> best = null;
        boolean unnamed = false;
        for (Constructor<?> constructor : cls.getConstructors())
        {
            boolean usable = true;
            for (Parameter parameter : constructor.getParameters())
            {
                unnamed |= !parameter.isNamePresent();
                usable &= parameter.isNamePresent() && propertyNames.contains(parameter.getName());
            }
            if (usable && (best == null || constructor.getParameterCount() > best.getParameterCount()))
            {
                best = constructor;
            }
        }
        if (best == null)
        {
            throw new QLiveException(
                cls.getName() + " has no public constructor whose parameters are all named like its properties" +
                    (unnamed ? ". Its parameter names are missing: compile it with -parameters." : ".")
            );
        }
        return best;
    }


    /// Whether the getter, or one it overrides, is `@NotNull`.
    private static boolean isRequired(Class<?> cls, Method getter)
    {
        if (getter == null)
        {
            return false;
        }
        final Deque<Class<?>> open = new ArrayDeque<>(List.of(cls));
        while (!open.isEmpty())
        {
            final Class<?> type = open.removeFirst();
            try
            {
                if (type.getDeclaredMethod(getter.getName()).isAnnotationPresent(NotNull.class))
                {
                    return true;
                }
            }
            catch (NoSuchMethodException e)
            {
                // not declared here
            }
            if (type.getSuperclass() != null)
            {
                open.add(type.getSuperclass());
            }
            open.addAll(List.of(type.getInterfaces()));
        }
        return false;
    }


    private static Class<?> rawClass(Type type)
    {
        if (type instanceof Class<?> cls)
        {
            return cls;
        }
        if (type instanceof ParameterizedType parameterized)
        {
            return rawClass(parameterized.getRawType());
        }
        if (type instanceof WildcardType wildcard)
        {
            return rawClass(wildcard.getUpperBounds()[0]);
        }
        return Object.class;
    }


    private static Type typeArgument(Type type, int index)
    {
        if (type instanceof WildcardType wildcard)
        {
            return typeArgument(wildcard.getUpperBounds()[0], index);
        }
        if (type instanceof ParameterizedType parameterized && parameterized.getActualTypeArguments().length > index)
        {
            return parameterized.getActualTypeArguments()[index];
        }
        return Object.class;
    }


    private static Class<?> boxed(Class<?> cls)
    {
        if (!cls.isPrimitive()) return cls;
        if (cls == boolean.class) return Boolean.class;
        if (cls == int.class) return Integer.class;
        if (cls == long.class) return Long.class;
        if (cls == double.class) return Double.class;
        if (cls == float.class) return Float.class;
        if (cls == short.class) return Short.class;
        if (cls == byte.class) return Byte.class;
        return Character.class;
    }


    private static Object defaultValue(Class<?> cls)
    {
        if (!cls.isPrimitive()) return null;
        if (cls == boolean.class) return false;
        if (cls == char.class) return '\0';
        return scalar(cls, 0, "default");
    }


    /// A property as the JSON has it: required, a constant, or neither, and how it reaches the instance.
    private record Property(
        String jsonName,
        String javaName,
        Method setter,
        boolean required,
        Object constant
    )
    {
    }


    /// A class to build, and in a union the keys that make an object one of it.
    private record ClassShape(
        Class<?> cls,
        Constructor<?> constructor,
        Map<String, Property> properties,
        Map<String, Property> keys
    )
    {
        ClassShape withKeys(Map<String, Property> keys)
        {
            return new ClassShape(cls, constructor, properties, keys);
        }


        Property byJavaName(String javaName)
        {
            for (Property property : properties.values())
            {
                if (property.javaName().equals(javaName))
                {
                    return property;
                }
            }
            throw new IllegalStateException(cls.getName() + " has no property " + javaName);
        }


        boolean isIn(Map<String, Object> map)
        {
            for (Property key : keys.values())
            {
                final Object value = map.get(key.jsonName());
                if (value != null && (key.constant() == null || key.constant().equals(value)))
                {
                    return true;
                }
            }
            return false;
        }
    }


    private record UnionShape(
        List<ClassShape> members
    )
    {
        /// The members by their keys: "a field, a section or status true".
        String describe()
        {
            final List<String> alternatives = members.stream()
                .map(
                    member -> member.keys().values().stream()
                        .map(key -> key.constant() == null ? "a " + key.jsonName() : key.jsonName() + " " + key.constant())
                        .collect(Collectors.joining(" or "))
                )
                .toList();
            if (alternatives.size() == 1)
            {
                return alternatives.get(0);
            }
            return String.join(", ", alternatives.subList(0, alternatives.size() - 1)) + " or " +
                alternatives.get(alternatives.size() - 1);
        }
    }
}
