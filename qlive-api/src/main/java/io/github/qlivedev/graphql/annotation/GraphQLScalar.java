package io.github.qlivedev.graphql.annotation;

import org.springframework.stereotype.Component;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks classes as GraphQL scalar values.
 * <p>
 *     The scalar must be defined with {@code io.github.qlivedev.graphql.QLiveDomainBuilder#withAdditionalScalar(Class, GraphQLScalarType)} from qlive-graphql, this annotation
 *     only protects the scalar from being used without it being formally declared as a scalar value. 
 * </p>
 */

@Target({ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface GraphQLScalar
{
}
