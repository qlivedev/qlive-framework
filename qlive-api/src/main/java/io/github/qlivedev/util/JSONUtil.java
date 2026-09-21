package io.github.qlivedev.util;

import org.svenson.JSON;
import org.svenson.JSONParser;
import org.svenson.TypeAnalyzer;
import org.svenson.info.JSONClassInfo;
import org.svenson.info.JSONPropertyInfo;
import org.svenson.info.JavaObjectPropertyInfo;
import org.svenson.info.JavaObjectSupport;
import org.svenson.util.JSONBeanUtil;

import java.lang.annotation.Annotation;

/// QLive's entry point to svenson.
///
/// Derived from the class of the same name in spring-jsview, Copyright Quinscape
/// GmbH, used under the Apache License, Version 2.0. See the NOTICE file at the
/// root of this repository.
///
/// Exactly one [JavaObjectSupport] may exist in the process. `TypeAnalyzer.getClassInfo` caches by class
/// alone -- `holders.putIfAbsent(cls, holder)` -- and `ClassInfoHolder` analyzes lazily, so a second
/// support is not extra work but a support that is silently ignored for whichever classes the first one
/// reached first, with class loading order deciding which answers. Everything that touches svenson goes
/// through here for that reason, which is also why this lives in the lowest module.
///
/// The generator is built on [#OBJECT_SUPPORT] rather than taken from `JSON.defaultJSON()`: that is
/// svenson's process-wide singleton and it builds its own support, which would be a second one.
public class JSONUtil
{
    private JSONUtil()
    {
        // no instances
    }

    public final static JavaObjectSupport OBJECT_SUPPORT = new JavaObjectSupport();

    public final static JSON DEFAULT_GENERATOR;

    public final static JSONParser DEFAULT_PARSER;

    public final static JSONBeanUtil DEFAULT_UTIL;

    static
    {
        final JSONParser parser = new JSONParser();
        parser.setObjectSupport(OBJECT_SUPPORT);
        DEFAULT_PARSER = parser;

        final JSONBeanUtil util = new JSONBeanUtil();
        util.setObjectSupport(OBJECT_SUPPORT);
        DEFAULT_UTIL = util;

        DEFAULT_GENERATOR = new JSON(OBJECT_SUPPORT, '"');
    }

    /// Class info for the given type, analyzed through [#OBJECT_SUPPORT].
    public static JSONClassInfo getClassInfo(Class<?> cls)
    {
        return TypeAnalyzer.getClassInfo(OBJECT_SUPPORT, cls);
    }

    /// Pretty-prints the given JSON document.
    public static String formatJSON(String s)
    {
        return JSON.formatJSON(s);
    }

    /// Returns the annotation of the given type declared on the property's getter or setter, or `null`.
    public static <T extends Annotation> T findAnnotation(JSONPropertyInfo propertyInfo, Class<T> annoClass)
    {
        if (!(propertyInfo instanceof JavaObjectPropertyInfo info))
        {
            throw new IllegalArgumentException(
                "Invalid property info type: " + propertyInfo + ", must be " + JavaObjectPropertyInfo.class.getName()
            );
        }

        if (info.isReadable())
        {
            final T getterAnno = info.getGetterMethod().getAnnotation(annoClass);
            if (getterAnno != null)
            {
                return getterAnno;
            }
        }

        if (info.isWriteable())
        {
            final T setterAnno = info.getSetterMethod().getAnnotation(annoClass);
            if (setterAnno != null)
            {
                return setterAnno;
            }
        }

        return null;
    }
}
