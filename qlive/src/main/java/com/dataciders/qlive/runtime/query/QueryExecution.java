package com.dataciders.qlive.runtime.query;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class QueryExecution
{
    private final QueryJoin rootJoin;

    private final String location;

    private final List<QueryExecution> dependentQueries;

    private Set<QueryField> fields;

    private Set<QueryField> fieldsRO;

    private final Map<String, Integer> usedNames;


    public QueryExecution(QueryJoin rootJoin, String location)
    {
        this.rootJoin = rootJoin;
        this.location = location;
        dependentQueries = new ArrayList<>();
        fields = new LinkedHashSet<>();
        fieldsRO = Collections.unmodifiableSet(fields);
        usedNames = new HashMap<>();

        // account for root join name
        getUniqueName(rootJoin.getAlias());
    }

    public void addField(String tableAlias, String fieldName)
    {
        fields.add(new QueryField(tableAlias, fieldName));
    }


    public String getUniqueName(String name)
    {

        final Integer count = usedNames.get(name);
        if (count == null)
        {
            usedNames.put(name, 2);
            return name;
        }
        usedNames.put(name, count + 1);
        return name + count;
    }


    public Set<QueryField> getFields()
    {
        return fieldsRO;
    }


    public void addDependentQuery(QueryExecution queryExecution)
    {
        dependentQueries.add(queryExecution);
    }


    public List<QueryExecution> getDependentQueries()
    {
        return dependentQueries;
    }

    public QueryJoin rootJoin()
    {
        return rootJoin;
    }


    public String getLocation()
    {
        return location;
    }


    @Override
    public String toString()
    {
        return "QueryExecution: "
            + "fields = " + fields
            + ", location = '" + location + '\''
            + ", rootJoin = " + rootJoin
            + ", dependentQueries(" + dependentQueries.size() + ")"
            ;
    }
}
