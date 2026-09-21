package io.github.qlivedev.runtime.util;

import org.svenson.JSON;
import org.svenson.JSONParser;
import org.svenson.info.JSONClassInfo;
import org.svenson.info.JSONPropertyInfo;
import org.svenson.info.JavaObjectSupport;
import org.svenson.util.JSONBeanUtil;

import java.lang.annotation.Annotation;

/// QLive's entry point to svenson.
///
/// Every member forwards to spring-jsview's `JSONUtil` rather than building its own, because exactly one
/// [JavaObjectSupport] may exist in the process. `TypeAnalyzer.getClassInfo` caches by class alone --
/// `holders.putIfAbsent(cls, holder)` -- and `ClassInfoHolder` analyzes lazily, so the second support to
/// reach a class is not extra work but a support that is silently ignored for that class, with class
/// loading order deciding which one answers. domainql resolves the spring-jsview class directly, so that
/// is the instance everything here has to share.
///
/// These become real implementations once domainql is vendored and its own references move here; see
/// `docs/design/dependency-consolidation.md`.
public class JSONUtil
{
    private JSONUtil()
    {
        // no instances
    }

    public final static JavaObjectSupport OBJECT_SUPPORT = de.quinscape.spring.jsview.util.JSONUtil.OBJECT_SUPPORT;

    public final static JSON DEFAULT_GENERATOR = de.quinscape.spring.jsview.util.JSONUtil.DEFAULT_GENERATOR;

    public final static JSONParser DEFAULT_PARSER = de.quinscape.spring.jsview.util.JSONUtil.DEFAULT_PARSER;

    public final static JSONBeanUtil DEFAULT_UTIL = de.quinscape.spring.jsview.util.JSONUtil.DEFAULT_UTIL;

    /// Class info for the given type, analyzed through [#OBJECT_SUPPORT].
    public static JSONClassInfo getClassInfo(Class<?> cls)
    {
        return de.quinscape.spring.jsview.util.JSONUtil.getClassInfo(cls);
    }

    /// Pretty-prints the given JSON document.
    public static String formatJSON(String s)
    {
        return de.quinscape.spring.jsview.util.JSONUtil.formatJSON(s);
    }

    /// Returns the annotation of the given type declared on the property's getter or setter, or `null`.
    public static <T extends Annotation> T findAnnotation(JSONPropertyInfo propertyInfo, Class<T> annoClass)
    {
        return de.quinscape.spring.jsview.util.JSONUtil.findAnnotation(propertyInfo, annoClass);
    }
}
