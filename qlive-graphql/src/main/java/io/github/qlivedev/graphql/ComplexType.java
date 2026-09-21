package io.github.qlivedev.graphql;

public interface ComplexType
{
    String getName();

    TypeContext getTypeContext();

    Class<?> getJavaType();

    boolean isEnum();
}
