package com.dataciders.qlive.model.condition;

import com.dataciders.qlive.runtime.util.TypeMappers;
import com.dataciders.qlive.runtime.util.JSONUtil;
import org.svenson.JSONParser;

/**
 * Specialized JSONParser setup to parse a FilterDSL node hierarchy into
 * the correct types.
 */
public final class ConditionParser
{
    private final JSONParser parser;

    public ConditionParser()
    {
        this.parser = new JSONParser();
        this.parser.setObjectSupport(JSONUtil.OBJECT_SUPPORT);

        this.parser.setTypeMapper(TypeMappers.byClassName(CNode.class));
    }

    public CNode parse(String json)
    {
        return parser.parse(CNode.class, json);
    }

    public Condition parseCondition(String json)
    {
        final CNode node = parser.parse(CNode.class, json);

        if ((node != null && !(node instanceof Condition)))
        {
            throw new IllegalStateException("Parsed node is not a Condition: " + node);
        }
        
        return (Condition) node;
    }
}
