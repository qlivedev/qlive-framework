package io.github.qlivedev.qlive.api.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a graphql mutation method.
 * <p>
 * The mutation's description is the method's doc comment, which the build extracts into the type docs.
 */

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface QLiveMutation
{
    /**
     * Name of the graphql mutation. If left empty (the default), the method name is used.
     * @return name or empty
     */
    String value() default "";


}
