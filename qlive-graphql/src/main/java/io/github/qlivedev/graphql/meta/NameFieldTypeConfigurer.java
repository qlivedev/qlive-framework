package io.github.qlivedev.graphql.meta;

import java.util.List;

/// What names the types one statement of a {@link NameFieldProvider} is about.
///
/// Never constructed directly: {@link NameFieldProvider#forType(Class)},
/// {@link NameFieldProvider#forTypes(Class[])} and {@link NameFieldProvider#forAllTypes()} say which types are
/// meant and hand one of these back.
///
///     NameFieldProvider.newProvider()
///         .forType(Bar.class)
///             .nameFields("name", "description")
///             .build()
///
/// {@link #build()} ends the chain and returns the provider; the `andFor...` methods end one statement and
/// begin the next.
public final class NameFieldTypeConfigurer
{
    private final NameFieldProvider nameFieldProvider;

    private List<String> nameFields;


    NameFieldTypeConfigurer(NameFieldProvider nameFieldProvider)
    {
        this.nameFieldProvider = nameFieldProvider;
    }


    /// The fields naming an instance of the types to a user, most significant first. Each is a scalar field of
    /// the type itself, never a path to a related one.
    ///
    /// @param nameFields  the name fields, at least one
    public NameFieldTypeConfigurer nameFields(String... nameFields)
    {
        if (nameFields == null || nameFields.length == 0)
        {
            throw new IllegalArgumentException("Need at least one name field");
        }

        for (String name : nameFields)
        {
            if (name.contains("."))
            {
                throw new IllegalArgumentException(
                    "Name field '" + name + "' is a path. A name field is a field of the type itself."
                );
            }
        }

        this.nameFields = List.of(nameFields);
        return this;
    }


    /// The name fields declared, or null where none were.
    List<String> getNameFields()
    {
        return nameFields;
    }


    /// Ends this statement and begins one about the given type.
    public NameFieldTypeConfigurer andForType(Class<?> javaType)
    {
        return nameFieldProvider.forType(javaType);
    }


    /// Ends this statement and begins one about the given types.
    public NameFieldTypeConfigurer andForTypes(Class<?>... javaTypes)
    {
        return nameFieldProvider.forTypes(javaTypes);
    }


    /// Ends this statement and begins -- or goes on with -- the one about all types.
    public NameFieldTypeConfigurer andForAllTypes()
    {
        return nameFieldProvider.forAllTypes();
    }


    @Override
    public String toString()
    {
        return super.toString() + ": nameFields = " + nameFields;
    }


    /// Ends the chain, for the bean that has to return the provider itself.
    public NameFieldProvider build()
    {
        return nameFieldProvider;
    }
}
