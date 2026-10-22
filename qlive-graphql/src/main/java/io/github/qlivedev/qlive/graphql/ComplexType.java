package io.github.qlivedev.qlive.graphql;

public interface ComplexType
{
    String getName();

    TypeContext getTypeContext();

    Class<?> getJavaType();

    boolean isEnum();
}
