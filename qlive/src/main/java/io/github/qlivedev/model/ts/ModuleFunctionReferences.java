package io.github.qlivedev.model.ts;

import org.svenson.JSONParameter;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Encapsulates the detected static function calls within one module.
 *
 */
public class ModuleFunctionReferences
{
    /**
     * Call name configured for call data to useInjection()
     */
    public static final String USE_INJECTION_CALL_NAME = "useInjection";

    public static final String GRAPHQL_QUERY_CONSTRUCTOR_NAME = "GraphQLQuery";

    private final String module;

    private final List<String> requires;

    private final Map<String, List<List<?>>> calls;
    private final Map<String, List<List<?>>> indexes;
    private final Map<String, List<List<?>>> contexts;


    public ModuleFunctionReferences(
        @JSONParameter("module")
        String module,
        @JSONParameter("requires")
        List<String> requires,
        @JSONParameter("calls")
        Map<String, List<List<?>>> calls,
        @JSONParameter("indexes")
        Map<String, List<List<?>>> indexes,
        @JSONParameter("contexts")
        Map<String, List<List<?>>> contexts

    )
    {
        this.module = module;
        this.requires = requires;

        this.calls = calls;
        this.indexes = indexes;
        this.contexts = contexts;
    }


    /**
     * Module name (without leading "./")
     * @return
     */
    public String getModule()
    {
        return module;
    }


    /**
     * Map of variable names mapping to
     * @return
     */
    public List<String> getRequires()
    {
        if (requires == null)
        {
            return Collections.emptyList();
        }
        return requires;
    }


    /**
     * Returns the list of call parameters for the given symbolic call name as defined in the babel plugin config.
     *
     * @param name
     * @return
     */
    public List<List<?>> getCalls(String name)
    {
        List<List<?>> calls = this.calls.get(name);
        if (calls == null)
        {
            return Collections.emptyList();
        }
        return calls;
    }
    /**
     * Returns the list of call parameters for the given symbolic call name as defined in the babel plugin config.
     *
     * @param name
     * @return
     */
    public List<List<?>> getIndexes(String name)
    {
        if (indexes == null)
        {
            return Collections.emptyList();
        }

        List<List<?>> indexes = this.indexes.get(name);
        return Objects.requireNonNullElse(indexes, Collections.emptyList());
    }



    public Map<String, List<List<?>>> getIndexes()
    {
        return indexes;
    }


    public Map<String, List<List<?>>> getContexts()
    {
        return contexts;
    }

    public List<List<?>> getContexts(String name)
    {
        List<List<?>> contexts = this.contexts.get(name);
        return Objects.requireNonNullElse(contexts, Collections.emptyList());
    }


    @Override
    public String toString()
    {
        return super.toString() + ": "
            + "module = '" + module + '\''
            + ", requires = " + requires
            + ", calls = " + calls
            + ", indexes = " + indexes
            ;
    }
}
